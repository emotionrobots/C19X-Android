package org.c19x.beacon;

import org.c19x.util.messaging.Broadcaster;

/**
 * Beacon receiver.
 */
public interface BeaconReceiver extends Broadcaster<BeaconListener> {

    /**
     * Start receiver for device identifers.
     */
    void start();

    /**
     * Stop receiver.
     */
    void stop();

    /**
     * Get receiver state.
     *
     * @return True means receiver has been started and is active.
     */
    boolean isStarted();

    /**
     * Set duty cycle of receiver, on for how long, and off for how long.
     *
     * @param onDuration  On duration in millis.
     * @param offDuration Off duration in millis.
     */
    void setDutyCycle(final int onDuration, final int offDuration);
}
