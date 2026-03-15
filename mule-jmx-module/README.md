# JMX Metrics Extension for Mule 4

[Japanese version / 日本語版](README_ja.md)

A Mule 4 custom module that exposes JVM metrics via JMX MXBeans as JSON. Provides a lightweight `/metrics` endpoint for monitoring Mule runtime health, CPU usage, memory, garbage collection, threads, and more -- without external monitoring agents.

## Overview

This module collects JVM metrics directly from `java.lang.management.ManagementFactory` and returns them as JSON. It is designed to be called from an HTTP endpoint to provide real-time observability into a running Mule application.

**Collected metric categories:**

- OS information and physical memory
- CPU load (process and system)
- Memory usage (heap, non-heap, individual memory pools with peak and collection usage)
- Garbage collector statistics
- Thread counts with state breakdown (RUNNABLE, WAITING, TIMED_WAITING, BLOCKED, etc.)
- Class loading statistics
- JVM runtime information (uptime, PID, VM version, input arguments)
- Buffer pools (direct and mapped memory)

## How It Works

### MXBeans Used

| MXBean | Metrics |
|--------|---------|
| `OperatingSystemMXBean` | OS name, arch, version, available processors, system load average |
| `com.sun.management.OperatingSystemMXBean` | Process/system CPU load, CPU time, physical/swap memory sizes |
| `MemoryMXBean` | Heap and non-heap memory usage (init, used, committed, max) |
| `MemoryPoolMXBean` | Per-pool usage, peak usage, and collection usage |
| `GarbageCollectorMXBean` | Per-collector name, collection count, collection time, memory pool names |
| `ThreadMXBean` | Thread count, peak, daemon count, total started, per-state breakdown |
| `ClassLoadingMXBean` | Loaded/unloaded class counts |
| `RuntimeMXBean` | VM name, vendor, version, uptime, start time, PID, input arguments |
| `BufferPoolMXBean` | Direct/mapped buffer count, memory used, total capacity |

### Architecture

All metric collection is implemented in `MetricsCollector.java` as static methods. `JmxOperations.java` exposes these as Mule SDK operations that serialize results to JSON strings. No external dependencies are needed beyond the JDK's built-in management APIs.

## Operations

### Collect All Metrics

Returns all JMX metrics in a single JSON response.

```xml
<jmx:collect-metrics />
```

### Collect CPU Metrics

Returns CPU-specific metrics only.

```xml
<jmx:collect-cpu />
```

**Returned fields:**

| Field | Type | Description |
|-------|------|-------------|
| `processCpuLoad` | double | CPU load by this JVM process (0.0 to 1.0) |
| `processCpuTime` | long | CPU time used by this process in nanoseconds |
| `systemCpuLoad` | double | Total system CPU load (0.0 to 1.0) |
| `availableProcessors` | int | Number of available processors |
| `systemLoadAverage` | double | System load average for the last minute |

### Collect Memory Metrics

Returns heap, non-heap, and per-memory-pool metrics.

```xml
<jmx:collect-memory />
```

**Returned structure:**

- `memory.heap` -- `{init, used, committed, max}` in bytes
- `memory.nonHeap` -- `{init, used, committed, max}` in bytes
- `memory.objectPendingFinalizationCount` -- objects awaiting finalization
- `memoryPools[]` -- array of per-pool details:
  - `name` -- pool name (e.g., "G1 Eden Space", "Metaspace")
  - `type` -- HEAP or NON_HEAP
  - `usage` -- `{init, used, committed, max}`
  - `peakUsage` -- `{init, used, committed, max}` (highest since JVM start)
  - `collectionUsage` -- `{init, used, committed, max}` (after last GC, if available)

### Collect GC Metrics

Returns per-garbage-collector statistics.

```xml
<jmx:collect-gc />
```

**Returned fields (per collector):**

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | GC name (e.g., "G1 Young Generation", "G1 Old Generation") |
| `collectionCount` | long | Total number of collections |
| `collectionTime` | long | Total collection time in milliseconds |
| `memoryPoolNames` | string[] | Memory pools managed by this collector |

### Collect Thread Metrics

Returns thread counts and per-state breakdown.

```xml
<jmx:collect-threads />
```

**Returned fields:**

| Field | Type | Description |
|-------|------|-------------|
| `threadCount` | int | Current live thread count |
| `peakThreadCount` | int | Peak live thread count since JVM start |
| `totalStartedThreadCount` | long | Total threads ever created |
| `daemonThreadCount` | int | Current daemon thread count |
| `states` | object | Breakdown by thread state (RUNNABLE, WAITING, TIMED_WAITING, BLOCKED, NEW, TERMINATED) |

## Full JSON Response Example

Response from `collect-metrics` (all-in-one):

