package org.c19x.data;

import org.c19x.C19XApplication;
import org.c19x.R;
import org.c19x.network.NetworkClient;
import org.c19x.network.response.NetworkResponse;
import org.c19x.util.Logger;
import org.c19x.util.messaging.DefaultBroadcaster;
import org.c19x.util.security.AliasIdentifier;
import org.c19x.util.security.KeyExchange;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class DeviceRegistration extends DefaultBroadcaster<DeviceRegistrationListener> {
    private final static String tag = DeviceRegistration.class.getName();

    private long identifier = -1;
    private byte[] sharedSecret = null;
    private SecretKey sharedSecretKey = null;
    private AliasIdentifier aliasIdentifier = null;

    public DeviceRegistration() {
        load();
    }

    /**
     * Load registration data from encrypted file on app internal storage
     */
    private boolean load() {
        // Read from encrypted file
        final String filename = C19XApplication.getContext().getString(R.string.file_registration);
        return C19XApplication.getStorage().atomicRead(filename, byteArray -> {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            identifier = byteBuffer.getLong(0);
            sharedSecret = new byte[byteArray.length - Long.BYTES];
            System.arraycopy(byteBuffer.array(), Long.BYTES, sharedSecret, 0, sharedSecret.length);
            sharedSecretKey = new SecretKeySpec(sharedSecret, 0, 16, "AES");
            aliasIdentifier = new AliasIdentifier(sharedSecret);
            Logger.info(tag, "Loaded device registration data from encrypted storage");
        });
    }

    /**
     * Write registration data to encrypted file on app internal storage,
     *
     * @return
     */
    private boolean write() {
        // Encode registration data
        final ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES + sharedSecret.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putLong(0, identifier);
        byteBuffer.position(Long.BYTES);
        byteBuffer.put(sharedSecret);

        // Write to encrypted file
        final String filename = C19XApplication.getContext().getString(R.string.file_registration);
        final boolean success = C19XApplication.getStorage().atomicWrite(byteBuffer.array(), filename);
        if (success) {
            Logger.info(tag, "Wrote device registration data to encrypted storage");
        } else {
            Logger.warn(tag, "Failed to write device registration data to encrypted storage");
        }
        return success;
    }

    /**
     * Device registration status.
     *
     * @return True if device has been registered, false otherwise.
     */
    public boolean isRegistered() {
        return sharedSecret != null && sharedSecretKey != null && aliasIdentifier != null;
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
     * Get device alias identifier for broadcasting in the clear over Bluetooth beacon.
     *
     * @return
     */
    public AliasIdentifier getAliasIdentifier() {
        return aliasIdentifier;
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
                                aliasIdentifier = new AliasIdentifier(sharedSecret);
                                write();
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
