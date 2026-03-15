package org.mule.extension.chterm.internal;

import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.annotation.param.display.Password;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Operations(ChtermOperations.class)
public class ChtermConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ChtermConfiguration.class);

    @Parameter
    @Password
    @DisplayName("Password")
    @Summary("Password required to generate exec key")
    private String password;

    private final TerminalSession session = new TerminalSession();

    public String getPassword() { return password; }

    public TerminalSession getSession() { return session; }
}
