package org.c19x.beacon;

import org.c19x.data.type.BluetoothState;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface BluetoothStateManager {
    Queue<BluetoothStateManagerDelegate> delegates = new ConcurrentLinkedQueue<>();

    BluetoothState state();
}
