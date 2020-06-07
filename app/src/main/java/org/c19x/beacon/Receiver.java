package org.c19x.beacon;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface Receiver {
    Queue<ReceiverDelegate> delegates = new ConcurrentLinkedQueue<>();

    /**
     * Start receiver. The actual start is triggered by bluetooth state changes.
     */
    void start(String source);

    /**
     * Stop and resets receiver.
     */
    void stop(String source);

    /**
     * Scan for beacons. This is normally called when bluetooth powers on, but also called by
     * background app refresh task in the AppDelegate as backup for keeping the receiver awake.
     */
    void scan(String source);

}
