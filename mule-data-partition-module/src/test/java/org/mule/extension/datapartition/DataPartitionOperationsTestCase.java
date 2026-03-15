package org.mule.extension.datapartition;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.Matchers.greaterThan;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mule.extension.datapartition.internal.DataPartitionConfiguration;
import org.mule.extension.datapartition.internal.DataPartitionConnection;
import org.mule.extension.datapartition.internal.DataPartitionOperations;
import org.mule.extension.datapartition.internal.SizeUnit;
import org.mule.runtime.extension.api.runtime.streaming.PagingProvider;

import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DataPartitionOperationsTestCase {

    private final DataPartitionOperations operations = new DataPartitionOperations();
    private final DataPartitionConfiguration config = new DataPartitionConfiguration();
    private final DataPartitionConnection connection = new DataPartitionConnection("test");

    private String generateCsvData(int rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,name,email,department,salary\n");
        for (int i = 1; i <= rows; i++) {
            sb.append(i).append(",user_").append(i)
              .append(",user_").append(i).append("@example.com")
              .append(",dept_").append(i % 10)
              .append(",").append(50000 + (i * 100))
              .append("\n");
        }
        return sb.toString();
    }

    private String generateJsonData(int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(i)
              .append(",\"name\":\"user_").append(i).append("\"")
              .append(",\"email\":\"user_").append(i).append("@example.com\"")
              .append(",\"department\":\"dept_").append(i % 10).append("\"")
              .append(",\"salary\":").append(50000 + (i * 100))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String readInputStream(InputStream is) throws Exception {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private List<String> drainPagingProvider(PagingProvider<DataPartitionConnection, InputStream> provider) throws Exception {
        List<String> partitions = new ArrayList<>();
        List<InputStream> page;
        while ((page = provider.getPage(connection)) != null) {
            for (InputStream is : page) {
                partitions.add(readInputStream(is));
                is.close();
            }
        }
        return partitions;
    }

    @Test
    public void t01_partitionCsvWithHeader() throws Exception {
        String csvData = generateCsvData(100);
        InputStream input = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionCsv(config, input, 1, SizeUnit.KB, 0, true);

        List<String> partitions = drainPagingProvider(provider);

        int totalDataLines = 0;
        for (String partition : partitions) {
            String[] lines = partition.split("\n");
            assertThat("First line should be header", lines[0], is("id,name,email,department,salary"));
            totalDataLines += lines.length - 1;
        }

        assertThat("Should have multiple partitions", partitions.size(), greaterThan(1));
        assertThat("Total data lines should match input", totalDataLines, is(100));

        provider.close(connection);
    }

    @Test
    public void t02_partitionCsvNoHeader() throws Exception {
        String csvData = generateCsvData(50);
        InputStream input = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionCsv(config, input, 1, SizeUnit.KB, 0, false);

        List<String> partitions = drainPagingProvider(provider);

        int totalLines = 0;
        for (String partition : partitions) {
            String[] lines = partition.split("\n");
            totalLines += lines.length;
        }

        assertThat("Should have multiple partitions", partitions.size(), greaterThan(1));
        assertThat("Total lines should match input", totalLines, is(51));

        provider.close(connection);
    }

    @Test
    public void t03_partitionCsvSinglePartition() throws Exception {
        String csvData = generateCsvData(100);
        InputStream input = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionCsv(config, input, 10, SizeUnit.KB, 0, true);

        List<String> partitions = drainPagingProvider(provider);
        assertThat("Data fits in one partition", partitions.size(), is(1));

        provider.close(connection);
    }

    @Test
    public void t04_partitionCsvDataIntegrity() throws Exception {
        int rowCount = 200;
        String csvData = generateCsvData(rowCount);
        InputStream input = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionCsv(config, input, 1, SizeUnit.KB, 0, false);

        List<String> partitions = drainPagingProvider(provider);

        StringBuilder reconstructed = new StringBuilder();
        for (String partition : partitions) {
            reconstructed.append(partition);
        }

        assertThat("Reconstructed data should match original", reconstructed.toString(), is(csvData));

        provider.close(connection);
    }

    @Test
    public void t05_partitionJsonBasic() throws Exception {
        String jsonData = generateJsonData(50);
        InputStream input = new ByteArrayInputStream(jsonData.getBytes(StandardCharsets.UTF_8));
        ObjectMapper objectMapper = new ObjectMapper();

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionJson(config, input, 1, SizeUnit.KB, 0);

        List<String> partitions = drainPagingProvider(provider);

        int totalObjects = 0;
        for (String partition : partitions) {
            JsonNode node = objectMapper.readTree(partition);
            assertThat("Partition should be an array", node.isArray(), is(true));
            totalObjects += node.size();
        }

        assertThat("Should have multiple partitions", partitions.size(), greaterThan(1));
        assertThat("Total objects should match input", totalObjects, is(50));

        provider.close(connection);
    }

    @Test
    public void t06_partitionJsonSinglePartition() throws Exception {
        String jsonData = generateJsonData(5);
        InputStream input = new ByteArrayInputStream(jsonData.getBytes(StandardCharsets.UTF_8));
        ObjectMapper objectMapper = new ObjectMapper();

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionJson(config, input, 10, SizeUnit.KB, 0);

        List<String> partitions = drainPagingProvider(provider);

        int totalObjects = 0;
        for (String partition : partitions) {
            JsonNode node = objectMapper.readTree(partition);
            assertThat("Partition should be an array", node.isArray(), is(true));
            totalObjects += node.size();
        }

        assertThat("Total objects should match input", totalObjects, is(5));

        provider.close(connection);
    }

    @Test
    public void t07_partitionJsonDataIntegrity() throws Exception {
        int count = 100;
        String jsonData = generateJsonData(count);
        InputStream input = new ByteArrayInputStream(jsonData.getBytes(StandardCharsets.UTF_8));
        ObjectMapper objectMapper = new ObjectMapper();

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionJson(config, input, 1, SizeUnit.KB, 0);

        List<String> partitions = drainPagingProvider(provider);

        List<JsonNode> allObjects = new ArrayList<>();
        for (String partition : partitions) {
            JsonNode array = objectMapper.readTree(partition);
            for (JsonNode obj : array) {
                allObjects.add(obj);
            }
        }

        assertThat("Total objects should match", allObjects.size(), is(count));
        assertThat("First object id", allObjects.get(0).get("id").asInt(), is(0));
        assertThat("Last object id", allObjects.get(count - 1).get("id").asInt(), is(count - 1));

        provider.close(connection);
    }

    @Test
    public void t08_partitionCsvEmptyInput() throws Exception {
        InputStream input = new ByteArrayInputStream(new byte[0]);

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionCsv(config, input, 1, SizeUnit.KB, 0, true);

        List<String> partitions = drainPagingProvider(provider);
        assertThat("Empty input should produce no partitions", partitions.size(), is(0));

        provider.close(connection);
    }

    @Test
    public void t09_partitionJsonEmptyArray() throws Exception {
        InputStream input = new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8));

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionJson(config, input, 1, SizeUnit.KB, 0);

        List<String> partitions = drainPagingProvider(provider);
        assertThat("Empty array should produce no partitions", partitions.size(), is(0));

        provider.close(connection);
    }

    @Test
    public void t10_partitionCsvMBUnit() throws Exception {
        String csvData = generateCsvData(500);
        InputStream input = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionCsv(config, input, 1, SizeUnit.MB, 0, true);

        List<String> partitions = drainPagingProvider(provider);
        assertThat("All data fits in 1MB partition", partitions.size(), is(1));

        String[] lines = partitions.get(0).split("\n");
        assertThat("Header + 500 lines", lines.length, is(501));

        provider.close(connection);
    }

    @Test
    public void t11_partitionCsvByMaxItems() throws Exception {
        // 100 rows, partition every 25 lines → 4 partitions
        String csvData = generateCsvData(100);
        InputStream input = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionCsv(config, input, 10, SizeUnit.MB, 25, true);

        List<String> partitions = drainPagingProvider(provider);

        assertThat("Should have 4 partitions", partitions.size(), is(4));

        int totalDataLines = 0;
        for (String partition : partitions) {
            String[] lines = partition.split("\n");
            assertThat("First line should be header", lines[0], is("id,name,email,department,salary"));
            int dataLines = lines.length - 1;
            assertThat("Each partition should have <= 25 data lines", dataLines, is(25));
            totalDataLines += dataLines;
        }
        assertThat("Total data lines", totalDataLines, is(100));

        provider.close(connection);
    }

    @Test
    public void t12_partitionJsonByMaxItems() throws Exception {
        // 50 objects, partition every 10 → 5 partitions
        String jsonData = generateJsonData(50);
        InputStream input = new ByteArrayInputStream(jsonData.getBytes(StandardCharsets.UTF_8));
        ObjectMapper objectMapper = new ObjectMapper();

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionJson(config, input, 10, SizeUnit.MB, 10);

        List<String> partitions = drainPagingProvider(provider);

        assertThat("Should have 5 partitions", partitions.size(), is(5));

        int totalObjects = 0;
        for (String partition : partitions) {
            JsonNode node = objectMapper.readTree(partition);
            assertThat("Partition should be an array", node.isArray(), is(true));
            assertThat("Each partition should have 10 objects", node.size(), is(10));
            totalObjects += node.size();
        }
        assertThat("Total objects", totalObjects, is(50));

        provider.close(connection);
    }

    @Test
    public void t13_partitionCsvSizeAndItemsBothLimit() throws Exception {
        // Both limits set: 1KB size AND 10 items — whichever comes first
        String csvData = generateCsvData(100);
        InputStream input = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        PagingProvider<DataPartitionConnection, InputStream> provider =
                operations.partitionCsv(config, input, 1, SizeUnit.KB, 10, true);

        List<String> partitions = drainPagingProvider(provider);

        int totalDataLines = 0;
        for (String partition : partitions) {
            String[] lines = partition.split("\n");
            assertThat("First line should be header", lines[0], is("id,name,email,department,salary"));
            int dataLines = lines.length - 1;
            // Each partition should have at most 10 data lines
            assertThat("Should not exceed maxItems", dataLines <= 10, is(true));
            totalDataLines += dataLines;
        }
        assertThat("Total data lines", totalDataLines, is(100));

        provider.close(connection);
    }
}
