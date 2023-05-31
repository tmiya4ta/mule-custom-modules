package org.mule.extension.csvfilesplit.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

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

import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
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

import static org.mule.runtime.extension.api.annotation.param.Optional.PAYLOAD;

/**
 * This class is a container for operations, every public method in this class
 * will be taken as an extension operation.
 */
public class CsvFileSplitOperations {

	private Stream<Path> splitCsvByCmd(String splitCmd, String tmpDir, Path srcFilePath, long unitNum)
			throws IOException, InterruptedException {

		ProcessBuilder builder = new ProcessBuilder(splitCmd, "-l", String.valueOf(unitNum), srcFilePath.toString());
		builder.directory(new File(tmpDir));
		builder.redirectErrorStream(true);
		Process process = builder.start();
		process.waitFor();
		return Files.list(Path.of(tmpDir));
	}

	private Stream<Path> splitCsvByImpl(String tmpDir, Path srcFilePath, long unitNum, long chunkSize) throws IOException {
		FileSystem fs = FileSystems.getDefault();

		try (BufferedReader rdr = Files.newBufferedReader(srcFilePath)) {
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
			    
			    if (chunkCounter == chunkSize) {
				outFileName = "s_" + String.format("%05d", fileSeqNum);
				outPath = fs.getPath(tmpDir, outFileName);
				String content = sj.toString() + "\n";
				Files.write(outPath, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
				sj = new StringJoiner("\n");
				chunkCounter = 0;
				
			    } 

			    if(lineCounter == unitNum) {
				outFileName = "s_" + String.format("%05d", fileSeqNum);
				outPath = fs.getPath(tmpDir, outFileName);
				String content = sj.toString() + "\n";
				Files.write(outPath, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
				sj = new StringJoiner("\n");
				chunkCounter = 0;
				lineCounter = 0;
				fileSeqNum++;
			    }
			}

			String remainedContent = sj.toString() + "\n";
			System.out.println("Remained length: " + remainedContent.length());
			if (1 < remainedContent.length()) {
				outFileName = "s_" + fileSeqNum;
				outPath = fs.getPath(tmpDir, outFileName);
				Files.write(outPath, remainedContent.getBytes());
			}

		}

		return Files.list(Path.of(tmpDir));

	}

	@DisplayName("Split")
	@Throws(ExecuteErrorsProvider.class)
	@MediaType(value = ANY, strict = false)
	public String[] splitCsv(@Config CsvFileSplitConfiguration configuration,
				 @Expression(ExpressionSupport.SUPPORTED) String srcFilePath,
				 @Expression(ExpressionSupport.SUPPORTED) @Optional(defaultValue = "10000") String line,
				 @Optional(defaultValue = "1000") long chunkSize) {
	    
		Stream<Path> result;
		try {
			String workDir = configuration.getWorkDir();
			Files.createDirectories(Path.of(workDir));
			String tmpDir = Files.createTempDirectory(Path.of(workDir), "mule-").toFile().getAbsolutePath();
			Path path = Paths.get(srcFilePath);
			long unitNum = Long.parseLong(line);

			String cmd = configuration.getSplitCmd();

			if (cmd == null || cmd.isEmpty()) {
				System.out.println("Use Java implementation");
				result = splitCsvByImpl(tmpDir, path, unitNum, chunkSize);
			} else {
				System.out.println("Use split command");
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
	public String concatCsv(@Config CsvFileSplitConfiguration configuration,
			@DisplayName("Files") @Optional(defaultValue = PAYLOAD) @Expression(ExpressionSupport.SUPPORTED) ArrayList<String> files,
			@DisplayName("Target File Path") @Optional(defaultValue = "/tmp/result.csv") @Expression(ExpressionSupport.SUPPORTED) String targetFilePath) {

		try {
			File targetFile = new File(targetFilePath);
			String cmd = configuration.getConcatCmd();

			ArrayList<String> cmdWithArgs = new ArrayList<String>();
			cmdWithArgs.add(cmd);
			cmdWithArgs.addAll(files);
			ProcessBuilder builder = new ProcessBuilder(cmdWithArgs);
			builder.redirectOutput(targetFile);
			builder.redirectErrorStream(true);

			// System.out.println("Command: " + builder.command());
			Process process = builder.start();
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ModuleException(CsvFileSplitErrors.INTERRUPTED, e);
		} catch (IOException e) {
			throw new ModuleException(CsvFileSplitErrors.INVALID_PARAMETER, e);
		}
		return targetFilePath;
	}
}
