package org.c19x.data.type;

import org.c19x.data.database.ContactEntity;

public class Contact {
    public Time time;
    public RSSI rssi;
    public BeaconCode code;

    public Contact(Time time, RSSI rssi, BeaconCode code) {
        this.time = time;
        this.rssi = rssi;
        this.code = code;
    }

    public Contact(ContactEntity entity) {
        time = new Time(entity.time);
        rssi = new RSSI(entity.rssi);
        code = new BeaconCode(entity.code);
    }

    @Override
    public String toString() {
        return "Contact{" +
                "time=" + time +
                ", rssi=" + rssi +
                ", code=" + code +
                '}';
    }
}
