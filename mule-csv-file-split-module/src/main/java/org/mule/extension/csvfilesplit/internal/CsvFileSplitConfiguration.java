package org.mule.extension.csvfilesplit.internal;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Path;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.api.meta.model.display.PathModel.Type;

/**
 * This class represents an extension configuration, values set in this class
 * are commonly used across multiple
 * operations since they represent something core from the extension.
 */
@Operations(CsvFileSplitOperations.class)
@ConnectionProviders(CsvFileSplitConnectionProvider.class)
public class CsvFileSplitConfiguration {

	@Parameter
	@Path(type = Type.FILE, acceptsUrls = false)
	@DisplayName("External split command")
	@Summary("Blank to use Java implementation")
	@Optional
	private String splitCmd;

	@Parameter
	@Path(type = Type.DIRECTORY, acceptsUrls = false)
	@Optional(defaultValue="/tmp/mule-work")
	@Summary("A direcotry to store temporary files. A directory is created under this with temporary name by each split operation.")
	@Example("/tmp/mule-work")
	private String workDir;
	
	public String getSplitCmd() {
		return splitCmd;
	}

	public String getWorkDir() {
	    return workDir;
	}
}
