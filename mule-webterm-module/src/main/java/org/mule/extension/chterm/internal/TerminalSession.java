package org.mule.extension.chterm.internal;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a PTY-backed interactive shell session and exec key authentication.
 */
public class TerminalSession {

    private static final Logger logger = LoggerFactory.getLogger(TerminalSession.class);

    private volatile String execKey = null;
    private volatile String password = null;

    private Process termProcess;
    private OutputStream termStdin;
    private final StringBuilder termBuffer = new StringBuilder();
    private volatile boolean termAlive = false;
    private volatile int termColumns = 120;
    private volatile int termRows = 24;

    private static final String TERM_PS1 =
        "\\[\\033[1;34m\\]\\u@\\h\\[\\033[0m\\]:\\[\\033[1;32m\\]\\w\\[\\033[0m\\]\\$ ";

    public TerminalSession() {
    }

    // ── Auth ──

    public Map<String, Object> generateExecKey(String inputPassword, String configPassword) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (inputPassword == null || inputPassword.isEmpty()) {
            result.put("error", "Password is required.");
            return result;
        }
        if (configPassword.equals(inputPassword)) {
            password = inputPassword;
            execKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            result.put("key", execKey);
        } else {
            result.put("error", "Invalid password.");
        }
        return result;
    }

    public boolean isExecKeySet() {
        return execKey != null;
    }

    private boolean validateKey(String key) {
        return execKey != null && execKey.equals(key);
    }

    // ── Exec (one-shot command) ──

    public Map<String, Object> exec(String command, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) {
            result.put("error", execKey == null ? "No exec key generated." : "Invalid exec key.");
            return result;
        }
        result.put("command", command);
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            int exitCode = p.waitFor();
            result.put("exitCode", exitCode);
            result.put("output", new String(out));
        } catch (Exception e) {
            result.put("exitCode", -1);
            result.put("output", e.getMessage());
        }
        return result;
    }

    // ── Interactive terminal ──

    public synchronized void ensureTermProcess() throws Exception {
        if (termProcess != null && termProcess.isAlive()) return;

        ProcessBuilder pb = new ProcessBuilder(
            "script", "-qfc",
            "stty cols " + termColumns + " rows " + termRows + " 2>/dev/null; exec /bin/bash -i",
            "/dev/null"
        );
        pb.redirectErrorStream(true);
        pb.environment().put("COLUMNS", String.valueOf(termColumns));
        pb.environment().put("LINES", String.valueOf(termRows));
        pb.environment().put("TERM", "xterm-256color");
        pb.environment().put("COLORTERM", "truecolor");
        termProcess = pb.start();
        termStdin = termProcess.getOutputStream();
        termAlive = true;

        InputStream is = termProcess.getInputStream();
        Thread reader = new Thread(() -> {
            byte[] buf = new byte[8192];
            try {
                int n;
                while ((n = is.read(buf)) != -1) {
                    synchronized (termBuffer) {
                        termBuffer.append(new String(buf, 0, n));
                    }
                }
            } catch (Exception ignored) {}
            termAlive = false;
        });
        reader.setDaemon(true);
        reader.start();

        String init = " export PS1='" + TERM_PS1 + "'; "
            + "shopt -s checkwinsize; "
            + "alias ls='ls --color=auto'; alias grep='grep --color=auto'; "
            + "clear\n";
        termStdin.write(init.getBytes());
        termStdin.flush();
    }

    public Map<String, Object> termWrite(String input, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) {
            result.put("error", execKey == null ? "No exec key set." : "Invalid exec key.");
            return result;
        }
        try {
            ensureTermProcess();
            termStdin.write(input.getBytes());
            termStdin.flush();
            result.put("ok", true);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> termRead(String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) {
            result.put("error", execKey == null ? "No exec key set." : "Invalid exec key.");
            return result;
        }
        String output;
        synchronized (termBuffer) {
            output = termBuffer.toString();
            termBuffer.setLength(0);
        }
        result.put("output", output);
        result.put("alive", termAlive);
        return result;
    }

    public boolean setTermSize(int cols, int rows) {
        termColumns = Math.max(40, Math.min(cols, 500));
        termRows = Math.max(10, Math.min(rows, 200));
        if (termProcess != null && termProcess.isAlive() && termStdin != null) {
            try {
                String cmd = " stty cols " + termColumns + " rows " + termRows
                    + " 2>/dev/null; export COLUMNS=" + termColumns + " LINES=" + termRows + "\n";
                termStdin.write(cmd.getBytes());
                termStdin.flush();
            } catch (Exception ignored) {}
        }
        return true;
    }

    // ── File operations ──

    public Map<String, Object> fileDownload(String path, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) {
            result.put("error", execKey == null ? "No exec key set." : "Invalid exec key.");
            return result;
        }
        try {
            java.io.File file = new java.io.File(path);
            if (!file.exists()) {
                result.put("error", "File not found: " + path);
                return result;
            }
            if (file.isDirectory()) {
                result.put("error", "Path is a directory: " + path);
                return result;
            }
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            result.put("name", file.getName());
            result.put("size", (long) data.length);
            result.put("content", java.util.Base64.getEncoder().encodeToString(data));
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> fileUpload(String path, String base64Content, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) {
            result.put("error", execKey == null ? "No exec key set." : "Invalid exec key.");
            return result;
        }
        try {
            byte[] data = java.util.Base64.getDecoder().decode(base64Content);
            java.io.File file = new java.io.File(path);
            // Create parent dirs if needed
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            java.nio.file.Files.write(file.toPath(), data);
            result.put("ok", true);
            result.put("size", (long) data.length);
            result.put("path", file.getAbsolutePath());
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> fileList(String path, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) {
            result.put("error", execKey == null ? "No exec key set." : "Invalid exec key.");
            return result;
        }
        try {
            java.io.File dir = new java.io.File(path);
            if (!dir.exists()) {
                result.put("error", "Path not found: " + path);
                return result;
            }
            if (!dir.isDirectory()) {
                result.put("error", "Not a directory: " + path);
                return result;
            }
            java.io.File[] files = dir.listFiles();
            java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
            if (files != null) {
                java.util.Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (java.io.File f : files) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", f.getName());
                    entry.put("size", f.isFile() ? f.length() : 0L);
                    entry.put("dir", f.isDirectory());
                    list.add(entry);
                }
            }
            result.put("path", dir.getAbsolutePath());
            result.put("files", list);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ── Chunked file transfer ──

    private static final int CHUNK_SIZE = 10 * 1024 * 1024; // 10MB

    private static class UploadSession {
        final String path;
        final java.io.FileOutputStream fos;
        long written;
        UploadSession(String path, java.io.FileOutputStream fos) {
            this.path = path; this.fos = fos; this.written = 0;
        }
    }

    private static class DownloadSession {
        final String path;
        final long size;
        final String name;
        long offset;
        DownloadSession(String path, long size, String name) {
            this.path = path; this.size = size; this.name = name; this.offset = 0;
        }
    }

    private final ConcurrentHashMap<String, UploadSession> uploads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DownloadSession> downloads = new ConcurrentHashMap<>();

    public Map<String, Object> uploadStart(String path, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) { result.put("error", "Invalid exec key."); return result; }
        try {
            java.io.File file = new java.io.File(path);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            String id = UUID.randomUUID().toString().substring(0, 8);
            uploads.put(id, new UploadSession(path, new java.io.FileOutputStream(file)));
            result.put("id", id);
            result.put("ok", true);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> uploadChunk(String id, String base64, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) { result.put("error", "Invalid exec key."); return result; }
        UploadSession sess = uploads.get(id);
        if (sess == null) { result.put("error", "Unknown upload session: " + id); return result; }
        try {
            byte[] data = java.util.Base64.getDecoder().decode(base64);
            sess.fos.write(data);
            sess.written += data.length;
            result.put("ok", true);
            result.put("written", sess.written);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> uploadEnd(String id, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) { result.put("error", "Invalid exec key."); return result; }
        UploadSession sess = uploads.remove(id);
        if (sess == null) { result.put("error", "Unknown upload session: " + id); return result; }
        try {
            sess.fos.close();
            result.put("ok", true);
            result.put("size", sess.written);
            result.put("path", sess.path);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> downloadStart(String path, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) { result.put("error", "Invalid exec key."); return result; }
        try {
            java.io.File file = new java.io.File(path);
            if (!file.exists()) { result.put("error", "File not found: " + path); return result; }
            if (file.isDirectory()) { result.put("error", "Path is a directory"); return result; }
            String id = UUID.randomUUID().toString().substring(0, 8);
            downloads.put(id, new DownloadSession(path, file.length(), file.getName()));
            result.put("id", id);
            result.put("size", file.length());
            result.put("name", file.getName());
            result.put("chunkSize", (long) CHUNK_SIZE);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> downloadChunk(String id, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!validateKey(key)) { result.put("error", "Invalid exec key."); return result; }
        DownloadSession sess = downloads.get(id);
        if (sess == null) { result.put("error", "Unknown download session: " + id); return result; }
        try {
            RandomAccessFile raf = new RandomAccessFile(sess.path, "r");
            raf.seek(sess.offset);
            int toRead = (int) Math.min(CHUNK_SIZE, sess.size - sess.offset);
            byte[] buf = new byte[toRead];
            int read = raf.read(buf, 0, toRead);
            raf.close();
            if (read <= 0) {
                downloads.remove(id);
                result.put("content", "");
                result.put("done", true);
                return result;
            }
            sess.offset += read;
            boolean done = sess.offset >= sess.size;
            if (done) downloads.remove(id);
            result.put("content", java.util.Base64.getEncoder().encodeToString(
                read == buf.length ? buf : java.util.Arrays.copyOf(buf, read)));
            result.put("read", (long) read);
            result.put("offset", sess.offset);
            result.put("done", done);
        } catch (Exception e) {
            result.put("error", e.getMessage());
            downloads.remove(id);
        }
        return result;
    }

    public void destroy() {
        if (termProcess != null && termProcess.isAlive()) {
            termProcess.destroyForcibly();
            logger.info("Terminal process destroyed");
        }
        // Cleanup transfer sessions
        uploads.values().forEach(s -> { try { s.fos.close(); } catch (Exception e) {} });
        uploads.clear();
        downloads.clear();
    }
}
