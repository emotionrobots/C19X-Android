package org.c19x.network.response;

import java.util.Arrays;

/**
 * Generic byte array response.
 */
public class ByteArrayResponse extends Response {
    private final byte[] byteArray;

    public ByteArrayResponse(final NetworkResponse networkResponse, final byte[] byteArray) {
        super(networkResponse);
        this.byteArray = byteArray;
    }

    /**
     * Get byte array.
     *
     * @return
     */
    public byte[] getByteArray() {
        return byteArray;
    }

    @Override
    public String toString() {
        return "ByteArrayResponse{" +
                "byteArray=" + Arrays.toString(byteArray) +
                ", networkResponse=" + networkResponse +
                '}';
    }
}
