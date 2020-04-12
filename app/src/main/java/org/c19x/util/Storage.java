package org.c19x.util;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.c19x.C19XApplication;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class Storage {
    private final static String tag = Storage.class.getName();
    private final ConcurrentHashMap<String, Lock> fileLocks = new ConcurrentHashMap<>();
    private final Context context = C19XApplication.getContext();

    /**
     * Lock file before read/write.
     *
     * @param filename
     */
    private void lock(final String filename) {
        fileLocks.computeIfAbsent(filename, f -> new ReentrantLock()).lock();
    }

    /**
     * Unlock file after read/write.
     *
     * @param filename
     */
    private void unlock(final String filename) {
        fileLocks.computeIfPresent(filename, (f, lock) -> {
            lock.unlock();
            return lock;
        });
    }

    /**
     * Get master key for encrypted app data files.
     *
     * @return
     */
    private final synchronized SecretKey getMasterKey() {
        try {
            final String alias = "c19xMasterKey";
            final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (!keyStore.containsAlias(alias)) {
                Logger.info(tag, "Generating master key");
                final KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                final KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(alias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build();
                keyGenerator.init(keyGenParameterSpec);
                final SecretKey masterKey = keyGenerator.generateKey();
                return masterKey;
            } else {
                Logger.info(tag, "Getting existing master key");
                final SecretKey masterKey = ((KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null)).getSecretKey();
                return masterKey;
            }
        } catch (Throwable e) {
            Logger.error(tag, "Failed to get or create master key", e);
            return null;
        }
    }

    /**
     * Write data to encrypted file atomically.
     *
     * @param bytes
     * @param filename
     * @return True on success
     */
    public final boolean atomicWrite(final byte[] bytes, final String filename) {
        boolean result = false;
        lock(filename);
        try {
            final String ivFilename = filename + ".iv";
            final String newIvFilename = filename + ".new.iv";
            final String newFilename = filename + ".new";
            // Initialise cipher
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getMasterKey());
            final byte[] iv = cipher.getIV();
            write(iv, newIvFilename);
            // Write new file
            final FileOutputStream fileOutputStream = context.openFileOutput(newFilename, Context.MODE_PRIVATE);
            final CipherOutputStream cipherOutputStream = new CipherOutputStream(fileOutputStream, cipher);
            cipherOutputStream.write(bytes);
            cipherOutputStream.flush();
            fileOutputStream.flush();
            cipherOutputStream.close();
            fileOutputStream.close();
            // If write was successful, replace existing files with new files
            result = context.getFileStreamPath(newFilename).renameTo(context.getFileStreamPath(filename));
            result = result && context.getFileStreamPath(newIvFilename).renameTo(context.getFileStreamPath(ivFilename));
        } catch (Throwable e) {
            Logger.warn(tag, "Atomic write failed (filename={})", filename, e);
        } finally {
            unlock(filename);
        }
        return result;
    }

    private final void write(final byte[] bytes, final String filename) throws Exception {
        final FileOutputStream ivFileOutputStream = C19XApplication.getContext().openFileOutput(filename, Context.MODE_PRIVATE);
        ivFileOutputStream.write(bytes);
        ivFileOutputStream.flush();
        ivFileOutputStream.close();
    }

    private final byte[] read(final String filename) throws Exception {
        final FileInputStream ivFileInputStream = C19XApplication.getContext().openFileInput(filename + ".iv");
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int bytesRead;
        final byte[] buffer = new byte[1024];
        while ((bytesRead = ivFileInputStream.read(buffer, 0, buffer.length)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        byteArrayOutputStream.flush();
        final byte[] iv = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream.close();
        ivFileInputStream.close();
        return iv;
    }

    /**
     * Read bytes from file atomically.
     *
     * @param filename
     * @param consumer Consumer for processing the file binary content.
     * @return True on success
     */
    public final boolean atomicRead(final String filename, final Consumer<byte[]> consumer) {
        boolean result = false;
        lock(filename);
        try {
            final File file = C19XApplication.getContext().getFileStreamPath(filename);
            final File ivFile = C19XApplication.getContext().getFileStreamPath(filename + ".iv");
            if (!file.exists()) {
                return true;
            }
            // Read IV to enable decryption
            final byte[] iv = read(filename);
            // Decrypt file
            final FileInputStream fileInputStream = C19XApplication.getContext().openFileInput(filename);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), gcmParameterSpec);
            final CipherInputStream cipherInputStream = new CipherInputStream(fileInputStream, cipher);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int bytesRead;
            final byte[] buffer = new byte[1024];
            while ((bytesRead = cipherInputStream.read(buffer, 0, buffer.length)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            byteArrayOutputStream.flush();
            final byte[] byteArray = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();
            cipherInputStream.close();
            fileInputStream.close();
            consumer.accept(byteArray);
        } catch (Throwable e) {
            Logger.warn(tag, "Atomic read failed (filename={})", filename, e);
        } finally {
            unlock(filename);
        }
        return result;
    }

    /**
     * Write text to encrypted file atomically.
     *
     * @param text
     * @param filename
     * @return True on success
     */
    public final boolean atomicWriteText(final String text, final String filename) {
        return atomicWrite(text.getBytes(), filename);
    }

    /**
     * Read lines from file atomically.
     *
     * @param filename
     * @param consumer Consumer for processing each line of file content.
     * @return True on success
     */
    public final boolean atomicReadText(final String filename, final Consumer<String> consumer) {
        return atomicRead(filename, byteArray -> {
            try {
                final BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(byteArray)));
                String line = reader.readLine();
                while (line != null) {
                    try {
                        consumer.accept(line);
                    } catch (Throwable e) {
                        Logger.warn(tag, "Consumer failed to process line (line={})", line, e);
                    }
                    line = reader.readLine();
                }
                reader.close();
            } catch (Throwable e) {
                Logger.warn(tag, "Atomic read text failed (filename={})", filename, e);
            }
        });
    }

}
