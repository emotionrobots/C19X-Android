package org.c19x.beacon.ble3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import org.c19x.C19XApplication;
import org.c19x.beacon.BeaconListener;
import org.c19x.beacon.BeaconReceiver;
import org.c19x.util.FlipFlopTimer;
import org.c19x.util.Logger;
import org.c19x.util.messaging.Broadcaster;
import org.c19x.util.messaging.DefaultBroadcaster;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Bluetooth low energy scanner for collecting the identifier of all other discoverable devices.
 */
public class BLEReceiver extends DefaultBroadcaster<BeaconListener> implements BeaconReceiver {
    private final static String tag = BLEReceiver.class.getName();
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothLeScanner;
    private final AtomicReference<ScanCallback> scanCallback = new AtomicReference<>(null);
    private final ConcurrentLinkedQueue<ScanResult> scanResults = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final FlipFlopTimer flipFlopTimer;


    private final static List<ScanFilter> getBluetoothLeScanFilter() {
        final List<ScanFilter> filter = new ArrayList<>(1);
        filter.add(new ScanFilter.Builder().setServiceUuid(
                new ParcelUuid(new UUID(BLETransmitter.bleServiceId, 0)),
                new ParcelUuid(new UUID(0xFFFFFFFFFFFFFFFFl, 0)))
                .build());
        return filter;
    }

    private final static ScanSettings getBluetoothLeScanSettings() {
        return new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(0)
                .build();

    }

    private final void startScan() {
        Logger.debug(tag, "Beacon receiver starting BLE scanner");
        if (bluetoothAdapter != null && bluetoothLeScanner != null && bluetoothAdapter.isEnabled() && started.get()) {
            try {
                final List<ScanFilter> scanFilter = getBluetoothLeScanFilter();
                final ScanSettings scanSettings = getBluetoothLeScanSettings();
                scanCallback.set(getScanCallback(this, started, scanResults));
                bluetoothLeScanner.startScan(scanFilter, scanSettings, scanCallback.get());
                Logger.debug(tag, "Beacon receiver started BLE scanner");
            } catch (Throwable e) {
                Logger.warn(tag, "Beacon receiver start exception", e);
            }
        }
    }

    private final void stopScan() {
        Logger.debug(tag, "Beacon receiver stopping BLE scanner");
        if (bluetoothAdapter != null && bluetoothLeScanner != null && scanCallback.get() != null) {
            try {
                bluetoothLeScanner.stopScan(scanCallback.get());
                scanCallback.set(null);
                processResults(scanResults, this);
                Logger.debug(tag, "Beacon receiver stopped BLE scanner");
            } catch (Throwable e) {
                Logger.warn(tag, "Beacon receiver stop exception", e);
            }
        }
    }

