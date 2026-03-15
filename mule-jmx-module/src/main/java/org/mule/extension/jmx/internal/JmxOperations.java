package org.mule.extension.jmx.internal;

import java.util.*;

import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;

import static org.mule.runtime.extension.api.annotation.param.MediaType.APPLICATION_JSON;

/**
 * JMX metrics operations. All operations return JSON strings.
 */
public class JmxOperations {

    /**
     * Collects all JMX metrics: CPU, memory, GC, threads, class loading, runtime, buffer pools.
     */
    @DisplayName("Collect All Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectMetrics(@Config JmxConfiguration config) {
        return JsonUtil.toJson(MetricsCollector.collectAll());
    }

    /**
     * Collects CPU metrics only.
     */
    @DisplayName("Collect CPU Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectCpu(@Config JmxConfiguration config) {
        return JsonUtil.toJson(MetricsCollector.collectCpu());
    }

    /**
     * Collects memory metrics (heap, non-heap, pools).
     */
    @DisplayName("Collect Memory Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectMemory(@Config JmxConfiguration config) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("memory", MetricsCollector.collectMemory());
        m.put("memoryPools", MetricsCollector.collectMemoryPools());
        return JsonUtil.toJson(m);
    }

    /**
     * Collects GC metrics.
     */
    @DisplayName("Collect GC Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectGc(@Config JmxConfiguration config) {
        return JsonUtil.toJson(MetricsCollector.collectGc());
    }

    /**
     * Collects thread metrics with state breakdown.
     */
    @DisplayName("Collect Thread Metrics")
    @MediaType(value = APPLICATION_JSON, strict = false)
    public String collectThreads(@Config JmxConfiguration config) {
        return JsonUtil.toJson(MetricsCollector.collectThreads());
    }
}
