package org.c19x.beacon.serviceOld;

public interface BeaconListener {

    /**
     * Beacon status.
     *
     * @param ready
     */
    void status(boolean ready);

    /**
     * Own ID update.
     *
     * @param id
     */
    void updateOwnId(long id);

    /**
     * Beacon detected.
     *
     * @param timestamp
     * @param id
     * @param rssi
     */
    void detected(long timestamp, long id, int rssi);
}
