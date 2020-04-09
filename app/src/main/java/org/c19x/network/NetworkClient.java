package org.c19x.network;

import android.os.Handler;
import android.os.Looper;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.c19x.C19XApplication;
import org.c19x.data.HealthStatus;
import org.c19x.network.response.BooleanResponse;
import org.c19x.network.response.ByteArrayResponse;
import org.c19x.network.response.KeyExchangeResponse;
import org.c19x.network.response.NetworkResponse;
import org.c19x.util.Logger;
import org.c19x.util.security.SymmetricCipher;

import java.math.BigInteger;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

/**
 * Client for interaction with server functions
 */
public class NetworkClient {
    private final static String tag = NetworkClient.class.getName();
    private final static int methodGet = Request.Method.GET;
    private final static int methodPost = Request.Method.POST;

    // URL contexts and parameter keys
    public final static String contextNonce = "n";
    public final static String contextKeyExchange = "k";
    public final static String contextKeyConfirm = "c";
    public final static String keyDeviceIdentifier = "i";

    private final static BigInteger postStatusHashRange = new BigInteger("9");
    private static RequestQueue requestQueue = null;
    private static Handler handler = null;
    private String server = C19XApplication.defaultServer;

    /**
     * Request retry counter.
     */
    private final static class RetryCounter {
        private int remaining = 3;
        private long delay = 2000;
        private float backoff = 2f;

        /**
         * Default retry policy is 3 attempts, randomised delay between 1-2 minutes, backoff of 2x.
         */
        public RetryCounter() {
            this.delay = 60000 + Math.round(Math.random() * 60000);
        }

        /**
         * Request retry counter.
         *
         * @param remaining Remaining attempts.
         * @param delay     Time delay until next attempt, in millis.
         * @param backoff   Backoff multiplier to increase time delay for each attempt.
         */
        public RetryCounter(int remaining, long delay, float backoff) {
            this.remaining = remaining;
            this.delay = delay;
            this.backoff = backoff;
        }

        /**
         * Get remaining attempts.
         *
         * @return
         */
        public int getRemaining() {
            return remaining;
        }

        /**
         * Get time delay until next attempt. Updates remaining attempt counter and increases time
         * delay for next attempt according to backoff multiplier.
         *
         * @return Delay in millis, or -1 if no more attempts.
         */
        public long getDelay() {
            if (remaining <= 0) {
                return -1;
            } else {
                remaining--;
                final long currentDelay = delay;
                delay = Math.round(delay * backoff);
                if (delay < 0) {
                    delay = Long.MAX_VALUE;
                }
                return currentDelay;
            }
        }

        @Override
        public String toString() {
            return "RetryCounter{" +
                    "remaining=" + remaining +
                    ", delay=" + delay +
                    '}';
        }
    }

