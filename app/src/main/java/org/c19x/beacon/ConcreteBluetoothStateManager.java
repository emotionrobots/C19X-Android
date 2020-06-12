package org.c19x.beacon;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.c19x.data.Logger;
import org.c19x.data.type.BluetoothState;

/**
 * Monitors bluetooth state changes.
 */
public class ConcreteBluetoothStateManager implements BluetoothStateManager {
    private final static String tag = ConcreteBluetoothStateManager.class.getName();
    private BluetoothState state;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == BluetoothAdapter.ACTION_STATE_CHANGED) {
                try {
                    final int nativeState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    Logger.debug(tag, "Bluetooth state changed (nativeState={})", nativeState);
                    switch (nativeState) {
                        case BluetoothAdapter.STATE_ON:
                            Logger.debug(tag, "Power ON");
                            state = BluetoothState.poweredOn;
                            delegates.forEach(d -> d.bluetoothStateManager(BluetoothState.poweredOn));
                            break;
                        case BluetoothAdapter.STATE_OFF:
                            Logger.debug(tag, "Power OFF");
                            state = BluetoothState.poweredOff;
                            delegates.forEach(d -> d.bluetoothStateManager(BluetoothState.poweredOff));
                            break;
                    }
                } catch (Throwable e) {
                    Logger.warn(tag, "Bluetooth state change exception", e);
                }
            }
        }
    };

    /**
     * Monitors bluetooth state changes.
     */
    public ConcreteBluetoothStateManager(Context context) {
        state = state();

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    public BluetoothState state() {
        if (state == null) {
            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                state = BluetoothState.unsupported;
                return state;
            }
            switch (BluetoothAdapter.getDefaultAdapter().getState()) {
                case BluetoothAdapter.STATE_ON:
                    state = BluetoothState.poweredOn;
                    break;
                case BluetoothAdapter.STATE_OFF:
                    state = BluetoothState.poweredOff;
                    break;
                default:
                    state = BluetoothState.resetting;
                    break;
            }
        }
        return state;
    }
}
