package org.c19x.util.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.c19x.C19XApplication;
import org.c19x.util.Logger;
import org.c19x.util.messaging.DefaultBroadcaster;

/**
 * Monitors bluetooth state changes.
 */
public class BluetoothStateMonitor extends DefaultBroadcaster<BluetoothStateMonitorListener> {
    private final static String tag = BluetoothStateMonitor.class.getName();
    private final IntentFilter intentFilter = new IntentFilter();
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == BluetoothAdapter.ACTION_STATE_CHANGED) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                Logger.debug(tag, "Bluetooth state changed (state={})", state);
                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Logger.debug(tag, "Bluetooth enabling");
                        broadcast(l -> l.enabling());
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Logger.debug(tag, "Bluetooth enabled");
                        broadcast(l -> l.enabled());
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Logger.debug(tag, "Bluetooth disabling");
                        broadcast(l -> l.disabling());
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        Logger.debug(tag, "Bluetooth disabled");
                        broadcast(l -> l.disabled());
                        break;
                }
            }
        }
    };
    private final BluetoothAdapter bluetoothAdapter;
    private boolean started = false;

    /**
     * Monitors bluetooth state changes.
     */
    public BluetoothStateMonitor() {
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    /**
     * Start bluetooth state monitoring.
     */
    public void start() {
        if (!started) {
            if (bluetoothAdapter == null) {
                broadcast(l -> l.unsupported());
            } else {
                C19XApplication.getContext().registerReceiver(broadcastReceiver, intentFilter);
            }
            Logger.debug(tag, "Bluetooth state monitor started (supported={},enabled={})", isSupported(), isEnabled());
            started = true;
        }
    }

    /**
     * Stop bluetooth state monitoring.
     */
    public void stop() {
        if (started) {
            if (bluetoothAdapter != null) {
                C19XApplication.getContext().unregisterReceiver(broadcastReceiver);
            }
            Logger.debug(tag, "Bluetooth state monitor stopped");
            started = false;
        }
    }

    /**
     * Is monitor active?
     *
     * @return
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Is bluetooth supported?
     *
     * @return
     */
    public boolean isSupported() {
        return bluetoothAdapter != null;
    }

    /**
     * Is bluetooth supported and enabled?
     *
     * @return
     */
    public boolean isEnabled() {
        return (bluetoothAdapter != null && bluetoothAdapter.isEnabled());
    }

}
