package org.c19x.data.type;

public class DayCode {
    public long value = 0;

    public DayCode(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "DayCode{" +
                "value=" + value +
                '}';
    }
}
