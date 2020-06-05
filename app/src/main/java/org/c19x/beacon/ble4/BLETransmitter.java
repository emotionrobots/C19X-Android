package org.c19x.beacon.ble4;

import org.c19x.beacon.BeaconListener;
import org.c19x.beacon.BeaconTransmitter;
import org.c19x.util.messaging.DefaultBroadcaster;

public class BLETransmitter extends DefaultBroadcaster<BeaconListener> implements BeaconTransmitter {
    public final static long bleServiceId = 9803801938501395l;

    @Override
    public void start(long id) {

    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isStarted() {
        return false;
    }

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public long getId() {
        return 0;
    }

    @Override
    public void setId(long id) {

    }
}
