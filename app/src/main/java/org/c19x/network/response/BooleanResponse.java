package org.c19x.network.response;

/**
 * Generic boolean response.
 */
public class BooleanResponse extends Response {
    private final static byte[] responseTrue = new byte[]{1};
    private final static byte[] responseFalse = new byte[]{0};
    private final boolean value;

    /**
     * Decode boolean response from raw byte array response.
     *
     * @param byteArrayResponse
     */
    public BooleanResponse(final ByteArrayResponse byteArrayResponse) {
        super(byteArrayResponse.getNetworkResponse());
        if (getNetworkResponse() == NetworkResponse.OK) {
            final byte[] byteArray = byteArrayResponse.getByteArray();
            this.value = (byteArray != null && byteArray.length == 1 && byteArray[0] == 1);
        } else {
            this.value = false;
        }
    }

    /**
     * Encode response on server side
     *
     * @return
     */
    public final static byte[] encode(final boolean value) {
        return (value ? responseTrue : responseFalse);
    }

    /**
     * Get boolean value.
     *
     * @return
     */
    public boolean getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "BooleanResponse{" + "value=" + value + ", networkResponse=" + networkResponse + '}';
    }
}
