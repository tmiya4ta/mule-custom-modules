package org.mule.extension.jmx.internal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Reads log files from the Mule runtime logs directory.
 * Auto-detects log path from mule.home system property.
 */
public final class LogFileReader {

    private LogFileReader() {}

    /**
     * Resolves the log directory path.
     * Priority: explicit path > ${mule.home}/logs > /opt/mule/logs > ./logs
     */
    static Path resolveLogDir(String explicitPath) {
        if (explicitPath != null && !explicitPath.isEmpty()) {
            return Paths.get(explicitPath);
        }
        String muleHome = System.getProperty("mule.home");
        if (muleHome != null) {
            return Paths.get(muleHome, "logs");
        }
        // CH2 fallback
        Path ch2 = Paths.get("/opt/mule/logs");
        if (Files.isDirectory(ch2)) return ch2;
        return Paths.get("logs");
    }

    /**
     * Lists log files in the log directory.
     */
    static List<Map<String, Object>> listLogFiles(String logDir) throws IOException {
        Path dir = resolveLogDir(logDir);
        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                  .forEach(p -> {
                      Map<String, Object> f = new LinkedHashMap<>();
                      f.put("name", p.getFileName().toString());
                      try { f.put("size", Files.size(p)); } catch (IOException e) { f.put("size", -1L); }
                      try { f.put("lastModified", Files.getLastModifiedTime(p).toMillis()); } catch (IOException e) {}
                      files.add(f);
                  });
        }
        return files;
    }

    /**
     * Reads the last N lines from a file (tail).
     * Memory-efficient: reads backwards from end of file.
     */
    static List<String> tailLines(String logDir, String fileName, int lines) throws IOException {
        Path dir = resolveLogDir(logDir);
        Path file = dir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new FileNotFoundException("Log file not found: " + file);
        }

        // Read from end
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) return Collections.emptyList();

            List<String> result = new ArrayList<>();
            long pos = fileLength - 1;
            StringBuilder sb = new StringBuilder();
            int count = 0;

            // Skip trailing newline
            raf.seek(pos);
            if (raf.read() == '\n') pos--;

            while (pos >= 0 && count < lines) {
                raf.seek(pos);
                int ch = raf.read();
                if (ch == '\n') {
                    result.add(sb.reverse().toString());
                    sb = new StringBuilder();
                    count++;
                } else {
                    sb.append((char) ch);
                }
                pos--;
            }
            if (sb.length() > 0 && count < lines) {
                result.add(sb.reverse().toString());
            }
            Collections.reverse(result);
            return result;
        }
    }

    /**
     * Searches a log file for lines matching a pattern (case-insensitive).
     * Returns the last N matching lines.
     */
    static List<String> searchLines(String logDir, String fileName, String pattern, int maxResults) throws IOException {
        Path dir = resolveLogDir(logDir);
        Path file = dir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new FileNotFoundException("Log file not found: " + file);
        }

        String lowerPattern = pattern.toLowerCase();
        List<String> matches = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains(lowerPattern)) {
                    matches.add(line);
                    // Keep only last maxResults
                    if (matches.size() > maxResults * 2) {
                        matches = new ArrayList<>(matches.subList(matches.size() - maxResults, matches.size()));
                    }
                }
            }
        }

        if (matches.size() > maxResults) {
            return matches.subList(matches.size() - maxResults, matches.size());
        }
        return matches;
    }
}
