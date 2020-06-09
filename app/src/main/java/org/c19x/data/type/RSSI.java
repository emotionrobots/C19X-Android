package org.c19x.data.type;

public class RSSI {
    public int value = 0;

    public RSSI(int value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RSSI rssi = (RSSI) o;
        return value == rssi.value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return "RSSI{" +
                "value=" + value +
                '}';
    }
}
