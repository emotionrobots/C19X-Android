package org.c19x.old.data;

/**
 * Listener for health status updates.
 */
public interface HealthStatusListener {

    /**
     * Health status has changed.
     *
     * @param fromStatus From status.
     * @param toStatus   To status.
     */
    void update(final byte fromStatus, final byte toStatus);
}
