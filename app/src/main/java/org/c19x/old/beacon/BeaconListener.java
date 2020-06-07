package org.c19x.old.beacon;

/**
 * Beacon listener for responding to beacon transmitter and receiver events.
 */
public class BeaconListener {

    public void start() {
    }

    public void error(int errorCode) {
    }

    public void stop() {
    }

    public void detect(final long timestamp, long id, float rssi) {
    }
}
