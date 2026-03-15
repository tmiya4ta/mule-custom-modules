package org.mule.extension.datapartition.internal;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.mule.runtime.extension.api.runtime.streaming.PagingProvider;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;
import static org.mule.runtime.extension.api.annotation.param.Optional.PAYLOAD;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataPartitionOperations {

    private static final Logger logger = LoggerFactory.getLogger(DataPartitionOperations.class);

    private static int safeAvailable(InputStream is) {
        try { return is.available(); } catch (Exception e) { return -2; }
    }

    private static boolean safeReady(java.io.BufferedReader r) {
        try { return r.ready(); } catch (Exception e) { return false; }
    }

    /**
     * InputStream that deletes the backing temp file when closed.
     */
    static class DeleteOnCloseInputStream extends FilterInputStream {
        private final Path path;

        DeleteOnCloseInputStream(InputStream in, Path path) {
            super(in);
            this.path = path;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                try {
                    Files.deleteIfExists(path);
                    logger.debug("Deleted temp file: {}", path);
                } catch (IOException e) {
                    logger.warn("Failed to delete temp file: {}", path);
                }
            }
        }
    }

    /**
     * Partitions CSV InputStream into chunks of approximately the specified size.
     * Each page returned by the PagingProvider contains one partition as an InputStream.
     * Data is streamed through temp files — memory usage is minimal (line buffer only).
     * Disk usage: ~2 × partitionSize at peak (one being written, one being read).
     */
    @DisplayName("Partition CSV")
    @Throws(DataPartitionErrorProvider.class)
    @MediaType(value = ANY, strict = false)
    public PagingProvider<DataPartitionConnection, InputStream> partitionCsv(
            @Config DataPartitionConfiguration configuration,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = PAYLOAD) InputStream input,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "50")
            @Summary("Partition size") int partitionSize,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "MB")
            @Summary("Size unit: KB, MB, or GB") SizeUnit sizeUnit,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "0")
            @Summary("Max items (lines) per partition. 0 = no limit (size only)") int maxItems,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "true")
            @Summary("Include header row in each partition") boolean includeHeader) {

        final long partitionSizeBytes = sizeUnit.toBytes(partitionSize);

        return new PagingProvider<DataPartitionConnection, InputStream>() {
            private BufferedReader reader;
            private final AtomicBoolean initialized = new AtomicBoolean(false);
            private String header;
            private String pendingLine;
            private boolean eof = false;
            private Path tempDir;
            private int partitionIndex = 0;

            private void initialize() {
                reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                try {
                    tempDir = Files.createTempDirectory("datapartition-csv-");
                    tempDir.toFile().deleteOnExit();

                    if (includeHeader) {
                        header = reader.readLine();
                        if (header == null) {
                            eof = true;
                        }
                    }
                } catch (IOException e) {
                    throw new ModuleException(DataPartitionErrors.IO_OPERATION_FAILED, e);
                }
                logger.info("CSV partitioner initialized, partitionSize={}{}, maxItems={}, tempDir={}",
                        partitionSize, sizeUnit.name(), maxItems, tempDir);
            }

            @Override
            public List<InputStream> getPage(DataPartitionConnection connection) {
                if (initialized.compareAndSet(false, true)) {
                    initialize();
                }

                if (eof) {
                    return null;
                }

                try {
                    Path tempFile = tempDir.resolve("p_" + String.format("%05d", partitionIndex++) + ".csv");
                    tempFile.toFile().deleteOnExit();

                    OutputStream os = new BufferedOutputStream(Files.newOutputStream(tempFile));
                    long bytesWritten = 0;

                    // Write header
                    if (includeHeader && header != null) {
                        byte[] headerBytes = (header + "\n").getBytes(StandardCharsets.UTF_8);
                        os.write(headerBytes);
                        bytesWritten += headerBytes.length;
                    }

                    // Write pending line from previous partition
                    int itemCount = 0;
                    if (pendingLine != null) {
                        byte[] lineBytes = (pendingLine + "\n").getBytes(StandardCharsets.UTF_8);
                        os.write(lineBytes);
                        bytesWritten += lineBytes.length;
                        pendingLine = null;
                        itemCount++;
                    }

                    String line;
                    while ((line = reader.readLine()) != null) {
                        byte[] lineBytes = (line + "\n").getBytes(StandardCharsets.UTF_8);

                        // Check size limit
                        if (bytesWritten + lineBytes.length > partitionSizeBytes && bytesWritten > 0) {
                            pendingLine = line;
                            break;
                        }

                        os.write(lineBytes);
                        bytesWritten += lineBytes.length;
                        itemCount++;

                        // Check item count limit
                        if (maxItems > 0 && itemCount >= maxItems) {
                            break;
                        }
                    }

                    os.close();

                    if (line == null && pendingLine == null) {
                        eof = true;
                    }

                    // Check if we wrote anything beyond header
                    long headerSize = 0;
                    if (includeHeader && header != null) {
                        headerSize = (header + "\n").getBytes(StandardCharsets.UTF_8).length;
                    }
                    if (bytesWritten <= headerSize) {
                        Files.deleteIfExists(tempFile);
                        return null;
                    }

                    logger.debug("CSV partition {} written: {} bytes to {}", partitionIndex - 1, bytesWritten, tempFile);
                    InputStream fis = Files.newInputStream(tempFile);
                    return Collections.singletonList(fis);

                } catch (IOException e) {
                    throw new ModuleException(DataPartitionErrors.IO_OPERATION_FAILED, e);
                }
            }

            @Override
            public java.util.Optional<Integer> getTotalResults(DataPartitionConnection connection) {
                return java.util.Optional.empty();
            }

            @Override
            public void close(DataPartitionConnection connection) throws MuleException {
                if (reader != null) {
                    try { reader.close(); } catch (IOException e) {
                        logger.error("Error closing CSV reader", e);
                    }
                }
                cleanupTempDir();
                logger.info("CSV partitioner closed");
            }

            private void cleanupTempDir() {
                if (tempDir != null) {
                    try {
                        Files.list(tempDir).forEach(f -> {
                            try { Files.deleteIfExists(f); } catch (IOException e) {
                                logger.warn("Failed to delete: {}", f);
                            }
                        });
                        Files.deleteIfExists(tempDir);
                    } catch (IOException e) {
                        logger.warn("Failed to cleanup tempDir: {}", tempDir);
                    }
                }
            }
        };
    }

    /**
     * Partitions a JSON array InputStream into chunks of approximately the specified size.
     * Each page returned by the PagingProvider contains one partition as an InputStream.
     * Each partition is a valid JSON array. Data is streamed through temp files.
     */
    @DisplayName("Partition JSON")
    @Throws(DataPartitionErrorProvider.class)
    @MediaType(value = ANY, strict = false)
    public PagingProvider<DataPartitionConnection, InputStream> partitionJson(
            @Config DataPartitionConfiguration configuration,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = PAYLOAD) InputStream input,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "50")
            @Summary("Partition size") int partitionSize,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "MB")
            @Summary("Size unit: KB, MB, or GB") SizeUnit sizeUnit,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "0")
            @Summary("Max items (objects) per partition. 0 = no limit (size only)") int maxItems) {

        final long partitionSizeBytes = sizeUnit.toBytes(partitionSize);

        return new PagingProvider<DataPartitionConnection, InputStream>() {
            private JsonParser parser;
            private ObjectMapper mapper;
            private final AtomicBoolean initialized = new AtomicBoolean(false);
            private String pendingObject;
            private boolean eof = false;
            private Path tempDir;
            private int partitionIndex = 0;

            private void initialize() {
                try {
                    mapper = new ObjectMapper();
                    parser = mapper.getFactory().createParser(input);
                    tempDir = Files.createTempDirectory("datapartition-json-");
                    tempDir.toFile().deleteOnExit();

                    JsonToken token = parser.nextToken();
                    if (token != JsonToken.START_ARRAY) {
                        throw new ModuleException(DataPartitionErrors.INVALID_PARAMETER,
                                new IllegalArgumentException("Input must be a JSON array. Got: " + token));
                    }
                    logger.info("JSON partitioner initialized, partitionSize={}{}, maxItems={}, tempDir={}",
                            partitionSize, sizeUnit.name(), maxItems, tempDir);
                } catch (IOException e) {
                    throw new ModuleException(DataPartitionErrors.IO_OPERATION_FAILED, e);
                }
            }

            @Override
            public List<InputStream> getPage(DataPartitionConnection connection) {
                if (initialized.compareAndSet(false, true)) {
                    initialize();
                }

                if (eof) {
                    return null;
                }

                try {
                    Path tempFile = tempDir.resolve("p_" + String.format("%05d", partitionIndex++) + ".json");
                    tempFile.toFile().deleteOnExit();

                    OutputStream os = new BufferedOutputStream(Files.newOutputStream(tempFile));
                    long bytesWritten = 0;
                    boolean first = true;

                    os.write('[');
                    bytesWritten++;

                    // Write pending object
                    int itemCount = 0;
                    if (pendingObject != null) {
                        byte[] objBytes = pendingObject.getBytes(StandardCharsets.UTF_8);
                        os.write(objBytes);
                        bytesWritten += objBytes.length;
                        pendingObject = null;
                        first = false;
                        itemCount++;
                    }

                    boolean reachedEnd = false;
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        if (parser.currentToken() == null) {
                            reachedEnd = true;
                            break;
                        }

                        JsonNode node = mapper.readTree(parser);
                        String objStr = node.toString();
                        byte[] objBytes = objStr.getBytes(StandardCharsets.UTF_8);

                        // Check size limit
                        long additionalBytes = (first ? 0 : 1) + objBytes.length;
                        if (!first && bytesWritten + additionalBytes > partitionSizeBytes) {
                            pendingObject = objStr;
                            break;
                        }

                        if (!first) {
                            os.write(',');
                            bytesWritten++;
                        }
                        os.write(objBytes);
                        bytesWritten += objBytes.length;
                        first = false;
                        itemCount++;

                        // Check item count limit
                        if (maxItems > 0 && itemCount >= maxItems) {
                            break;
                        }
                    }

                    if (reachedEnd || (parser.currentToken() == JsonToken.END_ARRAY && pendingObject == null)) {
                        eof = true;
                    }

                    os.write(']');
                    bytesWritten++;
                    os.close();

                    if (first) {
                        // No objects written
                        Files.deleteIfExists(tempFile);
                        return null;
                    }

                    logger.debug("JSON partition {} written: {} bytes to {}", partitionIndex - 1, bytesWritten, tempFile);
                    InputStream fis = Files.newInputStream(tempFile);
                    return Collections.singletonList(fis);

                } catch (IOException e) {
                    throw new ModuleException(DataPartitionErrors.IO_OPERATION_FAILED, e);
                }
            }

            @Override
            public java.util.Optional<Integer> getTotalResults(DataPartitionConnection connection) {
                return java.util.Optional.empty();
            }

            @Override
            public void close(DataPartitionConnection connection) throws MuleException {
                if (parser != null) {
                    try { parser.close(); } catch (IOException e) {
                        logger.error("Error closing JSON parser", e);
                    }
                }
                cleanupTempDir();
                logger.info("JSON partitioner closed");
            }

            private void cleanupTempDir() {
                if (tempDir != null) {
                    try {
                        Files.list(tempDir).forEach(f -> {
                            try { Files.deleteIfExists(f); } catch (IOException e) {
                                logger.warn("Failed to delete: {}", f);
                            }
                        });
                        Files.deleteIfExists(tempDir);
                    } catch (IOException e) {
                        logger.warn("Failed to cleanup tempDir: {}", tempDir);
                    }
                }
            }
        };
    }

    /**
     * Counts bytes and lines in an InputStream by streaming through it (8KB buffer).
     * The InputStream is fully consumed. Memory usage: ~8KB regardless of stream size.
     * Returns a Map with "bytes" and "lines" keys.
     */
    @DisplayName("Count Lines")
    @Throws(DataPartitionErrorProvider.class)
    @MediaType(value = ANY, strict = false)
    public java.util.Map<String, Long> countLines(
            @Config DataPartitionConfiguration configuration,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = PAYLOAD) InputStream input) {
        try {
            long[] result = StreamUtils.countBytesAndLines(input);
            java.util.Map<String, Long> map = new java.util.LinkedHashMap<>();
            map.put("bytes", result[0]);
            map.put("lines", result[1]);
            return map;
        } catch (IOException e) {
            throw new ModuleException(DataPartitionErrors.IO_OPERATION_FAILED, e);
        }
    }

    /**
     * Counts bytes and top-level items in a JSON array InputStream using Jackson streaming.
     * The InputStream is fully consumed. Memory usage: minimal (token-level parsing only).
     * Returns a Map with "bytes" and "items" keys.
     */
    @DisplayName("Count JSON Items")
    @Throws(DataPartitionErrorProvider.class)
    @MediaType(value = ANY, strict = false)
    public java.util.Map<String, Long> countJsonItems(
            @Config DataPartitionConfiguration configuration,
            @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = PAYLOAD) InputStream input) {
        try {
            ObjectMapper om = new ObjectMapper();
            com.fasterxml.jackson.core.JsonParser jp = om.getFactory().createParser(input);
            long items = 0;
            long bytes = 0;

            JsonToken token = jp.nextToken(); // START_ARRAY
            if (token != JsonToken.START_ARRAY) {
                throw new ModuleException(DataPartitionErrors.INVALID_PARAMETER,
                        new IllegalArgumentException("Input must be a JSON array. Got: " + token));
            }

            while (jp.nextToken() != JsonToken.END_ARRAY) {
                if (jp.currentToken() == null) break;
                jp.skipChildren(); // skip the entire object/value without building a tree
                items++;
            }

            bytes = jp.getCurrentLocation().getByteOffset();
            jp.close();

            java.util.Map<String, Long> map = new java.util.LinkedHashMap<>();
            map.put("bytes", bytes);
            map.put("items", items);
            return map;
        } catch (IOException e) {
            throw new ModuleException(DataPartitionErrors.IO_OPERATION_FAILED, e);
        }
    }
}
