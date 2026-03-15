package org.mule.extension.sshd.internal;

import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SshdOperations {

    private static final Logger logger = LoggerFactory.getLogger(SshdOperations.class);

    @DisplayName("Server Status")
    @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
    public InputStream serverStatus(@Config SshdConfiguration config) {
        boolean running = config.getSshServer() != null && config.getSshServer().isOpen();
        String json = "{\"status\":\"" + (running ? "RUNNING" : "STOPPED") + "\""
                + ",\"port\":" + config.getPort()
                + ",\"rootDirectory\":\"" + escapeJson(config.getRootDirectory()) + "\""
                + ",\"username\":\"" + escapeJson(config.getUsername()) + "\""
                + "}";
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @DisplayName("List Files")
    @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
    public InputStream listFiles(@Config SshdConfiguration config,
                                 @DisplayName("Path") String path) {
        try {
            Path dir = Paths.get(config.getRootDirectory()).resolve(path == null ? "" : path);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return jsonBytes("{\"error\":\"Directory not found: " + escapeJson(path) + "\"}");
            }
            String entries = Files.list(dir)
                .map(p -> "{\"name\":\"" + escapeJson(p.getFileName().toString()) + "\""
                    + ",\"type\":\"" + (Files.isDirectory(p) ? "directory" : "file") + "\""
                    + ",\"size\":" + fileSize(p) + "}")
                .collect(Collectors.joining(","));
            return jsonBytes("{\"path\":\"" + escapeJson(path) + "\",\"entries\":[" + entries + "]}");
        } catch (Exception e) {
            return jsonBytes("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private long fileSize(Path p) {
        try { return Files.size(p); } catch (Exception e) { return 0; }
    }

    private InputStream jsonBytes(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
