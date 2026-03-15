# mule-custom-modules

Custom modules and extensions for MuleSoft Mule 4.

[日本語版はこちら / Japanese](README_ja.md)

## Modules

### mule-data-partition-module

Streaming data partitioner for CSV and JSON. Splits large InputStreams into size-based (and/or item-count-based) partitions without loading entire data into memory.

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

### mule-chterm-module

Browser-based terminal console for Mule applications. Provides a web UI with xterm.js for interactive shell access to the Mule runtime container.

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

Then browse to `/chterm/` to access the terminal.

### mule-csv-file-split-module

File-based CSV splitter. Splits large CSV files by line count and concatenates them back.

- Split by line count with optional external `split` command
- `PagingProvider` based streaming partition
- File concatenation with zero-copy `FileChannel.transferTo()`

### mule-jp-characters-module

Japanese full-width / half-width character converter.

## Reference Applications

| App | Description |
|-----|-------------|
| `mule-data-partition-app` | Reference app for data-partition module (`/test/csv`, `/test/json`, `/test/csv-stream`, `/test/json-stream`) |
| `mule-term-app` | Reference app for chterm module — browser terminal console |

## Build

Requires Java 17 and Maven 3.x.

```bash
# Build a module
cd mule-data-partition-module
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install -DskipTests

# Build a test app
cd mule-data-partition-app
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package -DskipTests -DattachMuleSources
```

## Deploy

```bash
# Upload to Anypoint Exchange
yaac upload asset target/<artifact>.jar -g <org> -a <asset-id> -v <version>

# Deploy to CloudHub 2.0
yaac deploy app <org> <env> <app-name> target=<target> -g <org> -a <asset-id> -v <version> v-cores=0.1
```
