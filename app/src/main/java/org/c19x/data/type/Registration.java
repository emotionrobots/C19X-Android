package org.c19x.data.type;

public class Registration {
    public SerialNumber serialNumber;
    public SharedSecret sharedSecret;

    public Registration(SerialNumber serialNumber, SharedSecret sharedSecret) {
        this.serialNumber = serialNumber;
        this.sharedSecret = sharedSecret;
    }

    @Override
    public String toString() {
        return "Registration{" +
                "serialNumber=" + serialNumber +
                ", sharedSecret=" + sharedSecret +
                '}';
    }
}
