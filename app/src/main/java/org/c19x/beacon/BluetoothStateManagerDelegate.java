package org.c19x.beacon;

import org.c19x.data.type.BluetoothState;

public interface BluetoothStateManagerDelegate {
    void bluetoothStateManager(BluetoothState didUpdateState);
}
