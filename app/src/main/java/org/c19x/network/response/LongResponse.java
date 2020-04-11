package org.c19x.network.response;

/**
 * Generic long response.
 */
public class LongResponse extends Response {
    private final long value;

    /**
     * Decode boolean response from raw byte array response.
     *
     * @param byteArrayResponse
     */
    public LongResponse(final ByteArrayResponse byteArrayResponse) {
        super(byteArrayResponse.getNetworkResponse());
        if (getNetworkResponse() == NetworkResponse.OK) {
            final byte[] byteArray = byteArrayResponse.getByteArray();
            this.value = (byteArray != null && byteArray.length == 8 ? bytesToLong(byteArray) : 0);
        } else {
            this.value = 0;
        }
    }

    /**
     * Encode response on server side
     *
     * @return
     */
    public final static byte[] encode(final long value) {
        return longToBytes(value);
    }


    private final static byte[] longToBytes(final long x) {
        final byte[] array = new byte[8];
        array[0] = (byte) x;
        array[1] = (byte) (x >> 8);
        array[2] = (byte) (x >> 16);
        array[3] = (byte) (x >> 24);
        array[4] = (byte) (x >> 32);
        array[5] = (byte) (x >> 40);
        array[6] = (byte) (x >> 48);
        array[7] = (byte) (x >> 56);
        return array;
    }

    private final static long bytesToLong(final byte[] array) {
        return ((long) array[0] & 0xFF) | (((long) array[1] & 0xFF) << 8) | (((long) array[2] & 0xFF) << 16)
                | (((long) array[3] & 0xFF) << 24) | (((long) array[4] & 0xFF) << 32) | (((long) array[5] & 0xFF) << 40)
                | (((long) array[6] & 0xFF) << 48) | (((long) array[7] & 0xFF) << 56);
    }

    /**
     * Get long value.
     *
     * @return
     */
    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "LongResponse{" + "value=" + value + ", networkResponse=" + networkResponse + '}';
    }
}
