package org.c19x.network.response;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Key exchange response from Bob.
 */
public class KeyExchangeResponse extends Response {
    private final long identifier;
    private final byte[] bobPublicKeyEncoded;

    /**
     * Decode key exchange response from raw byte array response.
     *
     * @param byteArrayResponse
     */
    public KeyExchangeResponse(final ByteArrayResponse byteArrayResponse) {
        super(byteArrayResponse.getNetworkResponse());
        if (getNetworkResponse() == NetworkResponse.OK) {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(byteArrayResponse.getByteArray());
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            identifier = byteBuffer.getLong(0);
            bobPublicKeyEncoded = new byte[byteBuffer.capacity() - Long.BYTES];
            System.arraycopy(byteBuffer.array(), Long.BYTES, bobPublicKeyEncoded, 0, bobPublicKeyEncoded.length);
        } else {
            identifier = -1;
            bobPublicKeyEncoded = null;
        }
    }

    /**
     * Encode response on serve side
     *
     * @param identifier
     * @param bobPublicKeyEncoded
     * @return
     */
    public final static byte[] encode(final long identifier, final byte[] bobPublicKeyEncoded) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES + bobPublicKeyEncoded.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putLong(0, identifier);
        System.arraycopy(bobPublicKeyEncoded, 0, byteBuffer.array(), Long.BYTES, bobPublicKeyEncoded.length);
        return byteBuffer.array();
    }

    /**
     * Get globally unique identifier allocated by Bob, this is the public alias for this device.
     *
     * @return
     */
    public long getIdentifier() {
        return identifier;
    }

    /**
     * Get Bob's public key in encoded format.
     *
     * @return
     */
    public byte[] getBobPublicKeyEncoded() {
        return bobPublicKeyEncoded;
    }

    @Override
    public String toString() {
        return "KeyExchangeResponse{" +
                "identifier=" + identifier +
                ", bobPublicKeyEncoded=" + Arrays.toString(bobPublicKeyEncoded) +
                ", networkResponse=" + networkResponse +
                '}';
    }
}
