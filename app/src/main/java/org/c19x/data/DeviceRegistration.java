package org.c19x.data;

import org.c19x.C19XApplication;
import org.c19x.network.NetworkClient;
import org.c19x.network.response.NetworkResponse;
import org.c19x.util.Logger;
import org.c19x.util.messaging.DefaultBroadcaster;
import org.c19x.util.security.KeyExchange;

import javax.crypto.SecretKey;

public class DeviceRegistration extends DefaultBroadcaster<DeviceRegistrationListener> {
    private final static String tag = DeviceRegistration.class.getName();

    private long identifier = -1;
    private byte[] sharedSecret = null;
    private SecretKey sharedSecretKey = null;

    public DeviceRegistration() {
    }

    /**
     * Device registration status.
     *
     * @return True if device has been registered, false otherwise.
     */
    public boolean isRegistered() {
        return sharedSecret != null && sharedSecretKey != null;
    }

    /**
     * Get server allocated globally unique device identifier.
     *
     * @return
     */
    public long getIdentifier() {
        return identifier;
    }

    /**
     * Get shared secret between this device and the server, established during registration.
     *
     * @return
     */
    public byte[] getSharedSecret() {
        return sharedSecret;
    }

    /**
     * Get shared secret key between this device and the server, established during registration.
     *
     * @return
     */
    public SecretKey getSharedSecretKey() {
        return sharedSecretKey;
    }

    /**
     * Register device with server to obtain a server allocated globally unique device identifier
     * and establish shared secret between device and server for cryptographic operations.
     */
    public void register() {
        Logger.info(tag, "Device registration");
        try {
            Logger.info(tag, "Generating key");
            final KeyExchange keyExchange = new KeyExchange();
            Logger.info(tag, "Connecting to server");
            final NetworkClient networkClient = C19XApplication.getNetworkClient();
            networkClient.postPublicKeyToBob(keyExchange.getAlicePublicKey(), keyExchangeResponse -> {
                if (keyExchangeResponse.getNetworkResponse() == NetworkResponse.OK) {
                    Logger.debug(tag, "Key exchange successful (identifier={})", keyExchangeResponse.getIdentifier());
                    try {
                        keyExchange.acceptBobPublicKey(keyExchangeResponse.getBobPublicKeyEncoded());
                        Logger.debug(tag, "Key agreement successful (identifier={})", keyExchangeResponse.getIdentifier());
                        networkClient.confirmKeyAgreementWithBob(keyExchangeResponse.getIdentifier(), keyExchange.getSharedSecretKey(), confirmationResponse -> {
                            if (confirmationResponse.getNetworkResponse() == NetworkResponse.OK) {
                                identifier = keyExchangeResponse.getIdentifier();
                                sharedSecret = keyExchange.getSharedSecret();
                                sharedSecretKey = keyExchange.getSharedSecretKey();
                                broadcast(l -> l.registration(true, identifier));
                                Logger.info(tag, "Device registration success (identifier={})", identifier);
                            } else {
                                broadcast(l -> l.registration(false, identifier));
                                Logger.warn(tag, "Key confirmation failed, network error (error={})", confirmationResponse.getNetworkResponse());
                            }
                        });
                    } catch (Throwable e) {
                        broadcast(l -> l.registration(false, identifier));
                        Logger.warn(tag, "Key exchange failed, exception", e);
                    }
                } else {
                    broadcast(l -> l.registration(false, identifier));
                    Logger.warn(tag, "Key exchange failed, network error (error={})", keyExchangeResponse.getNetworkResponse());
                }
            });
        } catch (Throwable e) {
            Logger.warn(tag, "Device registration failed", e);
        }
    }
}
