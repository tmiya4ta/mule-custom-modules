package org.mule.extension.datapartition.internal;

import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;
import org.mule.sdk.api.annotation.JavaVersionSupport;
import static org.mule.sdk.api.meta.JavaVersion.JAVA_17;

@Xml(prefix = "datapartition")
@Extension(name = "DataPartition")
@ErrorTypes(DataPartitionErrors.class)
@Configurations(DataPartitionConfiguration.class)
@JavaVersionSupport({JAVA_17})
public class DataPartitionExtension {
}
