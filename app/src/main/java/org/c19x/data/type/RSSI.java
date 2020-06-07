package org.c19x.data.type;

public class RSSI {
    public int value = 0;

    public RSSI(int value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "RSSI{" +
                "value=" + value +
                '}';
    }
}