    public NetworkClient() {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(C19XApplication.getContext());
        }
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
    }

    /**
     * Set server address.
     *
     * @param server
     */
    public void setServer(final String server) {
        this.server = server;
        Logger.debug(tag, "Set server address (address={})", server);
    }


    /**
     * Submit request over network.
     *
     * @param method       GET or POST
     * @param url          Request URL
     * @param body         Request body, or null
     * @param retryCounter Retry counter for defining retry policy
     * @param callback     Callback on completion
     */
    private void request(final int method, final String url, final byte[] body, final RetryCounter retryCounter, final Consumer<ByteArrayResponse> callback) {
        Logger.debug(tag, "Submitting request (method={},url={},body={},attempts={})", (method == methodGet ? "GET" : "POST"), url, body != null, retryCounter.remaining);
        final ByteArrayRequest request = new ByteArrayRequest(method, url, body,
                (com.android.volley.Response.Listener<byte[]>) response -> {
                    Logger.debug(tag, "Request success (method={},url={},response={})", (method == methodGet ? "GET" : "POST"), url, response != null);
                    if (callback != null) {
                        callback.accept(new ByteArrayResponse(NetworkResponse.OK, response));
                    }
                },
                (com.android.volley.Response.ErrorListener) error -> {
                    final long retryDelay = retryCounter.getDelay();
                    if (retryDelay >= 0) {
                        Logger.debug(tag, "Request failed, retrying in {}ms (method={},url={},remainingAttempts={},error={})", retryDelay, (method == methodGet ? "GET" : "POST"), url, retryCounter.getRemaining(), error);
                        handler.postDelayed(() -> request(method, url, body, retryCounter, callback), retryDelay);
                    } else {
                        NetworkResponse networkResponse = NetworkResponse.UNKNOWN_ERROR;
                        if (error instanceof TimeoutError) {
                            networkResponse = NetworkResponse.TIMEOUT_ERROR;
                        } else if (error instanceof NoConnectionError) {
                            networkResponse = NetworkResponse.NO_CONNECTION_ERROR;
                        } else if (error instanceof AuthFailureError) {
                            networkResponse = NetworkResponse.AUTH_FAILURE_ERROR;
                        } else if (error instanceof ServerError) {
                            networkResponse = NetworkResponse.SERVER_ERROR;
                        } else if (error instanceof NetworkError) {
                            networkResponse = NetworkResponse.NETWORK_ERROR;
                        } else if (error instanceof ParseError) {
                            networkResponse = NetworkResponse.PARSE_ERROR;
                        }
                        Logger.debug(tag, "Request failed, no more retries (method={},url={},response={},details={})", (method == methodGet ? "GET" : "POST"), url, networkResponse, error);
                        if (callback != null) {
                            callback.accept(new ByteArrayResponse(networkResponse, null));
                        }
                    }
                }, null);
        requestQueue.add(request);
    }

    // Security functions
    // ============================================================================================

    /**
     * Get randomly generated one-time use nonce from Bob (server) as challenge for authenticating
     * Alice (device) requests, which must include Alice's (device) identifier and encrypted nonce
     * <p>
     * Alice (device) nonce from Bob (server) which is encrypted by Alice (device) then returned
     * to Bob (server) along with Alice's (device) unique identifier for authenticating requests.
     * Bob (server) only keeps the most recently released nonce for Alice (device), thus Alice
     * (device) can only submit one authenticated request at a time..
     * <p>
     * http://server:port/n?i=[aliceIdentifier]
     *
     * @param identifier Alice's (device) globally unique identifier allocated by Bob (server) on registration.
     * @param callback
     */
    public void getNonceFromBob(final long identifier, final Consumer<ByteArrayResponse> callback) {
        final String url = server + "/" + contextNonce + "?" + keyDeviceIdentifier + "=" + Long.toString(identifier);
        request(methodGet, url, null, new RetryCounter(3, 2000, 2), r -> callback.accept(r));
    }

    /**
     * Key exchange between Alice (device) and Bob (server) to establish shared secret and server
     * allocated unique identifier for Alice (device) registration.
     * Alice (device) sends her public key to Bob (server) to initiate registration. Bob (server)
     * responds with a globally unique identifier for Alice (device) and his public key that has
     * been generated for Alice to enable completion of key exchange to create a shared secret
     * between Alice (device) and Bob (server) that is registered against the unique identifier.
     * <p>
     * http://server:port/k
     * requestBody=alicePublicKeyEncoded
     *
     * @param alicePublicKeyEncoded Alice's (device) public key in encoded format.
     * @param callback
     */
    public void postKeyExchangePublicKeyToBob(final byte[] alicePublicKeyEncoded, final Consumer<KeyExchangeResponse> callback) {
        final String url = server + "/" + contextKeyExchange;
        request(methodPost, url, alicePublicKeyEncoded, new RetryCounter(10, 10000, 4), r -> callback.accept(new KeyExchangeResponse(r)));
    }

    /**
     * Confirm Alice (device) and Bob (server) have established a shared secret and Bob (server) has
     * registered the unique identifier for Alice (device) against the shared secret.
     * <p>
     * http://server:port/c?i=[aliceIdentifier]
     * requestBody=encryptedNonce
     *
     * @param identifier      Alice's (device) globally unique identifier allocated by Bob (server) on registration.
     * @param sharedSecretKey Alice's (device) and Bob's (server) shared secret key established by key exchange.
     * @param callback
     */
    public void getConfirmationOfSharedSecretKeyFromBob(final long identifier, final SecretKey sharedSecretKey, final Consumer<BooleanResponse> callback) {
        final String url = server + "/" + contextKeyConfirm + "?" + keyDeviceIdentifier + "=" + identifier;
        authRequestBoolean(methodPost, url, new RetryCounter(3, 2000, 2), identifier, sharedSecretKey, callback);
    }

    /**
     * Submit authenticated request for boolean response to server.
     *
     * @param method
     * @param url
     * @param retryCounter
     * @param identifier
     * @param sharedSecretKey
     * @param callback
     */
    private void authRequestBoolean(final int method, final String url, final RetryCounter retryCounter, final long identifier, final SecretKey sharedSecretKey, final Consumer<BooleanResponse> callback) {
        getNonceFromBob(identifier, nonceResponse -> {
            if (nonceResponse.getNetworkResponse() == NetworkResponse.OK) {
                final byte[] encryptedNonce = SymmetricCipher.encrypt(sharedSecretKey, nonceResponse.getByteArray());
                request(method, url, encryptedNonce, retryCounter, response -> {
                    callback.accept(new BooleanResponse(response));
                });
            } else {
                Logger.warn(tag, "Failed to get nonce from server (response={})", nonceResponse);
                callback.accept(new BooleanResponse(nonceResponse));
            }
        });
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
                new com.android.volley.Response.Listener<String>() {
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
                }, new com.android.volley.Response.ErrorListener() {
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
                new com.android.volley.Response.Listener<byte[]>() {
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
                }, new com.android.volley.Response.ErrorListener() {
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
