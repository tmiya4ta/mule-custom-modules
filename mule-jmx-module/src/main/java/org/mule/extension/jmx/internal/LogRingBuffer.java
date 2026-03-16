package org.mule.extension.jmx.internal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory ring buffer that captures Log4j2 log events.
 * Stores the last N entries in a circular buffer for real-time log access.
 */
public class LogRingBuffer {

    private static final String APPENDER_NAME = "JmxRingBuffer";
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final Map<String, Object>[] buffer;
    private int head = 0;
    private int size = 0;
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private Appender appender;

    @SuppressWarnings("unchecked")
    public LogRingBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new Map[capacity];
    }

    /**
     * Registers a Log4j2 appender to capture log events into this ring buffer.
     */
    public void install() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        appender = new AbstractAppender(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("timestamp", FORMATTER.format(Instant.ofEpochMilli(event.getTimeMillis())));
                entry.put("epochMillis", event.getTimeMillis());
                entry.put("level", event.getLevel().name());
                entry.put("logger", event.getLoggerName());
                entry.put("message", event.getMessage().getFormattedMessage());
                entry.put("thread", event.getThreadName());

                if (event.getThrown() != null) {
                    StringWriter sw = new StringWriter();
                    event.getThrown().printStackTrace(new PrintWriter(sw));
                    entry.put("exception", sw.toString());
                }

                lock.lock();
                try {
                    buffer[head] = entry;
                    head = (head + 1) % capacity;
                    if (size < capacity) size++;
                } finally {
                    lock.unlock();
                }
            }
        };

        appender.start();
        config.getRootLogger().addAppender(appender, null, null);
        ctx.updateLoggers();
    }

    /**
     * Unregisters the appender.
     */
    public void uninstall() {
        if (appender != null) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            config.getRootLogger().removeAppender(APPENDER_NAME);
            appender.stop();
            ctx.updateLoggers();
        }
    }

    /**
     * Returns the last N log entries, optionally filtered by level and/or pattern.
     */
    public List<Map<String, Object>> getLogs(int lines, String level, String pattern) {
        lock.lock();
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            // Read from oldest to newest
            int start = (size < capacity) ? 0 : head;
            for (int i = 0; i < size; i++) {
                Map<String, Object> entry = buffer[(start + i) % capacity];
                if (entry == null) continue;

                // Filter by level
                if (level != null && !level.isEmpty()) {
                    String entryLevel = (String) entry.get("level");
                    if (!matchesLevel(entryLevel, level)) continue;
                }

                // Filter by pattern (case-insensitive search in message + logger)
                if (pattern != null && !pattern.isEmpty()) {
                    String msg = ((String) entry.getOrDefault("message", "")).toLowerCase();
                    String lgr = ((String) entry.getOrDefault("logger", "")).toLowerCase();
                    String exc = ((String) entry.getOrDefault("exception", "")).toLowerCase();
                    String pat = pattern.toLowerCase();
                    if (!msg.contains(pat) && !lgr.contains(pat) && !exc.contains(pat)) continue;
                }

                result.add(entry);
            }

            // Return last N entries
            if (lines > 0 && result.size() > lines) {
                return result.subList(result.size() - lines, result.size());
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if entry level matches or is higher than requested level.
     */
    private boolean matchesLevel(String entryLevel, String requestedLevel) {
        int entryOrd = levelOrdinal(entryLevel);
        int reqOrd = levelOrdinal(requestedLevel.toUpperCase());
        return entryOrd >= reqOrd;
    }

    private int levelOrdinal(String level) {
        switch (level) {
            case "TRACE": return 0;
            case "DEBUG": return 1;
            case "INFO":  return 2;
            case "WARN":  return 3;
            case "ERROR": return 4;
            case "FATAL": return 5;
            default: return 0;
        }
    }

    public int getCapacity() { return capacity; }
    public int getSize() {
        lock.lock();
        try { return size; } finally { lock.unlock(); }
    }
}
