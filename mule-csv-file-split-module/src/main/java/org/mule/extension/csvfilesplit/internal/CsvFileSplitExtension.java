package org.mule.extension.csvfilesplit.internal;

import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;
import org.mule.runtime.extension.api.annotation.Operations;


/**
 * This is the main class of an extension, is the entry point from which configurations, connection providers, operations
 * and sources are going to be declared.
 */
@Xml(prefix = "csvfilesplit")
@Extension(name = "CsvFileSplit")
@ErrorTypes(CsvFileSplitErrors.class)
@Configurations(CsvFileSplitConfiguration.class)

public class CsvFileSplitExtension {

}
