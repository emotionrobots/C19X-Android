package org.c19x.data.type;

import java.util.Objects;

public class BeaconCode {
    public long value = 0;

    public BeaconCode(long value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BeaconCode that = (BeaconCode) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "BeaconCode{" +
                "value=" + value +
                '}';
    }
}
