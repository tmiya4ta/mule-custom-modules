# mule-custom-modules

Custom modules and extensions for MuleSoft Mule 4.

[日本語版はこちら / Japanese](README_ja.md)

## Overview

| Module | Description | Reference App |
|--------|-------------|---------------|
| **mule-data-partition-module** | Streaming CSV/JSON partitioner — splits large InputStream by size or item count without OOM | [mule-data-partitioner](reference-apps/mule-data-partitioner/) |
| **mule-webterm-module** | Browser-based terminal console (xterm.js) — interactive shell, file upload/download, right-click menu | [mule-webterm](reference-apps/mule-webterm/) |
| **mule-jmx-module** | JVM metrics collector via JMX — CPU, memory, GC, threads, runtime info in one JSON call | [mule-jmx-metrics](reference-apps/mule-jmx-metrics/) |
| **mule-csv-file-split-module** | File-based CSV splitter — splits by line count, concatenates with zero-copy FileChannel | — |
| **mule-jp-characters-module** | Japanese half-width / full-width character converter (hankaku ↔ zenkaku) | — |

## Modules

### [mule-data-partition-module](mule-data-partition-module/)

Streaming data partitioner for CSV and JSON. ([detailed docs](mule-data-partition-module/README.md)) Splits large InputStreams into size-based (and/or item-count-based) partitions without loading entire data into memory.

> Reference app: [reference-apps/mule-data-partitioner](reference-apps/mule-data-partitioner/) — endpoints: `/test/csv`, `/test/json`, `/test/csv-stream`, `/test/json-stream`

**Key features:**
- Partition by size (KB/MB/GB) and/or item count
- Temp-file backed streaming — memory usage is ~20KB regardless of data size
- OOM-safe: tested with 1GB+ CSV on 0.1 vCore CloudHub 2.0
- Returns `PagingProvider<InputStream>` — each partition is consumed lazily via `foreach`
- Includes `count-lines` and `count-json-items` operations for streaming item counting

**Usage:**
```xml
<datapartition:config name="Config" />

<datapartition:partition-csv config-ref="Config"
    partitionSize="50" sizeUnit="MB" maxItems="0" includeHeader="true">
    <non-repeatable-iterable />
</datapartition:partition-csv>

<foreach>
    <!-- payload = InputStream of ~50MB CSV partition -->
</foreach>
```

### mule-webterm-module

Browser-based terminal console for Mule applications. Provides a web UI with xterm.js for interactive shell access to the Mule runtime container.

> Reference app: [reference-apps/mule-webterm](reference-apps/mule-webterm/) — browse to `/` or `/chterm/` for the terminal UI

**Key features:**
- xterm.js terminal with full color and bash keybinding support (Ctrl-A/E/K/U/W/C/D/L/R)
- Password-protected exec key authentication
- One-shot command execution (`/api/exec`)
- Interactive PTY shell session (`/api/term/*`)
- File upload/download with chunked transfer (10MB chunks, supports large files)
- File browser with directory navigation
- Right-click context menu (Copy, Paste, Clear, Upload, Download)

**Usage:**
```xml
<chterm:config name="Chterm" password="yourpassword" />

<flow name="chterm-flow">
    <http:listener config-ref="HTTP_Listener_config" path="/chterm/*" />
    <chterm:handle-request config-ref="Chterm" />
</flow>
```

### [mule-jmx-module](mule-jmx-module/)

JVM metrics collector via JMX. ([detailed docs](mule-jmx-module/README.md)) Collects CPU, memory, GC, threads, class loading, runtime, and buffer pool metrics.

> Reference app: [reference-apps/mule-jmx-metrics](reference-apps/mule-jmx-metrics/) — endpoints: `/metrics`, `/metrics/cpu`, `/metrics/memory`, `/metrics/gc`, `/metrics/threads`

**Operations:**
- `collect-metrics` — All metrics in one call
- `collect-cpu` — CPU load, process CPU time, system load average
- `collect-memory` — Heap/non-heap usage, memory pool details
- `collect-gc` — GC collection count and time
- `collect-threads` — Thread count, state breakdown (RUNNABLE/WAITING/TIMED_WAITING)

**Usage:**
```xml
<jmx:config name="JMX_Config" />

<flow name="metrics-flow">
    <http:listener config-ref="HTTP_Listener_config" path="/metrics" />
    <jmx:collect-metrics config-ref="JMX_Config" />
</flow>
```

### mule-csv-file-split-module

File-based CSV splitter. Splits large CSV files by line count and concatenates them back.

- Split by line count with optional external `split` command
- `PagingProvider` based streaming partition
- File concatenation with zero-copy `FileChannel.transferTo()`

### mule-jp-characters-module

Japanese full-width / half-width character converter.

- `Hankaku -> Zenkaku` — half-width to full-width (katakana, ASCII, letters, numbers, spaces)
- `Zenkaku -> Hankaku` — full-width to half-width

## Build

Requires Java 17 and Maven 3.x.

```bash
# Build a module
cd mule-data-partition-module
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install -DskipTests

# Build a reference app
cd reference-apps/mule-data-partitioner
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package -DskipTests -DattachMuleSources
```

## Publish to Exchange

Modules can be published to Anypoint Exchange so other apps can use them as Maven dependencies.

### From Anypoint Studio

1. Right-click the module project in Package Explorer
2. Select **Anypoint Platform** > **Publish to Exchange**
3. Set **Group Id** to your organization ID
4. Set **Version** and click **Finish**

### From Maven CLI

Set the `groupId` in `pom.xml` to your organization ID, then:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean deploy -DskipTests
```

> Requires Exchange credentials in `~/.m2/settings.xml` under the `anypoint-exchange-v3` server ID.

### Using the module

Once published, add to your app's `pom.xml` using the organization ID as groupId:

```xml
<dependency>
    <groupId>${orgId}</groupId>
    <artifactId>mule-data-partition-module</artifactId>
    <version>0.1.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

## Deploy

### From Anypoint Studio

1. Right-click the app project in Package Explorer
2. Select **Anypoint Platform** > **Deploy to CloudHub 2.0**
3. Select the target environment and runtime
4. Click **Deploy**

### From Runtime Manager UI

1. Go to [Anypoint Runtime Manager](https://anypoint.mulesoft.com/cloudhub/)
2. Click **Deploy Application**
3. Upload the JAR file (`target/<app>-mule-application.jar`)
4. Select target, vCores, and runtime version
5. Click **Deploy Application**

### From Maven CLI

Add `mule-maven-plugin` deploy configuration to `pom.xml`, then:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean deploy -DmuleDeploy -DskipTests
```

> See [MuleSoft documentation](https://docs.mulesoft.com/cloudhub-2/ch2-deploy-maven) for `mule-maven-plugin` configuration.

## Test

Each reference app has an integration test script:

```bash
reference-apps/mule-data-partitioner/test.sh https://<app-url>
reference-apps/mule-jmx-metrics/test.sh https://<app-url>
reference-apps/mule-webterm/test.sh https://<app-url> <password>
```
