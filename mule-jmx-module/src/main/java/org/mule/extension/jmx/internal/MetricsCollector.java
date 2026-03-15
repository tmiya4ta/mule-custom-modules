package org.mule.extension.jmx.internal;

import java.lang.management.*;
import java.util.*;

/**
 * Collects all available JMX metrics from the JVM.
 */
public final class MetricsCollector {

    private MetricsCollector() {}

    public static Map<String, Object> collectAll() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("timestamp", System.currentTimeMillis());
        metrics.put("os", collectOs());
        metrics.put("cpu", collectCpu());
        metrics.put("memory", collectMemory());
        metrics.put("memoryPools", collectMemoryPools());
        metrics.put("gc", collectGc());
        metrics.put("threads", collectThreads());
        metrics.put("classLoading", collectClassLoading());
        metrics.put("runtime", collectRuntime());
        metrics.put("bufferPools", collectBufferPools());
        return metrics;
    }

    static Map<String, Object> collectOs() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", os.getName());
        m.put("arch", os.getArch());
        m.put("version", os.getVersion());
        m.put("availableProcessors", os.getAvailableProcessors());
        m.put("systemLoadAverage", os.getSystemLoadAverage());

        // com.sun.management extensions
        if (os instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean ext = (com.sun.management.OperatingSystemMXBean) os;
            m.put("totalPhysicalMemorySize", ext.getTotalMemorySize());
            m.put("freePhysicalMemorySize", ext.getFreeMemorySize());
            m.put("committedVirtualMemorySize", ext.getCommittedVirtualMemorySize());
            m.put("totalSwapSpaceSize", ext.getTotalSwapSpaceSize());
            m.put("freeSwapSpaceSize", ext.getFreeSwapSpaceSize());
        }
        return m;
    }

    static Map<String, Object> collectCpu() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> m = new LinkedHashMap<>();

        if (os instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean ext = (com.sun.management.OperatingSystemMXBean) os;
            m.put("processCpuLoad", ext.getProcessCpuLoad());
            m.put("processCpuTime", ext.getProcessCpuTime());
            m.put("systemCpuLoad", ext.getCpuLoad());
        }
        m.put("availableProcessors", os.getAvailableProcessors());
        m.put("systemLoadAverage", os.getSystemLoadAverage());
        return m;
    }

    static Map<String, Object> collectMemory() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("heap", memoryUsageMap(mem.getHeapMemoryUsage()));
        m.put("nonHeap", memoryUsageMap(mem.getNonHeapMemoryUsage()));
        m.put("objectPendingFinalizationCount", mem.getObjectPendingFinalizationCount());
        return m;
    }

    static List<Map<String, Object>> collectMemoryPools() {
        List<Map<String, Object>> pools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", pool.getName());
            p.put("type", pool.getType().toString());
            p.put("usage", memoryUsageMap(pool.getUsage()));
            MemoryUsage peak = pool.getPeakUsage();
            if (peak != null) p.put("peakUsage", memoryUsageMap(peak));
            MemoryUsage collectionUsage = pool.getCollectionUsage();
            if (collectionUsage != null) p.put("collectionUsage", memoryUsageMap(collectionUsage));
            pools.add(p);
        }
        return pools;
    }

    static List<Map<String, Object>> collectGc() {
        List<Map<String, Object>> gcs = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("name", gc.getName());
            g.put("collectionCount", gc.getCollectionCount());
            g.put("collectionTime", gc.getCollectionTime());
            g.put("memoryPoolNames", Arrays.asList(gc.getMemoryPoolNames()));
            gcs.add(g);
        }
        return gcs;
    }

    static Map<String, Object> collectThreads() {
        ThreadMXBean t = ManagementFactory.getThreadMXBean();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("threadCount", t.getThreadCount());
        m.put("peakThreadCount", t.getPeakThreadCount());
        m.put("totalStartedThreadCount", t.getTotalStartedThreadCount());
        m.put("daemonThreadCount", t.getDaemonThreadCount());

        // Thread state breakdown
        long[] ids = t.getAllThreadIds();
        ThreadInfo[] infos = t.getThreadInfo(ids);
        Map<String, Integer> states = new LinkedHashMap<>();
        for (ThreadInfo info : infos) {
            if (info != null) {
                String state = info.getThreadState().name();
                states.merge(state, 1, Integer::sum);
            }
        }
        m.put("states", states);
        return m;
    }

    static Map<String, Object> collectClassLoading() {
        ClassLoadingMXBean cl = ManagementFactory.getClassLoadingMXBean();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("loadedClassCount", cl.getLoadedClassCount());
        m.put("totalLoadedClassCount", cl.getTotalLoadedClassCount());
        m.put("unloadedClassCount", cl.getUnloadedClassCount());
        return m;
    }

    static Map<String, Object> collectRuntime() {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vmName", rt.getVmName());
        m.put("vmVendor", rt.getVmVendor());
        m.put("vmVersion", rt.getVmVersion());
        m.put("specVersion", rt.getSpecVersion());
        m.put("uptime", rt.getUptime());
        m.put("startTime", rt.getStartTime());
        m.put("inputArguments", rt.getInputArguments());
        m.put("pid", rt.getPid());
        return m;
    }

    static List<Map<String, Object>> collectBufferPools() {
        List<Map<String, Object>> pools = new ArrayList<>();
        for (BufferPoolMXBean bp : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", bp.getName());
            p.put("count", bp.getCount());
            p.put("memoryUsed", bp.getMemoryUsed());
            p.put("totalCapacity", bp.getTotalCapacity());
            pools.add(p);
        }
        return pools;
    }

    private static Map<String, Object> memoryUsageMap(MemoryUsage usage) {
        if (usage == null) return Collections.emptyMap();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("init", usage.getInit());
        m.put("used", usage.getUsed());
        m.put("committed", usage.getCommitted());
        m.put("max", usage.getMax());
        return m;
    }
}
