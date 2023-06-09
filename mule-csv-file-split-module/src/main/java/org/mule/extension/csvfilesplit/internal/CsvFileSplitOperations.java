package org.mule.extension.csvfilesplit.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.lang.reflect.Array;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Stream;
import java.io.BufferedReader;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.extension.api.runtime.streaming.PagingProvider;
import org.mule.runtime.extension.api.runtime.operation.Result;
    
import static org.mule.runtime.extension.api.annotation.param.Optional.PAYLOAD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * This class is a container for operations, every public method in this class
 * will be taken as an extension operation.
 */
public class CsvFileSplitOperations {
    static Logger logger = LoggerFactory.getLogger(CsvFileSplitOperations.class);
    
    private Stream<Path> splitCsvByCmd(String splitCmd, String tmpDir, Path srcFilePath, long unitNum)
	throws IOException, InterruptedException {

	ProcessBuilder builder = new ProcessBuilder(splitCmd, "-l", String.valueOf(unitNum),
						    srcFilePath.toFile().getAbsolutePath());
	builder.directory(new File(tmpDir));
	builder.redirectErrorStream(true);
	Process process = builder.start();
	process.waitFor();
	return Files.list(Paths.get(tmpDir));
    }

    private Stream<Path> splitCsvByImpl(String tmpDir, Path srcFilePath, InputStream input, long unitNum, long chunkSize) throws IOException {
	FileSystem fs = FileSystems.getDefault();
	BufferedReader rdr = null;
	
	try  {
	    if (srcFilePath != null) {
		rdr = new BufferedReader(new FileReader(srcFilePath.toFile()));
	    } else {
		rdr = new BufferedReader(new InputStreamReader(input));
	    }
	    String text;
	    long lineCounter = 0;
	    long fileSeqNum = 0;

	    String outFileName;
	    Path outPath;

	    long chunkCounter = 0;
	    
	    StringJoiner sj = new StringJoiner("\n");

	    while (true) {
		text = rdr.readLine();
		if (text == null)
		    break;
				
		sj.add(text);
		chunkCounter++;
		lineCounter++;
			    
		if (((chunkCounter == chunkSize) && (lineCounter < unitNum) || (lineCounter == unitNum))) {
		    outFileName = "s_" + String.format("%05d", fileSeqNum);
		    outPath = fs.getPath(tmpDir, outFileName);
		    String content = sj.toString() + "\n";
		    Files.write(outPath, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
		    sj = new StringJoiner("\n");
		    chunkCounter = 0;
		} 

		if(lineCounter == unitNum) {
		    chunkCounter = 0;
		    lineCounter = 0;
		    fileSeqNum++;
		}
	    }

	    String remainedContent = sj.toString();
	    logger.info("Remained length: " + remainedContent.length());
	    if (1 < remainedContent.length()) {
		outFileName = "s_" + fileSeqNum;
		outPath = fs.getPath(tmpDir, outFileName);
		Files.write(outPath, remainedContent.getBytes());
	    }

	} catch (IOException e) {
	    e.printStackTrace();
	    throw new ModuleException(CsvFileSplitErrors.INTERRUPTED, e);
	}  finally {
	    if (rdr !=null) {
		rdr.close();
		logger.info("Reader closed");
	    }
	}

	return Files.list(Paths.get(tmpDir));

    }

    @DisplayName("Split")
    @Throws(ExecuteErrorsProvider.class)
    @MediaType(value = ANY, strict = false)
    public String[] splitCsv(@Config CsvFileSplitConfiguration configuration,
			     @Expression(ExpressionSupport.SUPPORTED) @Optional String srcFilePath,
			     @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = PAYLOAD) InputStream input,
			     @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "10000") @Summary("Lines in a file") String line,
			     @Optional(defaultValue = "1000") @Summary("Unit lines for a split operation as a batch") long chunkSize) {
	    
	Stream<Path> result;
	try {
	    String workDir = configuration.getWorkDir();
	    Files.createDirectories(Paths.get(workDir));
	    String tmpDir = Files.createTempDirectory(Paths.get(workDir), "mule-").toFile().getAbsolutePath();
	    
	    long unitNum = Long.parseLong(line);

	    String cmd = configuration.getSplitCmd();

	    Path path = null;
	    if (srcFilePath != null) {
		path = Paths.get(srcFilePath);
	    }
	    
	    if (cmd == null || cmd.isEmpty()) {
		result = splitCsvByImpl(tmpDir, path, input, unitNum, chunkSize);
	    } else {
		result = splitCsvByCmd(cmd, tmpDir, path, unitNum);
		// result = splitCsvByCmd(configuration, srcFilePath, line);
	    }
	} catch (InterruptedException e) {
	    throw new ModuleException(CsvFileSplitErrors.INTERRUPTED, e);
	} catch (IOException e) {
	    throw new ModuleException(CsvFileSplitErrors.INVALID_PARAMETER, e);
	}

	return result.map((x) -> x.toString()).toArray(String[]::new);
    }

    @DisplayName("Concat")
    @Throws(ExecuteErrorsProvider.class)
    @MediaType(value = ANY, strict = false)
    public String concat(@Config CsvFileSplitConfiguration configuration,
			 @DisplayName("Files") @Optional(defaultValue = PAYLOAD) @Expression(ExpressionSupport.SUPPORTED) @NullSafe ArrayList<String> files,
			 @DisplayName("Target file path") @Optional(defaultValue = "/tmp/result.csv") @Expression(ExpressionSupport.SUPPORTED) String dstFilePath,
			 @DisplayName("Delete temporary files") @Optional(defaultValue = "true") @Expression(ExpressionSupport.SUPPORTED) boolean isDeleteTempFiles) {

	File dstFile = new File(dstFilePath);
	FileChannel dstChannel = null;
	try {
	    dstChannel = FileChannel.open(dstFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
	    for (String file : files) {
		File srcFile = new File(file);
		FileChannel srcChannel = FileChannel.open(srcFile.toPath(), StandardOpenOption.READ);
		srcChannel.transferTo(0, srcChannel.size(), dstChannel);
		srcChannel.close();
	    }
	} catch (IOException e) {
	    throw new ModuleException(CsvFileSplitErrors.INVALID_PARAMETER, e);
	} finally {
	    try {
		dstChannel.close();
		    
		if (isDeleteTempFiles) {
		    for (String file : files) {
			File srcFile = new File(file);
			srcFile.delete();
		    }
		    String firstFile = files.get(0);
		    Path filePath = Paths.get(firstFile);
		    logger.info("Delete temporary directory: " + filePath.getParent());
		    Files.delete(filePath.getParent());
		}
		    
	    } catch (IOException e) {
		throw new ModuleException(CsvFileSplitErrors.INVALID_PARAMETER, e);
	    }
	}
	return dstFilePath;
    }


    @DisplayName("Partition")
    @Throws(ExecuteErrorsProvider.class)
    @MediaType(value = ANY, strict = false)
    public  PagingProvider<CsvFileSplitConnection, List<Map<String, String>>> partition(@Config CsvFileSplitConfiguration configuration,
				 @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = PAYLOAD) InputStream input,
				 @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "10000") @Summary("Lines in a file") String line) {

	
	return new PagingProvider<CsvFileSplitConnection, List<Map<String, String>>>() {
	    private BufferedReader rdr = null;
	    
	    private final AtomicBoolean initialised = new AtomicBoolean(false);
	 

	    private void initializePagingProvider(CsvFileSplitConnection connection) {
		rdr = new BufferedReader(new InputStreamReader(input));
		logger.info("Initialized paging provider");
	    }
	    
	    @Override
	    public List<List<Map<String, String>>> getPage(CsvFileSplitConnection connection) {
		if (initialised.compareAndSet(false, true)) {
		    initializePagingProvider(connection);
		}
		List<Map<String, String>> lines = new ArrayList<Map<String, String>>();
		int unitNum = Integer.parseInt(line);
		logger.trace("unitNum: " + unitNum);
		
		for (int i = 0; i < unitNum; i++) {
		    try {
			String line = rdr.readLine();
			if (line == null) {
			    break;
			}
			Map<String, String> map = new HashMap<String, String>();
			String[] cols = line.split(",");

			for (int j = 0; j < cols.length; j++) {
			    map.put("column_" + j, cols[j]);
			}
			
			lines.add(map);
		    } catch (IOException e) {
			logger.error("Error reading line", e);
			throw new ModuleException(CsvFileSplitErrors.INVALID_PARAMETER, e);
		    }
		}

		if (lines.size() == 0) {
		    logger.info("No more data");
		    return null;
		} else {
		    return new ArrayList<List<Map<String, String>>>(Arrays.asList(lines));
		}
	    }

	    @Override
	    public java.util.Optional<Integer> getTotalResults(CsvFileSplitConnection connection) {
		return null;
	    }

	    @Override
	    public void close(CsvFileSplitConnection connection) throws MuleException {
		if (rdr != null) {
		    try {rdr.close();} catch (IOException e) {
			logger.error("Error closing reader", e);
		    }
		    logger.info("Reader closed");
		}
	    }
	};
    }
}


