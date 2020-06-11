package org.c19x.logic;

import org.c19x.data.Settings;
import org.c19x.data.type.Status;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface Controller {
    /// Delegates for receiving application events.
    Queue<ControllerDelegate> delegates = new ConcurrentLinkedQueue<>();

    Settings settings();

    /**
     * Reset all application data.
     */
    void reset();

    /**
     * Notify controller that app has entered foreground mode.
     */
    void foreground();

    /**
     * Notify controller that app has entered background mode.
     */
    void background();

    /**
     * Synchronise device data with server data.
     */
    void synchronise(boolean immediately);

    /**
     * Set health status, locally and remotely.
     */
    void status(Status setTo);

    /**
     * Export contacts.
     */
    void export();
}
