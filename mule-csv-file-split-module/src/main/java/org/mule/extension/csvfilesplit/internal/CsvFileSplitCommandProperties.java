package org.mule.extension.csvfilesplit.internal;

import org.mule.runtime.extension.api.annotation.param.ExclusiveOptionals;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;

@ExclusiveOptionals

public class CsvFileSplitCommandProperties {


    @Parameter
    @DisplayName("Use external command")
    public boolean useExternalSplitCommand;

    @Parameter
    @DisplayName("Command path")
    @Optional
    private String splitCmd;
    
}
