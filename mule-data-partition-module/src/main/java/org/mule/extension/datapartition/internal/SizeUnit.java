package org.mule.extension.datapartition.internal;

public enum SizeUnit {
    KB(1024L),
    MB(1024L * 1024L),
    GB(1024L * 1024L * 1024L);

    private final long bytes;

    SizeUnit(long bytes) {
        this.bytes = bytes;
    }

    public long toBytes(long size) {
        return size * bytes;
    }
}
