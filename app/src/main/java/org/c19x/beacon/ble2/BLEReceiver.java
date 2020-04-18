package org.c19x.beacon.ble2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import org.c19x.C19XApplication;
import org.c19x.beacon.BeaconListener;
import org.c19x.beacon.BeaconReceiver;
import org.c19x.util.FlipFlopTimer;
import org.c19x.util.Logger;
import org.c19x.util.messaging.DefaultBroadcaster;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bluetooth low energy scanner for collecting the identifier of all other discoverable devices.
 */
public class BLEReceiver extends DefaultBroadcaster<BeaconListener> implements BeaconReceiver {
    private final static String tag = BLEReceiver.class.getName();
    private final List<ScanFilter> bluetoothLeScanFilter = Arrays.asList(new ScanFilter.Builder()
            .setServiceUuid(
                    new ParcelUuid(new UUID(C19XApplication.bluetoothLeServiceId, 0)),
                    new ParcelUuid(new UUID(0xFFFFFFFFFFFFFFFFl, 0)))
            .build());
    private final ScanSettings bluetoothLeScanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)
            .build();
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothLeScanner;
    private final ConcurrentLinkedQueue<ScanResult> scanResults = new ConcurrentLinkedQueue<>();

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            scanResults.add(result);
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
                started = false;
            }
            broadcast(l -> l.error(errorCode));
        }
    };

    private final class GattCallback extends BluetoothGattCallback {
        private final long id;
        private final int rssi;
        private final byte[] data;

        public GattCallback(final long id, final int rssi) {
            this.id = id;
            this.rssi = rssi;
            final ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.putLong(0, id);
            byteBuffer.putInt(Long.BYTES, rssi);
            this.data = byteBuffer.array();
        }

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
            super.onServicesDiscovered(gatt, status);
            BluetoothGattService c19xBeaconService = null;
            for (BluetoothGattService bluetoothGattService : gatt.getServices()) {
                final boolean match = (bluetoothGattService.getUuid().getMostSignificantBits() == C19XApplication.bluetoothLeServiceId);
                //Logger.debug(tag, "Beacon receiver discovered service (service={},match={})", bluetoothGattService.getUuid(), match);
                if (match) {
                    c19xBeaconService = bluetoothGattService;
                }
            }
            if (c19xBeaconService != null) {
                //Logger.debug(tag, "Beacon receiver discovered C19X beacon service");
                BluetoothGattCharacteristic c19xBeaconServiceCharacteristic = null;
                for (BluetoothGattCharacteristic bluetoothGattCharacteristic : c19xBeaconService.getCharacteristics()) {
                    final boolean match = (bluetoothGattCharacteristic.getUuid().getMostSignificantBits() == C19XApplication.bluetoothLeServiceId);
                    // Logger.debug(tag, "Beacon receiver discovered characteristic (service={},characteristic={},match={})", c19xBeaconService.getUuid(), bluetoothGattCharacteristic.getUuid(), match);
                    if (match) {
                        c19xBeaconServiceCharacteristic = bluetoothGattCharacteristic;
                    }
                }
                if (c19xBeaconServiceCharacteristic != null) {
                    //Logger.debug(tag, "Beacon receiver sending echo data to transmitter (id={},rssi={})", id, rssi);
                    c19xBeaconServiceCharacteristic.setValue(data);
                    gatt.writeCharacteristic(c19xBeaconServiceCharacteristic);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Logger.debug(tag, "Beacon receiver sent echo data to transmitter (id={},rssi={},success={})", id, rssi, (status == BluetoothGatt.GATT_SUCCESS));
            gatt.close();
        }
    }


    private boolean started = false;
    private FlipFlopTimer flipFlopTimer;

    public BLEReceiver() {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Logger.error(tag, "Bluetooth unsupported");
        }
        this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Logger.warn(tag, "Bluetooth LE scanner unsupported");
        } else {
            Logger.debug(tag, "Bluetooth LE scanner is supported");
        }
        this.flipFlopTimer = new FlipFlopTimer(5000, 3000,
                () -> {
                    Logger.debug(tag, "Beacon receiver starting BLE scanner");
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bluetoothLeScanner != null && started) {
                        try {
                            bluetoothLeScanner.startScan(bluetoothLeScanFilter, bluetoothLeScanSettings, scanCallback);
                            Logger.debug(tag, "Beacon receiver started BLE scanner");
                        } catch (Throwable e) {
                            Logger.warn(tag, "Beacon receiver start exception", e);
                        }
                    }
                },
                () -> {
                    Logger.debug(tag, "Beacon receiver stopping BLE scanner");
                    if (bluetoothAdapter != null && bluetoothLeScanner != null) {
                        try {
                            bluetoothLeScanner.stopScan(scanCallback);
                            Logger.debug(tag, "Beacon receiver stopped BLE scanner");
                            processResults();
                        } catch (Throwable e) {
                            Logger.warn(tag, "Beacon receiver stop exception", e);
                        }
                    }
                });
    }

    /**
     * Process all scan results collected in a scan cycle.
     */
    private synchronized void processResults() {
        final long timestamp = C19XApplication.getTimestamp().getTime();
        // Consolidate results by signal strength
        final Map<Long, ScanResult> devices = new HashMap<>(scanResults.size());
        scanResults.forEach(result -> {
            final int rssi = result.getRssi();
            for (final Long id : getDeviceIds(result.getScanRecord().getServiceUuids())) {
                if (!devices.containsKey(id) || devices.get(id).getRssi() < rssi) {
                    devices.put(id, result);
                }
            }
        });
        Logger.debug(tag, "Beacon receiver found devices (timestamp={},devices={},results={})", timestamp, devices.size(), scanResults.size());
        // Handle results
        final long ownId = C19XApplication.getBeaconTransmitter().getId();
        devices.entrySet().forEach(e -> {
            final long id = e.getKey();
            final ScanResult result = e.getValue();
            final int rssi = result.getRssi();
            Logger.debug(tag, "Beacon receiver found transmitter (timestamp={},id={},rssi={})", timestamp, id, rssi);
            broadcast(l -> l.detect(timestamp, id, rssi));
            Logger.debug(tag, "Beacon receiver sending own id to transmitter via GATT (id={},rssi={})", ownId, rssi);
            result.getDevice().connectGatt(C19XApplication.getContext(), false, new GattCallback(ownId, rssi));
        });
        scanResults.clear();
    }

    @Override
    public void start() {
        if (!started) {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                flipFlopTimer.start();
                started = true;
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
        if (started) {
            started = false;
            if (flipFlopTimer.isStarted()) {
                flipFlopTimer.stop();
            }
            try {
                bluetoothLeScanner.stopScan(scanCallback);
            } catch (Throwable e) {
            }
            broadcast(l -> l.stop());
            Logger.debug(tag, "Beacon receiver stopped");
        } else {
            Logger.warn(tag, "Beacon receiver already stopped");
        }
    }

    @Override
    public boolean isStarted() {
        return started;
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
