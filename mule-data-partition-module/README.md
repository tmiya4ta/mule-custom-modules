# DataPartition Extension for Mule 4

[Japanese version / 日本語版](README_ja.md)

A Mule 4 custom module that partitions large CSV and JSON streams into smaller chunks with constant memory usage (~20KB). Designed for processing gigabyte-scale files on resource-constrained environments like CloudHub 2.0 with 0.1 vCore.

## Overview

When processing large datasets in MuleSoft, loading the entire file into memory causes OutOfMemoryError. This module solves that problem by streaming data through temporary files and exposing partitions via Mule's `PagingProvider` interface, which integrates naturally with `<foreach>`.

**Key capabilities:**

- Partition CSV files by size (KB/MB/GB) and/or item count, with automatic header propagation
- Partition JSON arrays by size and/or item count, producing valid JSON arrays per partition
- Count lines in CSV or items in JSON arrays without loading data into memory
- Tested: 1GB CSV and 1GB JSON on 0.1 vCore (256MB heap) without OOM

## How It Works

### PagingProvider and Lazy Evaluation

The partition operations return a `PagingProvider<DataPartitionConnection, InputStream>`. Mule's `<foreach>` drives iteration lazily -- it calls `getPage()` to fetch one partition at a time. Each call reads just enough data from the source stream to fill one partition, writes it to a temp file, and returns an `InputStream` pointing to that file.

This means only one partition exists in memory/disk at a time (plus possibly the next one being written).

### Temp-File-Backed Streaming

The data flow for each partition is:

```
Source InputStream
    --> BufferedReader.readLine() (CSV) / Jackson streaming parser (JSON)
    --> Write lines/objects to temp file via BufferedOutputStream
    --> Return DeleteOnCloseInputStream wrapping FileInputStream on the temp file
    --> Consumer (foreach body) reads from the FileInputStream
    --> On close, temp file is automatically deleted
```

### DeleteOnCloseInputStream

A custom `FilterInputStream` subclass that wraps a `FileInputStream` and automatically deletes the backing temp file when `close()` is called. This ensures no temp files leak, even if processing is interrupted. Additionally, `deleteOnExit()` is set on all temp files as a safety net.

### Memory Usage Analysis

| Component | Memory |
|-----------|--------|
| BufferedReader (CSV) | ~8KB (default buffer) |
| Jackson JsonParser (JSON) | ~8KB (internal buffer) |
| BufferedOutputStream | ~8KB (default buffer) |
| Line/object string | Varies per line (typically < 1KB) |
| **Total** | **~20KB constant** |

The partition size controls the temp file size on disk, not memory. Memory usage stays constant regardless of input data size or partition size.

### Size-Based AND Item-Count-Based Partitioning

Both CSV and JSON operations support two partitioning strategies that can be combined:

- **Size-based** (`partitionSize` + `sizeUnit`): Partition breaks when accumulated bytes exceed the threshold
- **Item-count-based** (`maxItems`): Partition breaks when the number of items reaches the limit
- **Combined**: Partition breaks at whichever limit is hit first. Set `maxItems=0` to disable item-count limit.

## Operations

### Partition CSV

Splits a CSV `InputStream` into partitions. Each partition is a valid CSV file. When `includeHeader` is true, the first line of the input is treated as a header and prepended to every partition.

**Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `input` | InputStream | `#[payload]` | The CSV input stream |
| `partitionSize` | int | `50` | Target partition size |
| `sizeUnit` | KB \| MB \| GB | `MB` | Unit for partitionSize |
| `maxItems` | int | `0` | Max lines per partition (0 = no limit) |
| `includeHeader` | boolean | `true` | Include header row in each partition |

**Returns:** `PagingProvider<DataPartitionConnection, InputStream>` -- iterable of InputStream partitions.

### Partition JSON

Splits a JSON array `InputStream` into partitions. Each partition is a valid JSON array (`[...]`). The input must be a top-level JSON array.

**Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `input` | InputStream | `#[payload]` | The JSON array input stream |
| `partitionSize` | int | `50` | Target partition size |
| `sizeUnit` | KB \| MB \| GB | `MB` | Unit for partitionSize |
| `maxItems` | int | `0` | Max objects per partition (0 = no limit) |

