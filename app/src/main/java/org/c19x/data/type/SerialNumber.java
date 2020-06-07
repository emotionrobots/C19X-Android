package org.c19x.data.type;

public class SerialNumber {
    public long value = 0;

    public SerialNumber(long value) {
        this.value = value;
    }

    public SerialNumber(String value) {
        this.value = Long.parseLong(value);
    }

    @Override
    public String toString() {
        return "SerialNumber{" +
                "value=" + value +
                '}';
    }
}
