
package org.mule.extension.csvfilesplit;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.hamcrest.core.Is.is;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.junit.Test;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import java.nio.file.Path;
import java.nio.file.Paths;


// import org.mule.runtime.extension.api.runtime.streaming.PagingProvider;
import org.mule.extension.csvfilesplit.internal.CsvFileSplitConnection;
import org.mule.runtime.core.internal.streaming.object.ManagedCursorIteratorProvider;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)

public class CsvFileSplitOperationsTestCase extends MuleArtifactFunctionalTestCase {

    @Override
    protected String getConfigFile() {
	return "test-mule-config.xml";
    }

    // Tools
    private void cleanUp(String[] paths) throws Exception {
	Arrays.asList(paths).forEach(path -> {
		try {
		    Files.delete(Paths.get(path));
		} catch (IOException e) {
		    System.out.println("An error occurred.");
		    e.printStackTrace();
		}});
	Files.delete(Paths.get(paths[0]).getParent());
    }


    
    private void assertResultLineCount(long count, String filePath, boolean isDeleteResultFile) throws IOException {

	Path path = Paths.get(filePath);
	try (Stream<String> stream = Files.lines(path)) {
	    long numberOfLines = stream.count();
	    assertThat(count, is(equalTo(numberOfLines)));
	} catch (IOException e) {
	    System.out.println("An error occurred.");
	    e.printStackTrace();	    
	} finally {
	    if(isDeleteResultFile) {
		Files.delete(path);
	    }
	}
    }
    

    private void runFlowAndAssertResult(long count, String flowName) throws Exception {
	String dstPathStr = (String) flowRunner(flowName).run()
	    .getMessage()
	    .getPayload()
	    .getValue();
	assertResultLineCount(count, dstPathStr, false);	
    }


    @Before
    // Clean CSV in /tmp
    public void setup() {
	Path tmpPath = Paths.get("/tmp/mule-work");
	
	try {
	    if(Files.exists(tmpPath)) {
		Files.list(Paths.get("/tmp/mule-work"))
		    .filter(Files::isRegularFile)
		    .filter(path -> path.toString().endsWith(".csv"))
		    .forEach(path -> {
			    try {
				Files.delete(path);
			    } catch (IOException e) {
				System.out.println("An error occurred.");
				e.printStackTrace();
			    }});
	    }

	} catch (IOException e) {
	    System.out.println("An error occurred.");
	    e.printStackTrace();
	}
    }

    // Test

    @Test
    public void executeSplitCsvOnly_500_100_20() throws Exception {
	String[] dstPathArray = (String[]) flowRunner("split-csv-only-500-100-20").run()
	    .getMessage()
	    .getPayload()
	    .getValue();

	assertThat(dstPathArray.length, is(equalTo(20)));
    }

    @Test
    public void executeSplitCsvAndConcatOperation_50_100_200() throws Exception {
	runFlowAndAssertResult(10000, "split-csv-50-100-200");
    }
    
    @Test
    public void executeSplitCsvAndConcatOperation_22_33_455() throws Exception {
	runFlowAndAssertResult(10000, "split-csv-22-33-455");
    }

    @Test
    public void executeSplitAndConcatOperation_50000_100_1() throws Exception {
	runFlowAndAssertResult(10000, "split-csv-50000-100-1");
    }

    @Test
    public void executeSplitAndConcatOperation_100_50000_1() throws Exception {
	runFlowAndAssertResult(10000, "split-csv-100-50000-1");
    }


    @Test
    public void executeSplitCsvByCmdAndConcatOperation_50_100_200() throws Exception {
	runFlowAndAssertResult(10000, "split-csv-by-cmd-50-100-200");
    }

    @Test
    public void executeSplitFromStreamAndConcatOperation_50_100_200() throws Exception {
	runFlowAndAssertResult(10000,"split-csv-from-stream-50-100-200");
    }



    @Test
    // Added prefix z to run this test at last.
    public void z0_executeCleanDir() throws Exception {
	flowRunner("clean-temporary-dir-test-1").run();

	assertThat(Files.exists(Paths.get("/tmp/mule-work/test-1")), is(false));

    }

    @Test
    // Added prefix z to run this test at last.
    public void z1_executeCleanDir() throws Exception {
	flowRunner("clean-temporary-dir-all").run();

	assertThat(Files.exists(Paths.get("/tmp/mule-work")), is(false));

    }


    // You should prepare large file due to limitation of github for upload of large file.
    // @Test
    public void executeSplitLargeFileAndConcatOperation_50_100_200() throws Exception {
	runFlowAndAssertResult(100000000, "split-csv-large-file-50-100-200");
    }



}
