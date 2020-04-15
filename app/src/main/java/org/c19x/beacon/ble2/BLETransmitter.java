package org.c19x.beacon.ble2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;

import org.c19x.C19XApplication;
import org.c19x.beacon.BeaconListener;
import org.c19x.beacon.BeaconTransmitter;
import org.c19x.util.Logger;
import org.c19x.util.messaging.DefaultBroadcaster;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private boolean started = false;
    private long id = 0;
    private AdvertiseData scanResponse;
    private BluetoothGattServer bluetoothGattServer;

    public BLETransmitter() {
        this.advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
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

        this.bluetoothManager = (BluetoothManager) C19XApplication.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
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

            final BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {
                @Override
                public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                    super.onConnectionStateChange(device, status, newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Logger.debug(tag, "Beacon transmitter GATT server connected to client");
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Logger.debug(tag, "Beacon transmitter GATT server disconnected from client");
                    }
                }

                @Override
                public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                    super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                    if (characteristic.getUuid().getMostSignificantBits() == C19XApplication.bluetoothLeServiceId) {
                        final ByteBuffer byteBuffer = ByteBuffer.wrap(value);
                        final long timestamp = C19XApplication.getTimestamp().getTime();
                        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        final long id = byteBuffer.getLong(0);
                        final int rssi = byteBuffer.getInt(Long.BYTES);
                        Logger.debug(tag, "Beacon transmitter GATT server received write request (timestamp={},id={},rssi={},data={})", timestamp, id, rssi);
                        broadcast(l -> l.detect(timestamp, id, rssi));
                    }
                }
            };


            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bluetoothLeAdvertiser != null) {
                try {
                    bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, scanResponse, advertiseCallback);
                    bluetoothGattServer = bluetoothManager.openGattServer(C19XApplication.getContext(), bluetoothGattServerCallback);
                    bluetoothGattServer.addService(createBluetoothGattService());
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

    private final BluetoothGattService createBluetoothGattService() {
        final BluetoothGattService bluetoothGattService = new BluetoothGattService(new UUID(C19XApplication.bluetoothLeServiceId, id), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        final BluetoothGattCharacteristic bluetoothGattCharacteristic = new BluetoothGattCharacteristic(
                new UUID(C19XApplication.bluetoothLeServiceId, 0),
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        bluetoothGattService.addCharacteristic(bluetoothGattCharacteristic);
        return bluetoothGattService;
    }

    @Override
    public synchronized void stop() {
        if (started) {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bluetoothLeAdvertiser != null) {
                try {
                    if (bluetoothGattServer != null) {
                        bluetoothGattServer.close();
                    }
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
