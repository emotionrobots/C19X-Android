package org.c19x.data.type;

public class BeaconCodeSeed {
    public long value = 0;
    public Day day;

    public BeaconCodeSeed(long value, Day day) {
        this.value = value;
        this.day = day;
    }

    @Override
    public String toString() {
        return "BeaconCodeSeed{" +
                "value=" + value +
                ", day=" + day +
                '}';
    }
}
