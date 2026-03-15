package org.mule.extension.jmx.internal;

import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.sdk.api.annotation.JavaVersionSupport;
import static org.mule.sdk.api.meta.JavaVersion.JAVA_17;

@Xml(prefix = "jmx")
@Extension(name = "JMX")
@Configurations(JmxConfiguration.class)
@JavaVersionSupport({JAVA_17})
public class JmxExtension {
}
