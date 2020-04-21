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
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS;

/**
 * Bluetooth low energy transmitter that is compatible with iOS and Android.
 */
public class BLETransmitter extends DefaultBroadcaster<BeaconListener> implements BeaconTransmitter {
    public final static long bleServiceId = 9803801938501395l;
    private final static String tag = BLETransmitter.class.getName();
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private final AtomicReference<AdvertiseCallback> advertiseCallback = new AtomicReference<>(null);
    private final AtomicReference<BluetoothGattServer> bluetoothGattServer = new AtomicReference<>(null);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicLong beaconCode = new AtomicLong(0);


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

    private final static AdvertiseCallback startAdvertising(final BluetoothLeAdvertiser bluetoothLeAdvertiser, final long serviceId, final long beaconCode, final Broadcaster<BeaconListener> broadcaster, final AtomicBoolean started) {
        final AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .build();

        final AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(new UUID(serviceId, 0)))
                .build();

        final AdvertiseCallback callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Logger.debug(tag, "Advertising start success (settings={})", settingsInEffect);
                started.set(true);
                broadcaster.broadcast(l -> l.start());
            }

            @Override
            public void onStartFailure(int errorCode) {
                Logger.warn(tag, "Advertising start failure (error={})", onStartFailureErrorCodeToString(errorCode));
                if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) {
                    started.set(false);
                }
                broadcaster.broadcast(l -> l.error(errorCode));
            }
        };

        bluetoothLeAdvertiser.startAdvertising(settings, data, callback);
        return callback;
    }

    private final static void stopAdvertising(final BluetoothLeAdvertiser bluetoothLeAdvertiser, final AdvertiseCallback advertiseCallback) {
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        Logger.debug(tag, "Advertising stopped");
    }

    private final static BluetoothGattServer startGattServer(final BluetoothManager bluetoothManager, final long serviceId, final Broadcaster<BeaconListener> broadcaster) {
        // Data = receiverBeaconCode (8 bytes long) + rssi (4 bytes int)
        final AtomicReference<BluetoothGattServer> server = new AtomicReference<>(null);
        final BluetoothGattServerCallback callback = new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                Logger.debug(tag, "GATT server connection state change (device={},status={},newState={})",
                        device, status, onConnectionStateChangeStatusToString(newState));
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                Logger.debug(tag, "GATT server characteristic write request (device={},responseNeeded={},value={})",
                        device, responseNeeded, Arrays.toString(value));
                if (characteristic.getUuid().getMostSignificantBits() == serviceId && value.length == (Long.BYTES + Integer.BYTES)) {
                    final ByteBuffer byteBuffer = ByteBuffer.wrap(value);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    final long id = byteBuffer.getLong(0);
                    final int rssi = byteBuffer.getInt(Long.BYTES);
                    final long timestamp = C19XApplication.getTimestamp().getTime();
                    Logger.debug(tag, "GATT server characteristic received (id={},rssi={},timestamp={})", id, rssi, timestamp);
                    if (responseNeeded) {
                        server.get().sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                    }
                    broadcaster.broadcast(l -> l.detect(timestamp, id, rssi));
                } else {
                    if (responseNeeded) {
                        server.get().sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                    }
                }
                server.get().cancelConnection(device);
            }
        };
        server.set(bluetoothManager.openGattServer(C19XApplication.getContext(), callback));

        Logger.debug(tag, "GATT server started (serviceId={})", serviceId);
        return server.get();
    }

    private final static void stopGattServer(final BluetoothGattServer bluetoothGattServer) {
        bluetoothGattServer.close();
        Logger.debug(tag, "GATT server stopped");
    }

    private final static void setGattService(final BluetoothManager bluetoothManager, final BluetoothGattServer bluetoothGattServer, final long serviceId, final long beaconCode) {
        if (bluetoothManager != null && bluetoothGattServer != null) {
            bluetoothManager.getConnectedDevices(BluetoothProfile.GATT).forEach(device -> bluetoothGattServer.cancelConnection(device));
            bluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER).forEach(device -> bluetoothGattServer.cancelConnection(device));
        }

        if (bluetoothGattServer != null) {
            bluetoothGattServer.clearServices();

            // Service UUID = serviceID (upper 64) + 0 (lower 64)
            // Characteristic UUID = serviceID (upper 64) + beaconCode (lower 64)
            final BluetoothGattService service = new BluetoothGattService(new UUID(serviceId, 0), BluetoothGattService.SERVICE_TYPE_PRIMARY);

            final BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                    new UUID(serviceId, beaconCode),
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            service.addCharacteristic(characteristic);

            bluetoothGattServer.addService(service);
            Logger.debug(tag, "GATT service updated (serviceId={},beaconCode={},serviceUUID={},characteristicUUID={})",
                    serviceId, beaconCode, service.getUuid(), characteristic.getUuid());
        }
    }

    private final static String onConnectionStateChangeStatusToString(final int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "STATE_CONNECTED";
            case BluetoothProfile.STATE_CONNECTING:
                return "STATE_CONNECTING";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "STATE_DISCONNECTING";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "STATE_DISCONNECTED";
            default:
                return "UNKNOWN_STATE_" + state;
        }
    }

    private final static String onStartFailureErrorCodeToString(final int errorCode) {
        switch (errorCode) {
            case ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "ADVERTISE_FAILED_DATA_TOO_LARGE";
            case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS";
            case ADVERTISE_FAILED_ALREADY_STARTED:
                return "ADVERTISE_FAILED_ALREADY_STARTED";
            case ADVERTISE_FAILED_INTERNAL_ERROR:
                return "ADVERTISE_FAILED_INTERNAL_ERROR";
            case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "ADVERTISE_FAILED_FEATURE_UNSUPPORTED";
            default:
                return "UNKNOWN_ERROR_CODE_" + errorCode;
        }
    }

    @Override
    public synchronized void start(final long id) {
        if (!started.get()) {
            beaconCode.set(id);
            if (bluetoothLeAdvertiser != null && bluetoothAdapter.isEnabled()) {
                Logger.warn(tag, "Beacon transmitter starting (id={})", id);
                try {
                    bluetoothGattServer.set(startGattServer(bluetoothManager, bleServiceId, this));
                    setGattService(bluetoothManager, bluetoothGattServer.get(), bleServiceId, beaconCode.get());
                    advertiseCallback.set(startAdvertising(bluetoothLeAdvertiser, bleServiceId, beaconCode.get(), this, started));
                } catch (Throwable e) {
                    Logger.warn(tag, "Beacon transmitter start exception", e);
                }
            } else {
                started.set(false);
                broadcast(l -> l.error(ADVERTISE_FAILED_FEATURE_UNSUPPORTED));
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
                    stopAdvertising(bluetoothLeAdvertiser, advertiseCallback.get());
                    advertiseCallback.set(null);
                }
                if (bluetoothGattServer.get() != null) {
                    bluetoothGattServer.get().clearServices();
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
        this.beaconCode.set(id);
        setGattService(bluetoothManager, bluetoothGattServer.get(), bleServiceId, beaconCode.get());
    }
}
