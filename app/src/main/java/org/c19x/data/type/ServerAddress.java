package org.c19x.data.type;

public class ServerAddress {
    public String value;

    public ServerAddress(final String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "ServerAddress{" +
                "value='" + value + '\'' +
                '}';
    }
}
