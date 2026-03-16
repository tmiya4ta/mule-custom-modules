package org.mule.extension.jmx.internal;

import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Operations(JmxOperations.class)
public class JmxConfiguration implements Initialisable, Disposable {

    private static final Logger logger = LoggerFactory.getLogger(JmxConfiguration.class);

    @Parameter
    @Optional(defaultValue = "5000")
    @DisplayName("Log Buffer Size")
    @Summary("Number of log entries to keep in the in-memory ring buffer")
    private int logBufferSize;

    @Parameter
    @Optional
    @DisplayName("Log Directory")
    @Summary("Path to log directory. Auto-detected from ${mule.home}/logs if blank")
    private String logDir;

    private LogRingBuffer ringBuffer;

    @Override
    public void initialise() throws InitialisationException {
        ringBuffer = new LogRingBuffer(logBufferSize);
        try {
            ringBuffer.install();
            logger.info("JMX log ring buffer installed (capacity={})", logBufferSize);
        } catch (Exception e) {
            logger.warn("Failed to install log ring buffer: {}", e.getMessage());
        }
    }

    @Override
    public void dispose() {
        if (ringBuffer != null) {
            ringBuffer.uninstall();
            logger.info("JMX log ring buffer uninstalled");
        }
    }

    public LogRingBuffer getRingBuffer() { return ringBuffer; }
    public String getLogDir() { return logDir; }
    public int getLogBufferSize() { return logBufferSize; }
}
