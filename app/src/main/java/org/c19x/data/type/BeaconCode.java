package org.c19x.data.type;

public class BeaconCode {
    public long value = 0;

    public BeaconCode(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "BeaconCode{" +
                "value=" + value +
                '}';
    }
}
