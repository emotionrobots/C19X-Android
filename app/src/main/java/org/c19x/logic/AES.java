package org.c19x.logic;

import android.util.Base64;

import org.c19x.util.Logger;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
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

    public final static String encrypt(final SecretKey key, final String value) {
        return encrypt(key.getEncoded(), value);
    }

    public final static String encrypt(final byte[] key, final String value) {
        assert (key.length == 256);
        try {
            final byte[] iv = new byte[16];
            getSecureRandom().nextBytes(iv);

            final IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            final SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");

            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            final byte[] encrypted = cipher.doFinal(value.getBytes());
            final String bundle = Base64.encodeToString(iv, Base64.DEFAULT)
                    + "," + Base64.encodeToString(encrypted, Base64.DEFAULT);
            return bundle;
        } catch (Throwable e) {
            Logger.error(tag, "Failed to encrypt", e);
            return null;
        }
    }

    public final static String decrypt(final SecretKey secretKey, final String bundle) {
        return decrypt(secretKey.getEncoded(), bundle);
    }

    public final static String decrypt(final byte[] key, final String bundle) {
        try {
            final String ivString = bundle.substring(0, bundle.indexOf(','));
            final String cryptString = bundle.substring(ivString.length() + 1);

            final IvParameterSpec ivParameterSpec = new IvParameterSpec(Base64.decode(ivString, Base64.DEFAULT));
            final SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");

            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            final String clearText = new String(cipher.doFinal(Base64.decode(cryptString, Base64.DEFAULT)));
            return clearText;
        } catch (Throwable e) {
            Logger.error(tag, "Failed to decrypt", e);
            return null;
        }
    }

}
