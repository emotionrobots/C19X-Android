package org.c19x.data.type;

public class Contact {
    public Time time;
    public RSSI rssi;
    public BeaconCode code;

    public Contact(Time time, RSSI rssi, BeaconCode code) {
        this.time = time;
        this.rssi = rssi;
        this.code = code;
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
