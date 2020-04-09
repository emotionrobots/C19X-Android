package org.c19x.network;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.util.Map;

/**
 * Byte array request for obtaining raw binary data from server.
 */
public class ByteArrayRequest extends Request<byte[]> {
    private final Response.Listener<byte[]> listener;
    private final byte[] body;
    private final Map<String, String> params;
    public Map<String, String> responseHeaders;

    public ByteArrayRequest(final int method, final String url, final Response.Listener<byte[]> listener, final Response.ErrorListener errorListener, final Map<String, String> params) {
        this(method, url, null, listener, errorListener, params);
    }

    public ByteArrayRequest(final int method, final String url, final byte[] body, final Response.Listener<byte[]> listener, final Response.ErrorListener errorListener, final Map<String, String> params) {
        super(method, url, errorListener);
        this.body = body;
        this.listener = listener;
        this.params = params;
        setShouldCache(false);
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

    @Override
    public byte[] getBody() throws AuthFailureError {
        return (body == null ? super.getBody() : body);
    }
}