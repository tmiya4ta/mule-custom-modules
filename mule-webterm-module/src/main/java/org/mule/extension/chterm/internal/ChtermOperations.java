package org.mule.extension.chterm.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Expression;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;
import static org.mule.runtime.extension.api.annotation.param.Optional.PAYLOAD;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single operation that handles all chterm requests.
 * Dispatches based on the HTTP request path.
 */
public class ChtermOperations {

    private static final Logger logger = LoggerFactory.getLogger(ChtermOperations.class);

    /**
     * Handles all chterm requests. Place this inside a flow with an HTTP listener
     * on path "/chterm/*". It dispatches based on the relative path:
     *   GET  /chterm/           → HTML console UI
     *   GET  /chterm/api/exec-key → check if key is set
     *   POST /chterm/api/exec-key → generate exec key
     *   POST /chterm/api/exec     → execute OS command
     *   POST /chterm/api/term/input  → write to terminal
     *   POST /chterm/api/term/read   → read terminal output
     *   POST /chterm/api/term/resize → resize terminal
     */
    @DisplayName("Handle Request")
    @MediaType(value = ANY, strict = false)
    public Result<InputStream, Void> handleRequest(
            @Config ChtermConfiguration config,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "#[attributes.requestPath]")
            @Summary("HTTP request path") String requestPath,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "#[attributes.method]")
            @Summary("HTTP method") String method,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = PAYLOAD)
            InputStream body) {

        TerminalSession session = config.getSession();

        // Determine relative path (strip everything up to and including the listener path prefix)
        // e.g. /chterm/api/exec-key → /api/exec-key
        String path = requestPath;
        int apiIdx = path.indexOf("/api/");
        if (apiIdx >= 0) {
            path = path.substring(apiIdx);
        } else {
            // Static file or root
            path = "/";
        }

        try {
            // ── API routes ──
            if (path.startsWith("/api/")) {
                String bodyStr = readBody(body);

                if (path.equals("/api/exec-key") || path.equals("/api/exec-key/")) {
                    if ("GET".equalsIgnoreCase(method)) {
                        return jsonResult("{\"set\":" + session.isExecKeySet() + "}");
                    }
                    Map<String, Object> parsed = parseJson(bodyStr);
                    String pw = (String) parsed.get("password");
                    Map<String, Object> result = session.generateExecKey(pw, config.getPassword());
                    return jsonResult(toJson(result));
                }

                if (path.equals("/api/exec") || path.equals("/api/exec/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    String command = (String) parsed.get("command");
                    String key = (String) parsed.get("key");
                    Map<String, Object> result = session.exec(command, key);
                    return jsonResult(toJson(result));
                }

                if (path.equals("/api/term/input") || path.equals("/api/term/input/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    String cmd = (String) parsed.get("cmd");
                    String key = (String) parsed.get("key");
                    Map<String, Object> result = session.termWrite(cmd, key);
                    return jsonResult(toJson(result));
                }

                if (path.equals("/api/term/read") || path.equals("/api/term/read/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    String key = (String) parsed.get("key");
                    Map<String, Object> result = session.termRead(key);
                    return jsonResult(toJson(result));
                }

                if (path.equals("/api/term/resize") || path.equals("/api/term/resize/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    int cols = toInt(parsed.get("cols"), 120);
                    int rows = toInt(parsed.get("rows"), 24);
                    session.setTermSize(cols, rows);
                    return jsonResult("{\"ok\":true}");
                }

                // ── Chunked upload ──
                if (path.equals("/api/file/upload-start") || path.equals("/api/file/upload-start/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    Map<String, Object> result = session.uploadStart(
                        (String) parsed.get("path"), (String) parsed.get("key"));
                    return jsonResult(toJson(result));
                }
                if (path.equals("/api/file/upload-chunk") || path.equals("/api/file/upload-chunk/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    Map<String, Object> result = session.uploadChunk(
                        (String) parsed.get("id"), (String) parsed.get("content"), (String) parsed.get("key"));
                    return jsonResult(toJson(result));
                }
                if (path.equals("/api/file/upload-end") || path.equals("/api/file/upload-end/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    Map<String, Object> result = session.uploadEnd(
                        (String) parsed.get("id"), (String) parsed.get("key"));
                    return jsonResult(toJson(result));
                }

                // ── Chunked download ──
                if (path.equals("/api/file/download-start") || path.equals("/api/file/download-start/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    Map<String, Object> result = session.downloadStart(
                        (String) parsed.get("path"), (String) parsed.get("key"));
                    return jsonResult(toJson(result));
                }
                if (path.equals("/api/file/download-chunk") || path.equals("/api/file/download-chunk/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    Map<String, Object> result = session.downloadChunk(
                        (String) parsed.get("id"), (String) parsed.get("key"));
                    return jsonResult(toJson(result));
                }

                // ── Legacy single-request (small files) ──
                if (path.equals("/api/file/download") || path.equals("/api/file/download/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    String filePath = (String) parsed.get("path");
                    String key = (String) parsed.get("key");
                    Map<String, Object> result = session.fileDownload(filePath, key);
                    return jsonResult(toJson(result));
                }

                if (path.equals("/api/file/upload") || path.equals("/api/file/upload/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    String filePath = (String) parsed.get("path");
                    String content = (String) parsed.get("content");
                    String key = (String) parsed.get("key");
                    Map<String, Object> result = session.fileUpload(filePath, content, key);
                    return jsonResult(toJson(result));
                }

                if (path.equals("/api/file/list") || path.equals("/api/file/list/")) {
                    Map<String, Object> parsed = parseJson(bodyStr);
                    String filePath = (String) parsed.get("path");
                    String key = (String) parsed.get("key");
                    Map<String, Object> result = session.fileList(filePath, key);
                    return jsonResult(toJson(result));
                }

                return jsonResult("{\"error\":\"Unknown API path: " + escapeJson(path) + "\"}");
            }

            // ── Static: serve HTML ──
            InputStream html = getClass().getResourceAsStream("/static/index.html");
            if (html == null) {
                return htmlResult("<h1>Chterm: index.html not found</h1>");
            }
            byte[] data = html.readAllBytes();
            html.close();
            return Result.<InputStream, Void>builder()
                    .output(new ByteArrayInputStream(data))
                    .mediaType(org.mule.runtime.api.metadata.MediaType.create("text", "html",
                            StandardCharsets.UTF_8))
                    .build();

        } catch (Exception e) {
            logger.error("Chterm error", e);
            return jsonResult("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ── Helpers ──

    private Result<InputStream, Void> jsonResult(String json) {
        return Result.<InputStream, Void>builder()
                .output(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
                .mediaType(org.mule.runtime.api.metadata.MediaType.create("application", "json",
                        StandardCharsets.UTF_8))
                .build();
    }

    private Result<InputStream, Void> htmlResult(String html) {
        return Result.<InputStream, Void>builder()
                .output(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)))
                .mediaType(org.mule.runtime.api.metadata.MediaType.create("text", "html",
                        StandardCharsets.UTF_8))
                .build();
    }

    private String readBody(InputStream is) {
        if (is == null) return "{}";
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{}";
        }
    }

    private Map<String, Object> parseJson(String body) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (body == null || body.isBlank()) return map;
        body = body.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);

        boolean inString = false, escape = false;
        int depth = 0, start = 0;
        java.util.List<String> parts = new java.util.ArrayList<>();

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                parts.add(body.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < body.length()) parts.add(body.substring(start).trim());

        for (String part : parts) {
            int colon = part.indexOf(':');
            if (colon < 0) continue;
            String key = part.substring(0, colon).trim();
            String value = part.substring(colon + 1).trim();
            if (key.startsWith("\"") && key.endsWith("\"")) key = key.substring(1, key.length() - 1);
            if (value.startsWith("\"") && value.endsWith("\"")) {
                map.put(key, unescapeJsonString(value.substring(1, value.length() - 1)));
            } else if ("true".equals(value)) {
                map.put(key, Boolean.TRUE);
            } else if ("false".equals(value)) {
                map.put(key, Boolean.FALSE);
            } else if ("null".equals(value)) {
                map.put(key, null);
            } else {
                try { map.put(key, Integer.parseInt(value)); }
                catch (NumberFormatException e) {
                    try { map.put(key, Double.parseDouble(value)); }
                    catch (NumberFormatException e2) { map.put(key, value); }
                }
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(e.getKey())).append("\":");
                sb.append(toJson(e.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Boolean || obj instanceof Number) return obj.toString();
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private String unescapeJsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"':  sb.append('"');  i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case '/':  sb.append('/');  i++; break;
                    case 'n':  sb.append('\n'); i++; break;
                    case 'r':  sb.append('\r'); i++; break;
                    case 't':  sb.append('\t'); i++; break;
                    case 'f':  sb.append('\f'); i++; break;
                    case 'b':  sb.append('\b'); i++; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            String hex = s.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                        break;
                    default: sb.append(c); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        // Escape control characters as JSON unicode escape
                        sb.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private int toInt(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) try { return Integer.parseInt((String) v); } catch (Exception e) {}
        return def;
    }
}
