package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.exception.ResponseSizeLimitExceededException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An InputStream wrapper that enforces a maximum byte limit.
 * If the number of read bytes exceeds the configured limit,
 * a ResponseSizeLimitExceededException is thrown.
 */
public class BoundedInputStream extends FilterInputStream {

    private final long maxBytes;
    private long bytesRead = 0;

    public BoundedInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            bytesRead++;
            checkLimit();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            bytesRead += n;
            checkLimit();
        }
        return n;
    }

    public long getBytesRead() {
        return bytesRead;
    }

    private void checkLimit() {
        if (bytesRead > maxBytes) {
            throw new ResponseSizeLimitExceededException(
                    "Response size exceeded configured maximum limit of " + maxBytes + " bytes (read: " + bytesRead + " bytes)"
            );
        }
    }
}
