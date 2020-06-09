package org.c19x.data.type;

public class ContactPattern {
    public String value;

    public ContactPattern(final String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "ContactPattern{" +
                "value='" + value + '\'' +
                '}';
    }
}
