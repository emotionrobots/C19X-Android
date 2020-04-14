package org.c19x.data.primitive;

/**
 * Light-weight atomic mutable byte object.
 */
public class AtomicByte {
    public byte value = 0;

    public AtomicByte(byte value) {
        this.value = value;
    }

    public synchronized byte get() {
        return value;
    }

    public synchronized void set(byte value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return Byte.toString(value);
    }
}
