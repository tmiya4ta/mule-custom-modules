package org.mule.extension.sshd.internal;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.source.Source;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;

import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SFTP listener that triggers a Mule flow when a file is uploaded via SFTP.
 */
@Alias("listener")
@MediaType(value = MediaType.ANY, strict = false)
public class SftpUploadListener extends Source<InputStream, SftpUploadAttributes> {

    private static final Logger logger = LoggerFactory.getLogger(SftpUploadListener.class);

    @Config
    private SshdConfiguration config;

    private SourceCallback<InputStream, SftpUploadAttributes> sourceCallback;
    private SftpEventListener eventListener;

    /** Track file handles that received write operations */
    private final Set<String> writtenHandles = ConcurrentHashMap.newKeySet();

    @Override
    public void onStart(SourceCallback<InputStream, SftpUploadAttributes> sourceCallback)
            throws org.mule.runtime.api.exception.MuleException {
        this.sourceCallback = sourceCallback;

        this.eventListener = new SftpEventListener() {

            @Override
            public void written(ServerSession session, String remoteHandle,
                                FileHandle localHandle, long offset, byte[] data,
                                int dataOffset, int dataLen, Throwable thrown) {
                if (thrown == null) {
                    writtenHandles.add(remoteHandle);
                }
            }

            @Override
            public void closed(ServerSession session, String remoteHandle,
                               Handle localHandle, Throwable thrown) {
                if (thrown != null || !writtenHandles.remove(remoteHandle)) {
                    return;
                }

                // File was written and now closed — trigger the flow
                if (!(localHandle instanceof FileHandle)) {
                    return;
                }

                try {
                    Path virtualPath = localHandle.getFile();
                    // virtualPath is within the virtual FS; resolve to real path
                    String relativePath = virtualPath.toString();
                    if (relativePath.startsWith("/")) {
                        relativePath = relativePath.substring(1);
                    }
                    Path rootPath = Paths.get(config.getRootDirectory());
                    Path realPath = rootPath.resolve(relativePath);
                    String filename = virtualPath.getFileName().toString();
                    long size = Files.exists(realPath) ? Files.size(realPath) : 0;
                    String username = session.getUsername();

                    logger.info("SFTP upload completed: {} ({} bytes) by {}", relativePath, size, username);

                    byte[] content = Files.readAllBytes(realPath);

                    SftpUploadAttributes attrs = new SftpUploadAttributes(
                            filename, relativePath, size, username, "upload");

                    Result<InputStream, SftpUploadAttributes> result =
                            Result.<InputStream, SftpUploadAttributes>builder()
                                    .output(new ByteArrayInputStream(content))
                                    .attributes(attrs)
                                    .mediaType(org.mule.runtime.api.metadata.MediaType.BINARY)
                                    .build();

                    sourceCallback.handle(result);

                } catch (IOException e) {
                    logger.error("Error processing uploaded file", e);
                }
            }
        };

        // Register listener on the SftpSubsystemFactory
        config.getSshServer().getSubsystemFactories().stream()
                .filter(f -> f instanceof SftpSubsystemFactory)
                .map(f -> (SftpSubsystemFactory) f)
                .forEach(f -> f.addSftpEventListener(eventListener));

        logger.info("SFTP upload listener started");
    }

    @Override
    public void onStop() {
        if (eventListener != null) {
            config.getSshServer().getSubsystemFactories().stream()
                    .filter(f -> f instanceof SftpSubsystemFactory)
                    .map(f -> (SftpSubsystemFactory) f)
                    .forEach(f -> f.removeSftpEventListener(eventListener));
        }
        writtenHandles.clear();
        this.sourceCallback = null;
        logger.info("SFTP upload listener stopped");
    }
}
