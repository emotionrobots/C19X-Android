package org.c19x.beacon.service;

/**
 * Beacon service
 */
public interface BeaconService {
    /**
     * Start beacon advertising and scanning service.
     *
     * @param ownId Own ID to broadcast to receivers.
     */
    void startService(long ownId);

    /**
     * Stop beacon service.
     */
    void stopService();
}