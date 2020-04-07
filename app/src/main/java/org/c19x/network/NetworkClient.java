package org.c19x.network;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.c19x.C19XApplication;
import org.c19x.data.HealthStatus;
import org.c19x.util.Logger;

import java.math.BigInteger;

/**
 * Client for interaction with server functions
 */
public class NetworkClient {
    private final static String tag = NetworkClient.class.getName();
    private final static BigInteger postStatusHashRange = new BigInteger("9");
    private static RequestQueue requestQueue = null;
    private String server = C19XApplication.defaultServer;

    public NetworkClient() {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(C19XApplication.getContext());
        }
    }

    /**
     * Set server address.
     *
     * @param server
     */
    public void setServer(final String server) {
        this.server = server;
    }

    /**
     * Post health status update to server
     *
     * @param id           Device identifier.
     * @param healthStatus Health status.
     * @param callback     Callback for request result.
     */
    public void postStatus(final long id, final byte healthStatus, final NetworkClientCallback callback) {
        final String url = server + "/s?i=" + Long.toString(id) + "&s=" + Short.toString(healthStatus);
        Logger.debug(tag, "Post status (id={},status={})", id, HealthStatus.toString(healthStatus));
        final StringRequest request = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(final String response) {
                        try {
                            if (response != null) {
                                final int expectedHash = (int) C19XApplication.hash(id + ":" + healthStatus, postStatusHashRange);
                                final int actualHash = Integer.parseInt(response);
                                if (expectedHash == actualHash) {
                                    Logger.info(tag, "Post status successful, health status has been shared");
                                    if (callback != null) {
                                        callback.result(true);
                                    }
                                } else {
                                    Logger.warn(tag, "Post status failed, health status has not been shared (response hash mismatch)");
                                    if (callback != null) {
                                        callback.result(false);
                                    }
                                }
                            } else {
                                Logger.warn(tag, "Post status failed, health status has not been shared (no response)");
                                if (callback != null) {
                                    callback.result(false);
                                }
                            }
                        } catch (Throwable e) {
                            Logger.warn(tag, "Post status failed (exception)", e);
                            if (callback != null) {
                                callback.result(false);
                            }
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(final VolleyError error) {
                if (error instanceof NoConnectionError) {
                    Logger.warn(tag, "Post status failed, cannot connect to server (address={})", server);
                } else if (error instanceof TimeoutError) {
                    Logger.warn(tag, "Post status failed, connection timeout (address={})", server);
                } else {
                    Logger.warn(tag, "Post status failed, received error response (error={})", error);
                }
                if (callback != null) {
                    callback.result(false);
                }
            }
        });
        request.setShouldCache(false);
        request.setRetryPolicy(new DefaultRetryPolicy(20000, 1, 1f));
        request.setTag(tag);
        requestQueue.add(request);
    }

    /**
     * Get global status log from server.
     *
     * @param timestamp Current log version.
     * @param callback  Callback for request result.
     */
    public void getUpdate(final long timestamp, final NetworkClientCallback callback) {
        final String url = server + "/u?t=" + Long.toString(timestamp);
        final ByteArrayRequest request = new ByteArrayRequest(Request.Method.GET, url,
                new Response.Listener<byte[]>() {
                    @Override
                    public void onResponse(final byte[] response) {
                        try {
                            if (response != null) {
                                if (response.length == 0) {
                                    Logger.info(tag, "Get update was unnecessary, already at latest version (current={})", timestamp);
                                    if (callback != null) {
                                        callback.result(true);
                                    }
                                } else if (C19XApplication.getGlobalStatusLog().setUpdate(response)) {
                                    C19XApplication.updateAllParameters();
                                    Logger.info(tag, "Get update successful, global status log has been updated (current={},new={})", timestamp, C19XApplication.getGlobalStatusLog().getTimestamp());
                                    if (callback != null) {
                                        callback.result(true);
                                    }
                                } else {
                                    Logger.warn(tag, "Get update failed, global status log was not updated (current={})", timestamp);
                                    if (callback != null) {
                                        callback.result(false);
                                    }
                                }
                            } else {
                                Logger.warn(tag, "Get update failed, global status log was not updated (no response)");
                                if (callback != null) {
                                    callback.result(false);
                                }
                            }
                        } catch (Throwable e) {
                            Logger.warn(tag, "Get update failed (exception)", e);
                            if (callback != null) {
                                callback.result(false);
                            }
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(final VolleyError error) {
                if (error instanceof NoConnectionError) {
                    Logger.warn(tag, "Get update failed, cannot connect to server (address={})", server);
                } else if (error instanceof TimeoutError) {
                    Logger.warn(tag, "Get update failed, connection timeout (address={})", server);
                } else {
                    Logger.warn(tag, "Get update failed, received error response (error={})", error);
                }
                if (callback != null) {
                    callback.result(false);
                }
            }
        }, null);
        requestQueue.add(request);
    }
}
