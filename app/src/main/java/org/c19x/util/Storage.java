package org.c19x.util;

import android.content.Context;

import org.c19x.C19XApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Storage {
    private final static String tag = Storage.class.getName();
    private final ConcurrentHashMap<String, Lock> fileLocks = new ConcurrentHashMap<>();

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
     * Write string to file atomically.
     *
     * @param string
     * @param filename
     * @return True on success
     */
    public final boolean atomicWriteText(final String string, final String filename) {
        boolean result = false;
        lock(filename);
        try {
            final byte[] bytes = string.getBytes();
            final String newFilename = filename + ".new";
            final FileOutputStream fileOutputStream = C19XApplication.getContext().openFileOutput(filename, Context.MODE_PRIVATE);
            fileOutputStream.write(bytes);
            fileOutputStream.flush();
            fileOutputStream.close();
            // If write was successful, replace log.csv with log.new
            final File newFile = C19XApplication.getContext().getFileStreamPath(newFilename);
            final File file = C19XApplication.getContext().getFileStreamPath(filename);
            result = newFile.renameTo(file);
        } catch (Throwable e) {
            Logger.warn(tag, "Atomic write failed (filename={})", filename, e);
        } finally {
            unlock(filename);
        }
        return result;
    }

    /**
     * Read lines from file atomically.
     *
     * @param filename
     * @param consumer Consumer for processing each line of file content.
     * @return True on success
     */
    public final boolean atomicReadText(final String filename, final Consumer<String> consumer) {
        boolean result = false;
        lock(filename);
        try {
            final File file = C19XApplication.getContext().getFileStreamPath(filename);
            if (!file.exists()) {
                return true;
            }
            final FileInputStream fileInputStream = C19XApplication.getContext().openFileInput(filename);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));
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
            fileInputStream.close();
        } catch (Throwable e) {
            Logger.warn(tag, "Atomic read failed (filename={})", filename, e);
        } finally {
            unlock(filename);
        }
        return result;
    }

}