```json
{
  "timestamp": 1710500000000,
  "os": {
    "name": "Linux",
    "arch": "amd64",
    "version": "6.1.0",
    "availableProcessors": 2,
    "systemLoadAverage": 0.5,
    "totalPhysicalMemorySize": 4294967296,
    "freePhysicalMemorySize": 1073741824,
    "committedVirtualMemorySize": 2147483648,
    "totalSwapSpaceSize": 2147483648,
    "freeSwapSpaceSize": 2147483648
  },
  "cpu": {
    "processCpuLoad": 0.05,
    "processCpuTime": 30000000000,
    "systemCpuLoad": 0.12,
    "availableProcessors": 2,
    "systemLoadAverage": 0.5
  },
  "memory": {
    "heap": {
      "init": 268435456,
      "used": 134217728,
      "committed": 268435456,
      "max": 536870912
    },
    "nonHeap": {
      "init": 7667712,
      "used": 95000000,
      "committed": 100000000,
      "max": -1
    },
    "objectPendingFinalizationCount": 0
  },
  "memoryPools": [
    {
      "name": "G1 Eden Space",
      "type": "HEAP",
      "usage": { "init": 27262976, "used": 10485760, "committed": 27262976, "max": -1 },
      "peakUsage": { "init": 27262976, "used": 27262976, "committed": 27262976, "max": -1 },
      "collectionUsage": { "init": 27262976, "used": 0, "committed": 27262976, "max": -1 }
    },
    {
      "name": "G1 Survivor Space",
      "type": "HEAP",
      "usage": { "init": 0, "used": 3145728, "committed": 3145728, "max": -1 },
      "peakUsage": { "init": 0, "used": 3145728, "committed": 3145728, "max": -1 },
      "collectionUsage": { "init": 0, "used": 3145728, "committed": 3145728, "max": -1 }
    },
    {
      "name": "G1 Old Gen",
      "type": "HEAP",
      "usage": { "init": 241172480, "used": 50331648, "committed": 241172480, "max": 536870912 },
      "peakUsage": { "init": 241172480, "used": 50331648, "committed": 241172480, "max": 536870912 }
    },
    {
      "name": "Metaspace",
      "type": "NON_HEAP",
      "usage": { "init": 0, "used": 80000000, "committed": 82000000, "max": -1 },
      "peakUsage": { "init": 0, "used": 80000000, "committed": 82000000, "max": -1 }
    }
  ],
  "gc": [
    {
      "name": "G1 Young Generation",
      "collectionCount": 25,
      "collectionTime": 350,
      "memoryPoolNames": ["G1 Eden Space", "G1 Survivor Space"]
    },
    {
      "name": "G1 Old Generation",
      "collectionCount": 2,
      "collectionTime": 120,
      "memoryPoolNames": ["G1 Eden Space", "G1 Survivor Space", "G1 Old Gen"]
    }
  ],
  "threads": {
    "threadCount": 45,
    "peakThreadCount": 52,
    "totalStartedThreadCount": 120,
    "daemonThreadCount": 38,
    "states": {
      "RUNNABLE": 12,
      "WAITING": 18,
      "TIMED_WAITING": 14,
      "BLOCKED": 1
    }
  },
  "classLoading": {
    "loadedClassCount": 12500,
    "totalLoadedClassCount": 12600,
    "unloadedClassCount": 100
  },
  "runtime": {
    "vmName": "OpenJDK 64-Bit Server VM",
    "vmVendor": "Eclipse Adoptium",
    "vmVersion": "17.0.8+7",
    "specVersion": "17",
    "uptime": 3600000,
    "startTime": 1710496400000,
    "inputArguments": ["-Xmx512m", "-Xms256m"],
    "pid": 12345
  },
  "bufferPools": [
    {
      "name": "direct",
      "count": 10,
      "memoryUsed": 1048576,
      "totalCapacity": 1048576
    },
    {
      "name": "mapped",
      "count": 0,
      "memoryUsed": 0,
      "totalCapacity": 0
    }
  ]
}
```

## Usage Examples

### Expose a /metrics endpoint

```xml
<flow name="metrics-endpoint">
    <http:listener config-ref="HTTP_Listener" path="/metrics" allowedMethods="GET" />
    <jmx:collect-metrics />
</flow>
```

### Monitor memory only

```xml
<flow name="memory-check">
    <http:listener config-ref="HTTP_Listener" path="/metrics/memory" allowedMethods="GET" />
    <jmx:collect-memory />
</flow>
```

### Periodic health logging with Scheduler

```xml
<flow name="health-logger">
    <scheduler>
        <scheduling-strategy>
            <fixed-frequency frequency="60" timeUnit="SECONDS" />
        </scheduling-strategy>
    </scheduler>
    <jmx:collect-cpu />
    <logger level="INFO" message="CPU metrics: #[payload]" />
</flow>
```

### Check thread states for deadlock detection

```xml
<flow name="thread-monitor">
    <http:listener config-ref="HTTP_Listener" path="/metrics/threads" allowedMethods="GET" />
    <jmx:collect-threads />
    <!-- Check for BLOCKED threads -->
    <choice>
        <when expression="#[payload.states.BLOCKED default 0 > 5]">
            <logger level="WARN" message="High number of BLOCKED threads detected!" />
        </when>
    </choice>
</flow>
```

## Build

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install
```

Add the module as a dependency in your Mule application's `pom.xml`:

```xml
<dependency>
    <groupId>com.demo-group</groupId>
    <artifactId>mule-jmx-module</artifactId>
    <version>0.1.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```
