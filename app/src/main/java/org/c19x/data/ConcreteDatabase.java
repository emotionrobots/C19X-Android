package org.c19x.data;

import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.RSSI;
import org.c19x.data.type.Time;

public class ConcreteDatabase implements Database {
    @Override
    public void insert(Time time, BeaconCode code, RSSI rssi) {

    }

    @Override
    public void remove(Time before) {

    }
}
