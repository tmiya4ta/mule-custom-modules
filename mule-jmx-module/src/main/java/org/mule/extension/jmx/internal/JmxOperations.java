package org.mule.extension.jmx.internal;

import java.io.IOException;
import java.util.*;

import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Expression;

import static org.mule.runtime.extension.api.annotation.param.MediaType.APPLICATION_JSON;

/**
 * JMX metrics and log operations.
 */
public class JmxOperations {

    // ── Metrics ──

    @DisplayName("Collect All Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectMetrics(@Config JmxConfiguration config) {
        return JsonUtil.toJson(MetricsCollector.collectAll());
    }

    @DisplayName("Collect CPU Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectCpu(@Config JmxConfiguration config) {
        return JsonUtil.toJson(MetricsCollector.collectCpu());
    }

    @DisplayName("Collect Memory Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectMemory(@Config JmxConfiguration config) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("memory", MetricsCollector.collectMemory());
        m.put("memoryPools", MetricsCollector.collectMemoryPools());
        return JsonUtil.toJson(m);
    }

    @DisplayName("Collect GC Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectGc(@Config JmxConfiguration config) {
        return JsonUtil.toJson(MetricsCollector.collectGc());
    }

    @DisplayName("Collect Thread Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectThreads(@Config JmxConfiguration config) {
        return JsonUtil.toJson(MetricsCollector.collectThreads());
    }

    // ── Logs: Ring Buffer (in-memory, real-time) ──

    /**
     * Returns recent log entries from the in-memory ring buffer.
     * Optionally filtered by minimum level and/or search pattern.
     *
     * @param lines   Max entries to return (default 100)
     * @param level   Minimum log level: TRACE, DEBUG, INFO, WARN, ERROR (default: all)
     * @param pattern Search pattern (case-insensitive match on message, logger, exception)
     */
    @DisplayName("Get Recent Logs")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String getRecentLogs(
            @Config JmxConfiguration config,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "100")
            @Summary("Max number of entries to return") int lines,
            @Expression(ExpressionSupport.SUPPORTED) @Optional
            @Summary("Minimum log level: TRACE, DEBUG, INFO, WARN, ERROR") String level,
            @Expression(ExpressionSupport.SUPPORTED) @Optional
            @Summary("Search pattern (case-insensitive)") String pattern) {

        LogRingBuffer rb = config.getRingBuffer();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bufferCapacity", rb.getCapacity());
        result.put("bufferSize", rb.getSize());
        List<Map<String, Object>> entries = rb.getLogs(lines, level, pattern);
        result.put("count", entries.size());
        result.put("entries", entries);
        return JsonUtil.toJson(result);
    }

    // ── Logs: File-based ──

    /**
     * Lists log files in the log directory.
     */
    @DisplayName("List Log Files")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String listLogFiles(@Config JmxConfiguration config) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("logDir", LogFileReader.resolveLogDir(config.getLogDir()).toString());
            result.put("files", LogFileReader.listLogFiles(config.getLogDir()));
            return JsonUtil.toJson(result);
        } catch (IOException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            return JsonUtil.toJson(err);
        }
    }

    /**
     * Reads the last N lines from a log file (tail).
     *
     * @param fileName Log file name (e.g. "mule-app.log")
     * @param lines    Number of lines to return (default 100)
     */
    @DisplayName("Tail Log File")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String tailLogFile(
            @Config JmxConfiguration config,
            @Expression(ExpressionSupport.SUPPORTED)
            @Summary("Log file name (e.g. mule-app.log)") String fileName,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "100")
            @Summary("Number of lines") int lines) {
        try {
            List<String> logLines = LogFileReader.tailLines(config.getLogDir(), fileName, lines);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("file", fileName);
            result.put("lines", logLines.size());
            result.put("content", logLines);
            return JsonUtil.toJson(result);
        } catch (IOException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            return JsonUtil.toJson(err);
        }
    }

    /**
     * Searches a log file for lines matching a pattern.
     *
     * @param fileName Log file name
     * @param pattern  Search pattern (case-insensitive)
     * @param lines    Max matching lines to return (default 50)
     */
    @DisplayName("Search Log File")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String searchLogFile(
            @Config JmxConfiguration config,
            @Expression(ExpressionSupport.SUPPORTED)
            @Summary("Log file name") String fileName,
            @Expression(ExpressionSupport.SUPPORTED)
            @Summary("Search pattern (case-insensitive)") String pattern,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "50")
            @Summary("Max matching lines") int lines) {
        try {
            List<String> matches = LogFileReader.searchLines(config.getLogDir(), fileName, pattern, lines);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("file", fileName);
            result.put("pattern", pattern);
            result.put("matches", matches.size());
            result.put("content", matches);
            return JsonUtil.toJson(result);
        } catch (IOException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            return JsonUtil.toJson(err);
        }
    }
}