**Returns:** `PagingProvider<DataPartitionConnection, InputStream>` -- iterable of InputStream partitions.

### Count Lines

Counts bytes and lines in an InputStream using an 8KB buffer in a single pass. The stream is fully consumed.

**Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `input` | InputStream | `#[payload]` | The input stream to count |

**Returns:** `Map<String, Long>` with keys `bytes` and `lines`.

### Count JSON Items

Counts bytes and top-level items in a JSON array using Jackson streaming parser. No tree is built -- items are skipped with `skipChildren()`. The stream is fully consumed.

**Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `input` | InputStream | `#[payload]` | The JSON array input stream |

**Returns:** `Map<String, Long>` with keys `bytes` and `items`.

## Usage Examples

### Partition CSV and process each chunk

```xml
<flow name="process-large-csv">
    <http:listener config-ref="HTTP_Listener" path="/upload-csv" allowedMethods="POST">
        <http:response statusCode="200" />
        <!-- Prevent Mule from buffering the request body -->
        <non-repeatable-stream />
    </http:listener>

    <data-partition:partition-csv partitionSize="50" sizeUnit="MB"
                                  includeHeader="true"
                                  outputMimeType="text/csv; streaming=true">
        <!-- CRITICAL: Prevent Mule from materializing all partitions -->
        <non-repeatable-iterable />
    </data-partition:partition-csv>

    <foreach>
        <logger level="INFO" message="Processing partition #[vars.counter]" />
        <!-- Each payload here is an InputStream of ~50MB CSV with header -->
        <http:request method="POST" url="http://target-api/import"
                      sendBodyMode="ALWAYS">
            <http:body>#[payload]</http:body>
        </http:request>
    </foreach>
</flow>
```

### Partition JSON array by item count

```xml
<flow name="process-large-json">
    <data-partition:partition-json partitionSize="100" sizeUnit="MB"
                                   maxItems="10000">
        <non-repeatable-iterable />
    </data-partition:partition-json>

    <foreach>
        <!-- Each payload is a valid JSON array with up to 10000 items -->
        <http:request method="POST" url="http://target-api/batch-import">
            <http:body>#[payload]</http:body>
        </http:request>
    </foreach>
</flow>
```

### Count lines before partitioning

```xml
<flow name="count-and-partition">
    <set-variable variableName="csvStream" value="#[payload]" />

    <data-partition:count-lines>
        <data-partition:input>#[vars.csvStream]</data-partition:input>
    </data-partition:count-lines>

    <logger level="INFO" message="File has #[payload.lines] lines (#[payload.bytes] bytes)" />
</flow>
```

## Important Notes

### Use `<non-repeatable-iterable />`

The partition operations return a `PagingProvider`. By default, Mule wraps the iterable in a repeatable (replayable) wrapper, which buffers **all partitions in memory** -- defeating the purpose entirely. Always add `<non-repeatable-iterable />` inside the operation tag.

### HTTP Input: Use `<non-repeatable-stream />` and streaming MIME type

When receiving large files via HTTP Listener, you must prevent Mule from buffering the request body:

1. Add `<non-repeatable-stream />` inside `<http:listener>` to disable repeatable stream buffering
2. Set `outputMimeType="text/csv; streaming=true"` on the partition operation so DataWeave does not try to parse the entire CSV

### CloudHub 2.0 Request Body Limit

CloudHub 2.0 imposes a **1GB limit** on HTTP request bodies. For files larger than 1GB, consider using an object store (S3, etc.) as an intermediary.

### Disk Space

Each partition is written to a temp file. Peak disk usage is approximately `2 x partitionSize` (one partition being written, one being read by the consumer). Temp files are automatically cleaned up via `DeleteOnCloseInputStream` and the `PagingProvider.close()` callback.

## Build

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install
```

The module JAR can then be added as a dependency in your Mule application's `pom.xml`:

```xml
<dependency>
    <groupId>com.demo-group</groupId>
    <artifactId>mule-data-partition-module</artifactId>
    <version>0.1.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```
