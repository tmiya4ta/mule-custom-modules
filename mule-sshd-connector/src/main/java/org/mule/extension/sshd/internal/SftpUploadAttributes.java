package org.mule.extension.sshd.internal;

import java.io.Serializable;

/**
 * Attributes for SFTP upload events, accessible as 'attributes' in Mule flows.
 */
public class SftpUploadAttributes implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final String path;
    private final long size;
    private final String username;
    private final String event;

    public SftpUploadAttributes(String filename, String path, long size, String username, String event) {
        this.filename = filename;
        this.path = path;
        this.size = size;
        this.username = username;
        this.event = event;
    }

    public String getFilename() { return filename; }
    public String getPath() { return path; }
    public long getSize() { return size; }
    public String getUsername() { return username; }
    public String getEvent() { return event; }

    @Override
    public String toString() {
        return "SftpUploadAttributes{event=" + event + ", path=" + path + ", filename=" + filename
                + ", size=" + size + ", username=" + username + "}";
    }
}
