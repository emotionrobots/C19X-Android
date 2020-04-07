package org.c19x.network;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.util.Map;

/**
 * Byte array request for downloading global status log updates as raw compressed data.
 */
public class ByteArrayRequest extends Request<byte[]> {
    private final Response.Listener<byte[]> listener;
    private Map<String, String> params;

    //create a static map for directly accessing headers
    public Map<String, String> responseHeaders;

    public ByteArrayRequest(final int method, final String url, final Response.Listener<byte[]> listener, final Response.ErrorListener errorListener, final Map<String, String> params) {
        super(method, url, errorListener);
        setShouldCache(false);
        this.listener = listener;
        this.params = params;
    }

    @Override
    protected Map<String, String> getParams() throws com.android.volley.AuthFailureError {
        return params;
    }

    @Override
    protected void deliverResponse(byte[] response) {
        listener.onResponse(response);
    }

    @Override
    protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
        responseHeaders = response.headers;
        return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
    }
}