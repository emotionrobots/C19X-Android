package org.c19x.beacon;

import org.c19x.data.type.BeaconCode;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface Transmitter {
    /**
     * Delegates for receiving beacon detection events. This is necessary because some Android devices (Samsung J6)
     * does not support BLE transmit, thus making the beacon characteristic writable offers a mechanism for such devices
     * to detect a beacon transmitter and make their own presence known by sending its own beacon code and RSSI as
     * data to the transmitter.
     */
    Queue<ReceiverDelegate> delegates = new ConcurrentLinkedQueue<>();
    UUID beaconServiceUUID = UUID.fromString("0022D481-83FE-1F13-0000-000000000000");

    /**
     * Start transmitter. The actual start is triggered by bluetooth state changes.
     */
    void start(String source);

    /**
     * Stops and resets transmitter.
     */
    void stop(String source);

    /**
     * Change beacon code being broadcasted by adjusting the lower 64-bit of characteristic UUID.
     */
    void updateBeaconCode();

    /**
     * Get current beacon code.
     */
    BeaconCode beaconCode();

    /**
     * Is transmitter supported.
     *
     * @return
     */
    boolean isSupported();
}
