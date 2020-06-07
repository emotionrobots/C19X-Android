package org.c19x.beacon;

import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.BluetoothState;
import org.c19x.data.type.RSSI;

/**
 * Beacon receiver delegate listens for beacon detection events (beacon code, rssi).
 */
public interface ReceiverDelegate {
    /**
     * Beacon code has been detected.
     */
    void receiver(BeaconCode didDetect, RSSI rssi);

    /**
     * Receiver did update state.
     */
    void receiver(BluetoothState didUpdateTo);
}
