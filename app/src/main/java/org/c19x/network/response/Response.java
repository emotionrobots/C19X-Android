package org.c19x.network.response;

/**
 * Generic request response.
 */
public class Response {
    protected final NetworkResponse networkResponse;

    public Response(final NetworkResponse networkResponse) {
        this.networkResponse = networkResponse;
    }

    /**
     * Get network response.
     *
     * @return
     */
    public NetworkResponse getNetworkResponse() {
        return networkResponse;
    }

    @Override
    public String toString() {
        return "Response{" +
                "networkResponse=" + networkResponse +
                '}';
    }
}
