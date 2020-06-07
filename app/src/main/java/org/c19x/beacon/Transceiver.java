package org.c19x.beacon;

public interface Transceiver {
    /**
     * Start transmitter and receiver to follow Bluetooth state changes to start and stop advertising and scanning.
     */
    void start(String source);

    /**
     * Stop transmitter and receiver will disable advertising, scanning and terminate all connections.
     */
    void stop(String source);

    void append(ReceiverDelegate delegate);
}
