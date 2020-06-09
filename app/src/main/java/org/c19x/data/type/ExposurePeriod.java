package org.c19x.data.type;

public class ExposurePeriod {
    public int value;

    public ExposurePeriod(int value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExposurePeriod that = (ExposurePeriod) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return "ExposurePeriod{" +
                "value=" + value +
                '}';
    }
}
