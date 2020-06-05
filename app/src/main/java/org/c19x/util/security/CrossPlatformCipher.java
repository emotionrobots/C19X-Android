package org.c19x.util.security;

import org.c19x.util.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CrossPlatformCipher {
    private final static String tag = CrossPlatformCipher.class.getName();

    public byte[] aesEncrypt(final byte[] key, final byte[] data) {
        try {
            final IvParameterSpec ivspec = new IvParameterSpec(key);
            final SecretKeySpec keyspec = new SecretKeySpec(key, "AES");
            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keyspec, ivspec);
            final byte[] encrypted = cipher.doFinal(data);
            return encrypted;
        } catch (Exception e) {
            Logger.warn(tag, "Encryption failed", e);
        }
        return null;
    }
}
