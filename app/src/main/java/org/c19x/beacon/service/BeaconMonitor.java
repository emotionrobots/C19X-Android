package org.c19x.beacon.service;

/**
 * Beacon monitor for gathering updates.
 */
public interface BeaconMonitor {

    /**
     * Start monitor.
     */
    void start();

    /**
     * Stop monitor.
     */
    void stop();

    /**
     * Add listener.
     *
     * @param listener
     */
    void addListener(BeaconListener listener);

    /**
     * Remove listener.
     *
     * @param listener
     */
    void removeListener(BeaconListener listener);

    /**
     * Get beacon monitor status.
     *
     * @return True means beacon service is active, false otherwise.
     */
    boolean isReady();
}