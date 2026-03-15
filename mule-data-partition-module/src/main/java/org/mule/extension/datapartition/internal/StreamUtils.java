package org.mule.extension.datapartition.internal;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility for counting items in an InputStream without loading all content into memory.
 * Reads byte by byte through a buffer, counting newlines (for CSV) or top-level objects (for JSON).
 */
public final class StreamUtils {

    private StreamUtils() {}

    /**
     * Counts the number of lines in an InputStream by counting newline bytes.
     * Uses a small buffer — memory usage is ~8KB regardless of stream size.
     * The InputStream is fully consumed but NOT closed by this method.
     */
    public static long countLines(InputStream is) throws IOException {
        byte[] buf = new byte[8192];
        long lines = 0;
        int bytesRead;
        while ((bytesRead = is.read(buf)) != -1) {
            for (int i = 0; i < bytesRead; i++) {
                if (buf[i] == '\n') {
                    lines++;
                }
            }
        }
        return lines;
    }

    /**
     * Counts bytes and lines in a single pass.
     * Returns long[2]: [0]=bytes, [1]=lines
     */
    public static long[] countBytesAndLines(InputStream is) throws IOException {
        byte[] buf = new byte[8192];
        long bytes = 0;
        long lines = 0;
        int bytesRead;
        while ((bytesRead = is.read(buf)) != -1) {
            bytes += bytesRead;
            for (int i = 0; i < bytesRead; i++) {
                if (buf[i] == '\n') {
                    lines++;
                }
            }
        }
        return new long[]{bytes, lines};
    }
}
