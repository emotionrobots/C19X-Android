package org.c19x.beacon;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface Transceiver {
    Queue<ReceiverDelegate> delegates = new ConcurrentLinkedQueue<>();

    /**
     * Start transmitter and receiver to follow Bluetooth state changes to start and stop advertising and scanning.
     */
    void start(String source);

    /**
     * Stop transmitter and receiver will disable advertising, scanning and terminate all connections.
     */
    void stop(String source);
}
