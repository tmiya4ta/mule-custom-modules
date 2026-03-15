package org.mule.extension.sshd.internal;

import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.sdk.api.annotation.JavaVersionSupport;
import static org.mule.sdk.api.meta.JavaVersion.JAVA_17;

@Xml(prefix = "sshd")
@Extension(name = "SSHD")
@Configurations(SshdConfiguration.class)
@JavaVersionSupport({JAVA_17})
public class SshdExtension {
}
