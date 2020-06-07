package org.c19x.data.type;

/**
 * Time interval in seconds.
 */
public class TimeInterval {
    public long value = 0;

    public TimeInterval(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "TimeInterval{" +
                "value=" + value +
                '}';
    }
}