    private final static ScanCallback getScanCallback(final Broadcaster<BeaconListener> broadcaster, final AtomicBoolean started, final ConcurrentLinkedQueue<ScanResult> scanResults) {
        return new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                scanResults.add(result);
                Logger.debug(tag, "Beacon receiver scan result (result={})", result);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                switch (errorCode) {
                    case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                        Logger.warn(tag, "Beacon receiver scan failed (error=alreadyStarted)");
                        break;
                    case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                        Logger.warn(tag, "Beacon receiver scan failed (error=registrationFailed)");
                        break;
                    case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                        Logger.warn(tag, "Beacon receiver scan failed (error=internalError)");
                        break;
                    case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                        Logger.warn(tag, "Beacon receiver scan failed (error=featureUnsupported)");
                        break;
                    default:
                        Logger.warn(tag, "Beacon receiver scan failed (error=unknown,errorCode={})", errorCode);
                        break;
                }
                if (errorCode != ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                    started.set(false);
                }
                broadcaster.broadcast(l -> l.error(errorCode));
            }
        };
    }

    public BLEReceiver() {
        this.bluetoothManager = (BluetoothManager) C19XApplication.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.bluetoothLeScanner = (bluetoothAdapter != null ? bluetoothAdapter.getBluetoothLeScanner() : null);
        if (bluetoothAdapter == null) {
            Logger.warn(tag, "Bluetooth unsupported");
        }
        if (bluetoothLeScanner == null) {
            Logger.warn(tag, "Bluetooth LE scanner unsupported");
        }
        this.flipFlopTimer = new FlipFlopTimer(5000, 3000, () -> startScan(), () -> stopScan());
    }

    private final static List<ScanResult> getConnectQueue(final Collection<ScanResult> scanResults) {
        // Consolidate by maximum RSSI to find nearest result for each device
        final Map<BluetoothDevice, ScanResult> devices = new HashMap<>(scanResults.size());
        scanResults.forEach(scanResult -> {
            final BluetoothDevice device = scanResult.getDevice();
            final int rssi = scanResult.getRssi();
            final ScanResult scanResultMaxRssi = devices.get(device);
            if (scanResultMaxRssi == null || scanResultMaxRssi.getRssi() < rssi) {
                devices.put(device, scanResult);
            }
        });
        // Sort consolidated results by RSSI for connection
        return devices.values().stream().sorted((a, b) -> Integer.compare(b.getRssi(), a.getRssi())).collect(Collectors.toList());
    }

    /**
     * Process scan results collected in a scan cycle.
     */
    private final static void processResults(final ConcurrentLinkedQueue<ScanResult> scanResults, final Broadcaster<BeaconListener> broadcaster) {
        final long timestamp = C19XApplication.getTimestamp().getTime();
        final List<ScanResult> connectQueue = getConnectQueue(scanResults);
        scanResults.clear();
        Logger.debug(tag, "Beacon receiver scan result (timestamp={},devices={})", timestamp, connectQueue.size());

        final long beaconCode = C19XApplication.getBeaconTransmitter().getId();
        for (final ScanResult scanResult : connectQueue) {
            try {
                final Long transmitterBeaconCode = exchangeId(scanResult, beaconCode).get(5, TimeUnit.SECONDS);
                if (transmitterBeaconCode != null) {
                    broadcaster.broadcast(l -> l.detect(timestamp, transmitterBeaconCode, scanResult.getRssi()));
                }
            } catch (Throwable e) {
            }
        }
    }

    private final static Long getTransmitterIdAndSendReceiverId(final BluetoothGatt gatt, final byte[] value) {
        for (final BluetoothGattService service : gatt.getServices()) {
            for (final BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                if (characteristic.getUuid().getMostSignificantBits() == BLETransmitter.bleServiceId) {
                    final long transmitterId = characteristic.getUuid().getLeastSignificantBits();
                    characteristic.setValue(value);
                    gatt.writeCharacteristic(characteristic);
                    return transmitterId;
                }
            }
        }
        return null;
    }

    private final static Future<Long> exchangeId(final ScanResult scanResult, final long receiverId) {
        final CompletableFuture<Long> future = new CompletableFuture<>();
        final byte[] encodedCharacteristicData = encodedReceiverCharacteristicData(receiverId, scanResult.getRssi());
        final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
            private Long transmitterId = null;

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Logger.debug(tag, "Beacon receiver connected to GATT server");
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Logger.debug(tag, "Beacon receiver disconnected from GATT server");
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                transmitterId = getTransmitterIdAndSendReceiverId(gatt, encodedCharacteristicData);
                Logger.debug(tag, "Beacon receiver scanned services and characteristics (transmitterId={})", transmitterId);
                if (transmitterId == null) {
                    gatt.close();
                    future.complete(null);
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Logger.debug(tag, "Beacon receiver exchanged ID (receiverId={},transmitterId={},rssi={},success={})", receiverId, transmitterId, scanResult.getRssi(), (status == BluetoothGatt.GATT_SUCCESS));
                gatt.close();
                future.complete(transmitterId);
            }
        };
        scanResult.getDevice().connectGatt(C19XApplication.getContext(), false, bluetoothGattCallback);
        return future;
    }

    private final static byte[] encodedReceiverCharacteristicData(final long id, final int rssi) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putLong(0, id);
        byteBuffer.putInt(Long.BYTES, rssi);
        return byteBuffer.array();
    }


    @Override
    public void start() {
        if (!started.get()) {
            if (bluetoothAdapter != null && bluetoothLeScanner != null && bluetoothAdapter.isEnabled()) {
                flipFlopTimer.start();
                started.set(true);
                broadcast(l -> l.start());
                Logger.debug(tag, "Beacon receiver started");
            } else {
                Logger.warn(tag, "Beacon receiver start failed (adapter={},enabled={})", bluetoothAdapter, (bluetoothAdapter != null && bluetoothAdapter.isEnabled()));
            }
        } else {
            Logger.warn(tag, "Beacon receiver already started");
        }
    }

    @Override
    public void stop() {
        if (started.get()) {
            started.set(false);
            if (flipFlopTimer.isStarted()) {
                flipFlopTimer.stop();
            }
            broadcast(l -> l.stop());
            Logger.debug(tag, "Beacon receiver stopped");
        } else {
            Logger.warn(tag, "Beacon receiver already stopped");
        }
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isSupported() {
        return bluetoothLeScanner != null;
    }

    @Override
    public void setDutyCycle(int onDuration, int offDuration) {
        Logger.debug(tag, "Set receiver duty cycle (on={},off={})", onDuration, offDuration);
        flipFlopTimer.setOnDuration(onDuration);
        flipFlopTimer.setOffDuration(offDuration);
    }

    /**
     * Get device id(s) from the lower 64-bits of matching service UUIDs.
     *
     * @param uuids
     * @return
     */
    private final static List<Long> getDeviceIds(List<ParcelUuid> uuids) {
        final List<Long> deviceIds = new ArrayList<>(uuids.size());
        for (final ParcelUuid uuid : uuids) {
            if (uuid.getUuid().getMostSignificantBits() == C19XApplication.bluetoothLeServiceId) {
                deviceIds.add(uuid.getUuid().getLeastSignificantBits());
            }
        }
        return deviceIds;
    }
}
