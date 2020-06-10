package org.c19x.logic;

import android.util.Base64;

import org.c19x.data.Logger;
import org.c19x.data.type.SharedSecret;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AES {
    private final static String tag = AES.class.getName();
    public final static SecureRandom secureRandom = getSecureRandom();

    /**
     * Get a securely seeded secure random number generator. The implementation uses
     * an instance of the SHA1 PRNG which is securely seeded using a separate
     * instance of secure random.
     *
     * @return
     */
    private final static SecureRandom getSecureRandom() {
        try {
            // Get an instance of the SUN SHA1 PRNG
            final SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            // Securely seed
            final SecureRandom randomForSeeding = new SecureRandom();
            // NIST SP800-90A suggests 440 bits for SHA1 seed
            final byte[] seed = randomForSeeding.generateSeed(55);
            secureRandom.setSeed(seed);
            // Securely start
            secureRandom.nextBytes(new byte[256 + secureRandom.nextInt(1024)]);
            return secureRandom;
        } catch (Exception e) {
            Logger.error(tag, "Failed to initialise pseudo random number generator", e);
            return null;
        }
    }

    public final static String encrypt(final SharedSecret sharedSecret, final String value) {
        try {
            final byte[] iv = new byte[16];
            getSecureRandom().nextBytes(iv);

            final IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            final SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret.value, "AES");

            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            final byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            final String bundle = base64Encode(iv) + "," + base64Encode(encrypted);
            return bundle;
        } catch (Throwable e) {
            Logger.error(tag, "Failed to encrypt", e);
            return null;
        }
    }

    public final static String decrypt(final SharedSecret sharedSecret, final String bundle) {
        try {
            final String ivString = bundle.substring(0, bundle.indexOf(','));
            final String cryptString = bundle.substring(ivString.length() + 1);

            final IvParameterSpec ivParameterSpec = new IvParameterSpec(base64Decode(ivString));
            final SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret.value, "AES");

            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            final String clearText = new String(cipher.doFinal(base64Decode(cryptString)), StandardCharsets.UTF_8);
            return clearText;
        } catch (Throwable e) {
            Logger.error(tag, "Failed to decrypt", e);
            return null;
        }
    }

    private final static String base64Encode(final byte[] data) {
        return Base64.encodeToString(data, Base64.DEFAULT + Base64.NO_WRAP + Base64.URL_SAFE);
    }

    private final static byte[] base64Decode(final String value) {
        return Base64.decode(value, Base64.DEFAULT + Base64.NO_WRAP + Base64.URL_SAFE);
    }
}
