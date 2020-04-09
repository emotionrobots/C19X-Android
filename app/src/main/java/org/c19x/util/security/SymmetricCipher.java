package org.c19x.util.security;

import org.c19x.util.Logger;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SymmetricCipher {
    private final static String tag = SymmetricCipher.class.getName();
    private final static String cipherSpec = "AES/CBC/PKCS5PADDING";
    private final static int IV_LENGTH = getInitialisationVectorSize();
    private final static PRNG prng = new PRNG();

    /**
     * Get initialisation vector (IV) size for cipher spec.
     *
     * @return IV size for cipher spec.
     */
    private final static int getInitialisationVectorSize() {
        try {
            final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(cipherSpec);
            final int size = cipher.getBlockSize();
            return size;
        } catch (Exception e) {
            Logger.error(tag, "Failed to get IV size (cipher={})", cipherSpec, e);
            return 0;
        }
    }

    /**
     * Generate a secret key for encryption and decryption based on given shared secret data.
     *
     * @param bytes
     * @return
     */
    public final static SecretKey createKey(final byte[] bytes) {
        final SecretKey secretKey = new SecretKeySpec(bytes, 0, bytes.length, "AES");
        return secretKey;
    }

    /**
     * Encrypt data using the secret key. The initialisation vector (IV) is generated using secure
     * random and returned as prefix of the cipher text.
     *
     * @param key  Secret key for symmetric encryption
     * @param data Clear data for encryption
     * @return IV + cipher text, or null on failure.
     */
    public final static byte[] encrypt(final SecretKey key, final byte[] data) {
        try {
            // Encrypt the data using the secret key
            final byte[] ivBytes = prng.getBytes(IV_LENGTH);
            final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(cipherSpec);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, new IvParameterSpec(ivBytes));
            final byte[] encryptedData = cipher.doFinal(data);
            // Bundle the IV as prefix to the cipher text
            final byte[] bundle = new byte[IV_LENGTH + encryptedData.length];
            System.arraycopy(ivBytes, 0, bundle, 0, IV_LENGTH);
            System.arraycopy(encryptedData, 0, bundle, IV_LENGTH, encryptedData.length);
            return bundle;
        } catch (Exception e) {
            Logger.warn(tag, "Failed to encrypt (key={},data={})", key, data, e);
            return null;
        }
    }

    /**
     * Decrypt data using the secret key. The initialisation vector (IV) is extracted from the
     * prefix of the cipher text bundle.
     *
     * @param key    Secret key for symmetric encryption
     * @param bundle IV + cipher text
     * @return Clear data
     */
    public final static byte[] decrypt(final SecretKey key, final byte[] bundle) {
        try {
            // Separate the IV and cipher text from the bundle
            final byte[] ivBytes = new byte[IV_LENGTH];
            final byte[] encryptedData = new byte[bundle.length - IV_LENGTH];
            System.arraycopy(bundle, 0, ivBytes, 0, IV_LENGTH);
            System.arraycopy(bundle, IV_LENGTH, encryptedData, 0, encryptedData.length);
            // Decrypt the data using the secret key
            final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(cipherSpec);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, new IvParameterSpec(ivBytes));
            final byte[] data = cipher.doFinal(encryptedData);
            return data;
        } catch (Exception e) {
            Logger.warn(tag, "Failed to decrypt (key={},bundle={})", key, bundle, e);
            return null;
        }
    }

}
