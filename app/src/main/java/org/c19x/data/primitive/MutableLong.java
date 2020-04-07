package org.c19x.data.primitive;

/**
 * Light-weight mutable long object.
 */
public class MutableLong {
    public long value = 0;

    public MutableLong(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
