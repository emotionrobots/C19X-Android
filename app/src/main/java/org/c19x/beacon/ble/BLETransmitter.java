package org.c19x.beacon.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.ParcelUuid;

import org.c19x.C19XApplication;
import org.c19x.beacon.BeaconListener;
import org.c19x.beacon.BeaconTransmitter;
import org.c19x.util.Logger;
import org.c19x.util.messaging.DefaultBroadcaster;

import java.util.UUID;

/**
 * Bluetooth low energy transmitter for broadcasting an identifier encoded in the lower 64-bit
 * of the UUID.
 */
public class BLETransmitter extends DefaultBroadcaster<BeaconListener> implements BeaconTransmitter {
    private final static String tag = BLETransmitter.class.getName();
    private final AdvertiseSettings advertiseSettings;
    private final AdvertiseData advertiseData;
    private final AdvertiseCallback advertiseCallback;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private boolean started = false;
    private long id = 0;
    private AdvertiseData scanResponse;

    public BLETransmitter() {
        this.advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(false)
                .build();
        this.advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build();
        this.advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                started = true;
                broadcast(l -> l.start());
                Logger.debug(tag, "Beacon transmitter started (id={})", id);
            }

            @Override
            public void onStartFailure(int errorCode) {
                started = false;
                broadcast(l -> l.startFailed(errorCode));
                switch (errorCode) {
                    case ADVERTISE_FAILED_DATA_TOO_LARGE:
                        Logger.warn(tag, "Beacon transmitter start failed (error=dataTooLarge)");
                        break;
                    case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                        Logger.warn(tag, "Beacon transmitter start failed (error=tooManyAdvertisers)");
                        break;
                    case ADVERTISE_FAILED_ALREADY_STARTED:
                        Logger.warn(tag, "Beacon transmitter start failed (error=alreadyStarted)");
                        break;
                    case ADVERTISE_FAILED_INTERNAL_ERROR:
                        Logger.warn(tag, "Beacon transmitter start failed (error=internalError)");
                        break;
                    case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                        Logger.warn(tag, "Beacon transmitter start failed (error=featureUnsupported)");
                        break;
                    default:
                        Logger.warn(tag, "Beacon transmitter start failed (error=unknown,errorCode={})", errorCode);
                        break;
                }
            }
        };
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.bluetoothLeAdvertiser = (bluetoothAdapter != null && bluetoothAdapter.isMultipleAdvertisementSupported() ? bluetoothAdapter.getBluetoothLeAdvertiser() : null);
        if (bluetoothAdapter == null) {
            Logger.warn(tag, "Bluetooth unsupported");
        }
        if (bluetoothLeAdvertiser == null) {
            Logger.warn(tag, "Bluetooth LE advertiser unsupported");
        } else {
            Logger.debug(tag, "Bluetooth LE advertising is supported");
        }
    }

    @Override
    public synchronized void start(long id) {
        if (!started) {
            this.id = id;
            final AdvertiseData scanResponse = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(new ParcelUuid(new UUID(C19XApplication.bluetoothLeServiceId, id)))
                    .build();
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bluetoothLeAdvertiser != null) {
                try {
                    bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, scanResponse, advertiseCallback);
                } catch (Throwable e) {
                    Logger.warn(tag, "Beacon transmitter start exception", e);
                }
            } else {
                started = false;
                broadcast(l -> l.startFailed(AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED));
            }
        } else {
            Logger.warn(tag, "Beacon transmitter already started");
        }
    }

    @Override
    public synchronized void stop() {
        if (started) {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bluetoothLeAdvertiser != null) {
                try {
                    bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
                } catch (Throwable e) {
                    Logger.warn(tag, "Beacon transmitter stop exception", e);
                }
            } else {
                Logger.warn(tag, "Beacon transmitter stop failed (adapter={},enabled={},advertiser={})", bluetoothAdapter != null, (bluetoothAdapter != null && bluetoothAdapter.isEnabled()), bluetoothLeAdvertiser != null);
            }
            started = false;
            broadcast(l -> l.stop());
            Logger.debug(tag, "Beacon transmitter stopped");
        } else {
            Logger.warn(tag, "Beacon transmitter already stopped");
        }
    }

    @Override
    public synchronized boolean isStarted() {
        return started;
    }

    @Override
    public synchronized long getId() {
        return id;
    }

    @Override
    public synchronized void setId(final long id) {
        final boolean state = started;
        if (started) {
            stop();
        }
        if (state) {
            start(id);
        }
        this.id = id;
    }
}
