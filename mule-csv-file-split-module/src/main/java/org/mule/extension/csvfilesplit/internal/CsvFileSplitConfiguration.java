package org.mule.extension.csvfilesplit.internal;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Path;

/**
 * This class represents an extension configuration, values set in this class
 * are commonly used across multiple
 * operations since they represent something core from the extension.
 */
@Operations(CsvFileSplitOperations.class)
// @ConnectionProviders(CsvFileSplitConnectionProvider.class)
public class CsvFileSplitConfiguration {

    // @Parameter
    // private CsvFileSplitCommandProperties param;
	
	// @Parameter
	// @DisplayName("Use external split command")
	// @Optional
	// private boolean useExternalSplitCommand;

	@Parameter
	@Path
	@DisplayName("External Split Command")
	@Optional
	private String splitCmd;

	@Parameter
	@DisplayName("External Concat Command")
	@Optional
	private String concatCmd;


	@Parameter
	@Optional(defaultValue="/tmp/mule-work")
	private String workDir;
	
	public String getSplitCmd() {
		return splitCmd;
	}

	public String getConcatCmd() {
	    return concatCmd;
	}

	public String getWorkDir() {
	    return workDir;
	}
}
