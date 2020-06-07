package org.c19x.old.data;

/**
 * Device registration listener.
 */
public interface DeviceRegistrationListener {

    /**
     * Registration result.
     *
     * @param success    True if registration was successful.
     * @param identifier Server allocated device identifier, or -1 if unsuccessful.
     */
    void registration(boolean success, long identifier);
}
