package org.c19x.beacon.ble3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
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
import org.c19x.util.messaging.Broadcaster;
import org.c19x.util.messaging.DefaultBroadcaster;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bluetooth low energy transmitter that is compatible with iOS and Android.
 */
public class BLETransmitter extends DefaultBroadcaster<BeaconListener> implements BeaconTransmitter {
    private final static String tag = BLETransmitter.class.getName();
    public final static long bleServiceId = 9803801938501395l;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private final AtomicReference<AdvertiseCallback> advertiseCallback = new AtomicReference<>(null);
    private final AtomicReference<BluetoothGattServer> bluetoothGattServer = new AtomicReference<>(null);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicLong beaconCode = new AtomicLong(0);


    private final static AdvertiseSettings getAdvertiseSettings() {
        return new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .build();
    }

    private final static AdvertiseData getAdvertiseData(final BluetoothGattService bluetoothGattService) {
        return new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(bluetoothGattService.getUuid()))
                .build();
    }

    private final static AdvertiseCallback getAdvertiseCallback(final Broadcaster<BeaconListener> broadcaster, final AtomicBoolean started) {
        return new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                started.set(true);
                broadcaster.broadcast(l -> l.start());
                Logger.debug(tag, "Beacon transmitter started");
            }

            @Override
            public void onStartFailure(int errorCode) {
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
                if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) {
                    started.set(false);
                }
                broadcaster.broadcast(l -> l.error(errorCode));
            }
        };
    }

    private final static BluetoothGattService getBluetoothGattService(final long serviceID, final long beaconCode) {
        // Service UUID = serviceID (upper 64) + 0 (lower 64)
        final BluetoothGattService bluetoothGattService = new BluetoothGattService(new UUID(serviceID, 0), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        // Characteristic UUID = serviceID (upper 64) + beaconCode (lower 64)
        // Data = receiverBeaconCode (8 bytes long) + rssi (4 bytes int)
        final BluetoothGattCharacteristic bluetoothGattCharacteristic = new BluetoothGattCharacteristic(
                new UUID(serviceID, beaconCode),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        bluetoothGattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        bluetoothGattService.addCharacteristic(bluetoothGattCharacteristic);
        Logger.debug(tag, "Created GATT service (serviceId={},beaconCode={},serviceUuid={},characteristicUuid={})",
                serviceID, beaconCode,
                bluetoothGattService.getUuid(),
                bluetoothGattCharacteristic.getUuid());
        return bluetoothGattService;
    }

    private final static BluetoothGattServerCallback getBluetoothGattServerCallback(final Broadcaster<BeaconListener> broadcaster, final long serviceID, final AtomicReference<BluetoothGattServer> bluetoothGattServer) {
        return new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Logger.debug(tag, "Client connected to GATT server");
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Logger.debug(tag, "Client disconnected from GATT server");
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                if (characteristic.getUuid().getMostSignificantBits() == serviceID) {
                    final ByteBuffer byteBuffer = ByteBuffer.wrap(value);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    final long id = byteBuffer.getLong(0);
                    final int rssi = byteBuffer.getInt(Long.BYTES);
                    final long timestamp = C19XApplication.getTimestamp().getTime();
                    Logger.debug(tag, "GATT service received echo data (id={},rssi={},timestamp={})", id, rssi, timestamp);
                    if (responseNeeded) {
                        bluetoothGattServer.get().sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    }
                    broadcaster.broadcast(l -> l.detect(timestamp, id, rssi));
                }
            }
        };

    }


    public BLETransmitter() {
        this.bluetoothManager = (BluetoothManager) C19XApplication.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.bluetoothLeAdvertiser = (bluetoothAdapter != null && bluetoothAdapter.isMultipleAdvertisementSupported() ? bluetoothAdapter.getBluetoothLeAdvertiser() : null);
        if (bluetoothAdapter == null) {
            Logger.warn(tag, "Bluetooth unsupported");
        }
        if (bluetoothLeAdvertiser == null) {
            Logger.warn(tag, "Bluetooth LE advertiser unsupported");
        }
    }

    @Override
    public synchronized void start(long id) {
        if (!started.get()) {
            this.beaconCode.set(id);
            if (bluetoothAdapter != null && bluetoothLeAdvertiser != null && bluetoothAdapter.isEnabled()) {
                Logger.warn(tag, "Beacon transmitter starting (id={})", id);
                try {
                    final BluetoothGattService bluetoothGattService = getBluetoothGattService(bleServiceId, beaconCode.get());
                    final AdvertiseSettings advertiseSettings = getAdvertiseSettings();
                    final AdvertiseData advertiseData = getAdvertiseData(bluetoothGattService);
                    advertiseCallback.set(getAdvertiseCallback(this, started));
                    final BluetoothGattServerCallback bluetoothGattServerCallback = getBluetoothGattServerCallback(this, bleServiceId, bluetoothGattServer);
                    bluetoothGattServer.set(bluetoothManager.openGattServer(C19XApplication.getContext(), bluetoothGattServerCallback));
                    bluetoothGattServer.get().addService(bluetoothGattService);
                    bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback.get());
                } catch (Throwable e) {
                    Logger.warn(tag, "Beacon transmitter start exception", e);
                }
            } else {
                started.set(false);
                broadcast(l -> l.error(AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED));
            }
        } else {
            Logger.warn(tag, "Beacon transmitter already started");
        }
    }


    @Override
    public synchronized void stop() {
        if (started.get()) {
            Logger.warn(tag, "Beacon transmitter stopping");
            try {
                if (bluetoothLeAdvertiser != null && advertiseCallback.get() != null) {
                    bluetoothLeAdvertiser.stopAdvertising(advertiseCallback.get());
                    advertiseCallback.set(null);
                }
                if (bluetoothGattServer.get() != null) {
                    bluetoothGattServer.get().close();
                    bluetoothGattServer.set(null);
                }
            } catch (Throwable e) {
                Logger.warn(tag, "Beacon transmitter stop exception", e);
            }
            started.set(false);
            broadcast(l -> l.stop());
            Logger.debug(tag, "Beacon transmitter stopped");
        } else {
            Logger.warn(tag, "Beacon transmitter already stopped");
        }
    }

    @Override
    public synchronized boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isSupported() {
        return bluetoothLeAdvertiser != null;
    }

    @Override
    public synchronized long getId() {
        return beaconCode.get();
    }

    @Override
    public synchronized void setId(final long id) {
        Logger.debug(tag, "Beacon transmitter set ID (id={},started={})", id, started);
        final boolean state = started.get();
        if (state) {
            stop();
        }
        if (state) {
            start(id);
        }
        this.beaconCode.set(id);
    }
}
