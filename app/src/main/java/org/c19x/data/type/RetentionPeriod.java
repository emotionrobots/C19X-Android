package org.c19x.data.type;

public class RetentionPeriod extends TimeInterval {

    public RetentionPeriod(long value) {
        super(value);
    }

    @Override
    public String toString() {
        return "RetentionPeriod{" +
                "value=" + value +
                '}';
    }
}
