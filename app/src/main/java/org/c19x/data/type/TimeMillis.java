package org.c19x.data.type;

public class TimeMillis {
    public long value = 0;

    public TimeMillis(final long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "TimeMillis{" +
                "value=" + value +
                '}';
    }
}
