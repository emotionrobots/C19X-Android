package org.c19x.beacon;

import org.c19x.util.messaging.Broadcaster;

/**
 * Beacon transmitter.
 */
public interface BeaconTransmitter extends Broadcaster<BeaconListener> {

    /**
     * Start transmitter for broadcasting a device identifer.
     *
     * @param id Device identifier.
     */
    void start(long id);

    /**
     * Stop transmitter.
     */
    void stop();

    /**
     * Get transmitter state.
     *
     * @return True means transmitter has been started and is active.
     */
    boolean isStarted();

    /**
     * Check if capability is supported.
     *
     * @return
     */
    boolean isSupported();

    /**
     * Get identifier being broadcasted by transmitter.
     *
     * @return
     */
    long getId();

    /**
     * Set identifier being broadcasted by transmitter.
     *
     * @param
     */
    void setId(final long id);
}
