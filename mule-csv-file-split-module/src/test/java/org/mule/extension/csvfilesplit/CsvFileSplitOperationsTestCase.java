package org.mule.extension.csvfilesplit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.junit.Test;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
// import org.mule.runtime.extension.api.runtime.streaming.PagingProvider;
import org.mule.extension.csvfilesplit.internal.CsvFileSplitConnection;
import org.mule.runtime.core.internal.streaming.object.ManagedCursorIteratorProvider;
    
public class CsvFileSplitOperationsTestCase extends MuleArtifactFunctionalTestCase {

    /**
     * Specifies the mule config xml with the flows that are going to be executed in the tests, this file lives in the test resources.
     */
    @Override
    protected String getConfigFile() {
	return "test-mule-config.xml";
    }

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
    
    @Test
    public void executeSplitCsvOperation() throws Exception {
	String dstFilePath = (String) flowRunner("split-csv-22-33-455").run()
	    .getMessage()
	    .getPayload()
	    .getValue();
	System.out.println(dstFilePath);

    }

    //  @Test
    public void executeSplitCsvByCommandOperation() throws Exception {
	String[] paths = (String[]) flowRunner("split-csv-by-command").run()
	    .getMessage()
	    .getPayload()
	    .getValue();

	  
	Path filePath = Paths.get(paths[0]);
	String fileName = filePath.getFileName().toString();
    

	Arrays.asList(paths).forEach(path -> {
		try {
		    Files.delete(Paths.get(path));
		} catch (IOException e) {
		    System.out.println("An error occurred.");
		    e.printStackTrace();
		}});
	Files.delete(filePath.getParent());
	assertThat(fileName, is("xaa"));

    }

    //  @Test
    public void executeSplitFromStreamOperation() throws Exception {
	String[] paths = (String[]) flowRunner("split-csv-from-stream").run()
	    .getMessage()
	    .getPayload()
	    .getValue();

	Path filePath = Paths.get(paths[0]);
	String fileName = filePath.getFileName().toString();
    

	Arrays.asList(paths).forEach(path -> {
		try {
		    Files.delete(Paths.get(path));
		} catch (IOException e) {
		    System.out.println("An error occurred.");
		    e.printStackTrace();
		}});
	Files.delete(filePath.getParent());
	assertThat(fileName, is("s_00000"));
    }

    //  @Test
    public void executeSplitFromStreamOperationWithPartition() throws Exception {
	flowRunner("split-csv-from-stream-with-partition").run()
	    .getMessage()
	    .getPayload()
	    .getValue();
      
	// Path filePath = Paths.get(paths[0]);
	// String fileName = filePath.getFileName().toString();
    

	// Arrays.asList(paths).forEach(path -> {
	// 	  try {
	// 	      Files.delete(Paths.get(path));
	// 	  } catch (IOException e) {
	// 	      System.out.println("An error occurred.");
	// 	      e.printStackTrace();
	// 	  }});
	// Files.delete(filePath.getParent());
	// assertThat(fileName, is("s_00000"));
    }


    //  @Test
    public void executeConcatOperation() throws Exception {
	String path = (String) flowRunner("split-and-concat").run()
	    .getMessage()
	    .getPayload()
	    .getValue();

	  
	Path filePath = Paths.get(path);
	if (!Files.exists(filePath)) {
	    assertThat(Files.exists(filePath), is(true));
	}
	Files.delete(filePath);	  
    }

}
