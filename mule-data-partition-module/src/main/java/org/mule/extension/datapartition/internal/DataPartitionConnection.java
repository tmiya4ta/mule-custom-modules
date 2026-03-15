package org.mule.extension.datapartition.internal;

public final class DataPartitionConnection {

    private final String id;

    public DataPartitionConnection(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void invalidate() {
    }
}
