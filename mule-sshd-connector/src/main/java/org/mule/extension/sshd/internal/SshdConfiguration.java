package org.mule.extension.sshd.internal;

import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.Sources;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.annotation.param.display.Password;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandDirectStreamsAware;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

@Operations(SshdOperations.class)
@Sources(SftpUploadListener.class)
public class SshdConfiguration implements Initialisable, Disposable {

    private static final Logger logger = LoggerFactory.getLogger(SshdConfiguration.class);

    @Parameter
    @DisplayName("SFTP Port")
    @Summary("Port for the embedded SFTP server")
    @Optional(defaultValue = "2222")
    private int port;

    @Parameter
    @DisplayName("Username")
    @Summary("Username for SFTP authentication")
    @Optional(defaultValue = "sftpuser")
    private String username;

    @Parameter
    @Password
    @DisplayName("Password")
    @Summary("Password for SFTP authentication")
    private String password;

    @Parameter
    @DisplayName("Root Directory")
    @Summary("Root directory for SFTP file access")
    @Optional(defaultValue = "/tmp/sshd-root")
    private String rootDirectory;

    @Parameter
    @DisplayName("Host Key Path")
    @Summary("Path to store the generated host key")
    @Optional(defaultValue = "/tmp/sshd-hostkey.ser")
    private String hostKeyPath;

    private SshServer sshServer;

    @Override
    public void initialise() throws InitialisationException {
        try {
            Path rootPath = Paths.get(rootDirectory);
            if (!Files.exists(rootPath)) {
                Files.createDirectories(rootPath);
                logger.info("Created SFTP root directory: {}", rootPath);
            }

            sshServer = SshServer.setUpDefaultServer();
            sshServer.setPort(port);
            sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get(hostKeyPath)));

            SftpSubsystemFactory sftpFactory = new SftpSubsystemFactory();
            sshServer.setSubsystemFactories(Collections.singletonList(sftpFactory));

            String configuredUsername = this.username;
            String configuredPassword = this.password;
            sshServer.setPasswordAuthenticator((user, pass, session) ->
                configuredUsername.equals(user) && configuredPassword.equals(pass));

            org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory fsFactory =
                new org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory(rootPath);
            sshServer.setFileSystemFactory(fsFactory);

            // Command factory for exec channel (pwd, etc.)
            // Mule SFTP connector uses 'pwd' to resolve home directory
            sshServer.setCommandFactory((channelSession, command) -> new Command() {
                private OutputStream out;
                private OutputStream err;
                private ExitCallback exitCallback;

                @Override
                public void setInputStream(InputStream in) {}

                @Override
                public void setOutputStream(OutputStream out) { this.out = out; }

                @Override
                public void setErrorStream(OutputStream err) { this.err = err; }

                @Override
                public void setExitCallback(ExitCallback callback) { this.exitCallback = callback; }

                @Override
                public void start(ChannelSession channel, Environment env) throws IOException {
                    String response;
                    if ("pwd".equalsIgnoreCase(command.trim())) {
                        response = "/\n";
                    } else {
                        response = "Unknown command: " + command + "\n";
                    }
                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    exitCallback.onExit(0);
                }

                @Override
                public void destroy(ChannelSession channel) {}
            });

            sshServer.start();
            logger.info("SSHD SFTP server started on port {} with root directory: {}", port, rootPath);
        } catch (IOException e) {
            throw new InitialisationException(
                org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage(
                    "Failed to start SSHD server: " + e.getMessage()), e, this);
        }
    }

    @Override
    public void dispose() {
        if (sshServer != null && sshServer.isOpen()) {
            try {
                sshServer.stop(true);
                logger.info("SSHD SFTP server stopped");
            } catch (IOException e) {
                logger.error("Error stopping SSHD server", e);
            }
        }
    }

    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getRootDirectory() { return rootDirectory; }
    public SshServer getSshServer() { return sshServer; }
}
