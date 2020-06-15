package org.c19x.beacon;

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
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import org.c19x.data.Logger;
import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.BluetoothState;
import org.c19x.data.type.RSSI;
import org.c19x.data.type.TimeInterval;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR;
import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS;

public class ConcreteTransmitter implements Transmitter, BluetoothStateManagerDelegate {
    private final static String tag = ConcreteTransmitter.class.getName();
    /// Beacon code generator for creating cryptographically secure public codes that can be later used for on-device matching.
    private final BeaconCodes beaconCodes;
    /// Automatically change beacon codes at regular intervals.
    private final TimeInterval updateCodeAfter;

    private final Context context;
    private final Handler handler;
    private BluetoothStateManager bluetoothStateManager;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private BluetoothGattServer bluetoothGattServer;
    private AdvertiseCallback advertiseCallback;
    private BeaconCode beaconCode;
    private boolean enabled = false;

    /**
     * Transmitter starts automatically when Bluetooth is enabled. Use the updateBeaconCode() function
     * to manually change the beacon code being broadcasted by the transmitter. The code is also
     * automatically updated after the given time interval.
     */
    public ConcreteTransmitter(Context context, BluetoothStateManager bluetoothStateManager, BeaconCodes beaconCodes, TimeInterval updateCodeAfter) {
        this.context = context;
        this.bluetoothStateManager = bluetoothStateManager;
        this.beaconCodes = beaconCodes;
        this.updateCodeAfter = updateCodeAfter;
        this.handler = new Handler(Looper.getMainLooper());
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isMultipleAdvertisementSupported()) {
            bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        }
        if (bluetoothLeAdvertiser == null) {
            Logger.warn(tag, "Bluetooth LE advertiser unsupported");
            return;
        }
        bluetoothStateManager.delegates.add(this);
        bluetoothStateManager(bluetoothStateManager.state());
        scheduleUpdateBeaconCode(updateCodeAfter);
    }

    private void scheduleUpdateBeaconCode(final TimeInterval updateCodeAfter) {
        handler.postDelayed(() -> {
            Logger.debug(tag, "Automatic beacon code update");
            updateBeaconCode();
            scheduleUpdateBeaconCode(updateCodeAfter);
        }, updateCodeAfter.value * 1000);
    }

    @Override
    public void start(String source) {
        if (bluetoothLeAdvertiser == null) {
            Logger.warn(tag, "Bluetooth LE advertiser unsupported");
            return;
        }
        if (bluetoothStateManager.state() != BluetoothState.poweredOn) {
            Logger.warn(tag, "Bluetooth is not powered on");
            return;
        }
        if (advertiseCallback != null) {
            Logger.warn(tag, "Already started");
            return;
        }
        enabled = true;
        updateBeaconCode();
        Logger.debug(tag, "start");
    }

    @Override
    public void stop(String source) {
        enabled = false;
        if (advertiseCallback == null) {
            Logger.warn(tag, "Already stopped");
            return;
        }
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        advertiseCallback = null;
        if (bluetoothGattServer != null) {
            bluetoothGattServer.clearServices();
            bluetoothGattServer.close();
            bluetoothGattServer = null;
        }
    }

    @Override
    public void updateBeaconCode() {
        beaconCode = beaconCodes.get();
        if (beaconCode == null) {
            Logger.warn(tag, "Beacon codes exhausted");
            return;
        }
        Logger.debug(tag, "updateBeaconCode (code={})", beaconCode);
        if (bluetoothLeAdvertiser == null) {
            Logger.warn(tag, "Bluetooth LE advertiser unsupported");
            return;
        }
        if (bluetoothStateManager.state() != BluetoothState.poweredOn) {
            Logger.warn(tag, "Bluetooth is not powered on");
            return;
        }
        if (advertiseCallback != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            advertiseCallback = null;
        }
        if (bluetoothGattServer != null) {
            bluetoothGattServer.clearServices();
            bluetoothGattServer.close();
            bluetoothGattServer = null;
        }
        if (!enabled) {
            return;
        }
        final long beaconServiceUUIDPrefix = beaconServiceUUID.getMostSignificantBits();
        bluetoothGattServer = startGattServer(context, beaconServiceUUIDPrefix);
        setGattService(context, bluetoothGattServer, beaconServiceUUIDPrefix, beaconCode);
        advertiseCallback = startAdvertising(bluetoothLeAdvertiser, beaconServiceUUIDPrefix);
        Logger.debug(tag, "transmitting (code={})", beaconCode);
    }

    @Override
    public boolean isSupported() {
        return bluetoothLeAdvertiser != null;
    }

    @Override
    public BeaconCode beaconCode() {
        if (beaconCode == null) {
            beaconCode = beaconCodes.get();
        }
        return beaconCode;
    }

    @Override
    public void bluetoothStateManager(BluetoothState didUpdateState) {
        Logger.debug(tag, "Update state (state={})", didUpdateState);
        if (didUpdateState == BluetoothState.poweredOn) {
            start("didUpdateState|poweredOn");
        } else if (didUpdateState == BluetoothState.poweredOff) {
            stop("didUpdateState|poweredOff");
        }
    }

    private final static AdvertiseCallback startAdvertising(final BluetoothLeAdvertiser bluetoothLeAdvertiser, final long serviceId) {
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
                Logger.debug(tag, "Advert started (settings={})", settingsInEffect);
            }

            @Override
            public void onStartFailure(int errorCode) {
                Logger.warn(tag, "Advert start failure (error={})", onStartFailureErrorCodeToString(errorCode));
            }
        };
        bluetoothLeAdvertiser.startAdvertising(settings, data, callback);
        return callback;
    }

    private final static BluetoothGattServer startGattServer(final Context context, final long serviceId) {
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Logger.warn(tag, "Bluetooth unsupported");
            return null;
        }
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
                    final BeaconCode beaconCode = new BeaconCode(byteBuffer.getLong(0));
                    final RSSI rssi = new RSSI(byteBuffer.getInt(Long.BYTES));
                    Logger.debug(tag, "Detected beacon (beaconCode={},rssi={})", beaconCode, rssi);
                    if (responseNeeded) {
                        server.get().sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                    }
                    delegates.forEach(d -> d.receiver(beaconCode, rssi));
                } else {
                    if (responseNeeded) {
                        server.get().sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                    }
                }
                server.get().cancelConnection(device);
            }
        };
        server.set(bluetoothManager.openGattServer(context, callback));
        Logger.debug(tag, "GATT server started (serviceId={})", serviceId);
        return server.get();
    }

    private final static void setGattService(final Context context, final BluetoothGattServer bluetoothGattServer, final long serviceId, final BeaconCode beaconCode) {
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Logger.warn(tag, "Bluetooth unsupported");
            return;
        }
        if (bluetoothGattServer == null) {
            Logger.warn(tag, "Bluetooth LE advertiser unsupported");
            return;
        }
        bluetoothManager.getConnectedDevices(BluetoothProfile.GATT).forEach(device -> bluetoothGattServer.cancelConnection(device));
        bluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER).forEach(device -> bluetoothGattServer.cancelConnection(device));
        bluetoothGattServer.clearServices();
        // Service UUID = serviceID (upper 64) + 0 (lower 64)
        // Characteristic UUID = serviceID (upper 64) + beaconCode (lower 64)
        final BluetoothGattService service = new BluetoothGattService(new UUID(serviceId, 0), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        final BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                new UUID(serviceId, beaconCode.value),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        service.addCharacteristic(characteristic);
        bluetoothGattServer.addService(service);
        Logger.debug(tag, "setGattService (beaconCode={},serviceUUID={},characteristicUUID={})",
                beaconCode, service.getUuid(), characteristic.getUuid());
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

}
