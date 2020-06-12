package org.c19x.beacon;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import org.c19x.data.Logger;
import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.BluetoothState;
import org.c19x.data.type.RSSI;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ConcreteReceiver implements Receiver, BluetoothStateManagerDelegate {
    private final static String tag = ConcreteReceiver.class.getName();
    private final static int manufacturerIdForApple = 76;

    private final Context context;
    private final Transmitter transmitter;
    private final Handler handler;
    private final ExecutorService operationQueue = Executors.newSingleThreadExecutor();
    private final Queue<ScanResult> scanResults = new ConcurrentLinkedQueue<>();
    private final BluetoothStateManager bluetoothStateManager;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private boolean enabled = false;


    private enum ScanResultType {APPLE_FOREGROUND, APPLE_BACKGROUND, ANDROID}

    private final static class ScanResultForProcessing {
        public final ScanResultType scanResultType;
        public final ScanResult scanResult;

        public ScanResultForProcessing(ScanResultType scanResultType, ScanResult scanResult) {
            this.scanResultType = scanResultType;
            this.scanResult = scanResult;
        }
    }

    public ConcreteReceiver(final Context context, final BluetoothStateManager bluetoothStateManager, final Transmitter transmitter) {
        this.context = context;
        this.bluetoothStateManager = bluetoothStateManager;
        this.transmitter = transmitter;
        this.handler = new Handler(Looper.getMainLooper());
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        if (bluetoothLeScanner == null) {
            Logger.warn(tag, "Bluetooth LE scanner unsupported");
            return;
        }
        bluetoothStateManager.delegates.add(this);
        bluetoothStateManager(bluetoothStateManager.state());
    }

    private final void startScan() {
        if (bluetoothLeScanner == null) {
            Logger.warn(tag, "Bluetooth LE scanner unsupported");
            return;
        }
        if (scanCallback != null) {
            Logger.warn(tag, "Already started");
            return;
        }
        if (!enabled) {
            return;
        }
        operationQueue.execute(() -> {
            try {
                scanCallback = startScan(bluetoothLeScanner, scanResults);
            } catch (Throwable e) {
                Logger.warn(tag, "Start scan failed", e);
            }
        });
    }

    private final void stopScan(Consumer<Boolean> callback) {
        if (bluetoothLeScanner == null) {
            Logger.warn(tag, "Bluetooth LE scanner unsupported");
            return;
        }
        if (scanCallback == null) {
            Logger.warn(tag, "Already stopped");
            return;
        }
        if (bluetoothStateManager.state() == BluetoothState.poweredOff) {
            Logger.warn(tag, "Bluetooth is powered off");
            return;
        }
        try {
            bluetoothLeScanner.stopScan(scanCallback);
            scanCallback = null;
            operationQueue.execute(() -> {
                final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                bluetoothAdapter.cancelDiscovery();
                processScanResults(context, scanResults, transmitter);
                callback.accept(true);
            });
        } catch (Throwable e) {
            Logger.warn(tag, "Stop scan failed", e);
            callback.accept(false);
        }
    }

    @Override
    public void start(String source) {
        if (bluetoothLeScanner == null) {
            Logger.warn(tag, "Bluetooth LE scanner unsupported");
            return;
        }
        if (bluetoothStateManager.state() != BluetoothState.poweredOn) {
            Logger.warn(tag, "Bluetooth is not powered on");
            return;
        }
        if (scanCallback != null) {
            Logger.warn(tag, "Already started");
            return;
        }
        enabled = true;
        scan("start");
        Logger.debug(tag, "start");
    }

    @Override
    public void stop(String source) {
        enabled = false;
        stopScan(success -> {
        });
        Logger.debug(tag, "stop");
    }

    @Override
    public void scan(String source) {
        Logger.debug(tag, "scan (source={},enabled={})", source, enabled);
        if (!enabled) {
            return;
        }

        startScan();
        final int on = 4000;
        final int off = 8000;
        handler.postDelayed(() -> {
            stopScan(success -> {
                if (enabled) {
                    handler.postDelayed(() -> {
                        scan("schedule");
                    }, off);
                }
            });
        }, on);
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

    private final static ScanCallback startScan(final BluetoothLeScanner bluetoothLeScanner, final Collection<ScanResult> scanResults) {
        final long beaconServiceUUIDPrefix = Transmitter.beaconServiceUUID.getMostSignificantBits();
        final List<ScanFilter> filter = new ArrayList<>(2);
        filter.add(new ScanFilter.Builder().setManufacturerData(
                manufacturerIdForApple, new byte[0], new byte[0]).build());
        filter.add(new ScanFilter.Builder().setServiceUuid(
                new ParcelUuid(new UUID(beaconServiceUUIDPrefix, 0)),
                new ParcelUuid(new UUID(0xFFFFFFFFFFFFFFFFl, 0)))
                .build());
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(0)
                .build();
        final ScanCallback callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                // Logger.debug(tag, "Scan result (result={})", result);
                scanResults.add(result);
            }
            @Override
            public void onScanFailed(int errorCode) {
                Logger.warn(tag, "Scan failed (error={})", onScanFailedErrorCodeToString(errorCode));
            }
        };
        bluetoothLeScanner.startScan(filter, settings, callback);
        Logger.debug(tag, "Scan started (filter={},settings={})", filter, settings);
        return callback;
    }

    private final static void processScanResults(final Context context, final Queue<ScanResult> scanResults, final Transmitter transmitter) {
        final long beaconServiceUUIDPrefix = Transmitter.beaconServiceUUID.getMostSignificantBits();

        // Separate results and sort by RSSI
        final List<ScanResult> scanResultsApple = scanResults.stream()
                .filter(r -> r.getScanRecord() != null && r.getScanRecord().getManufacturerSpecificData(manufacturerIdForApple) != null)
                .collect(Collectors.toList());
        final List<ScanResultForProcessing> scanResultsAppleBackground = scanResultsApple.stream()
                .filter(r -> r.getScanRecord().getServiceUuids() == null || r.getScanRecord().getServiceUuids().size() == 0)
                .sorted((a, b) -> Integer.compare(b.getRssi(), a.getRssi()))
                .map(r -> new ScanResultForProcessing(ScanResultType.APPLE_BACKGROUND, r))
                .collect(Collectors.toList());
        final List<ScanResultForProcessing> scanResultsAppleForeground = scanResultsApple.stream()
                .filter(r -> r.getScanRecord().getServiceUuids() != null &&
                        r.getScanRecord().getServiceUuids().stream().filter(u -> u.getUuid().getMostSignificantBits() == beaconServiceUUIDPrefix).count() > 0)
                .sorted((a, b) -> Integer.compare(b.getRssi(), a.getRssi()))
                .map(r -> new ScanResultForProcessing(ScanResultType.APPLE_FOREGROUND, r))
                .collect(Collectors.toList());
        final List<ScanResultForProcessing> scanResultsAndroid = scanResults.stream()
                .filter(r -> !scanResultsApple.contains(r) && r.getScanRecord() != null && r.getScanRecord().getServiceUuids().stream().filter(u -> u.getUuid().getMostSignificantBits() == beaconServiceUUIDPrefix).count() > 0)
                .sorted((a, b) -> Integer.compare(b.getRssi(), a.getRssi()))
                .map(r -> new ScanResultForProcessing(ScanResultType.ANDROID, r))
                .collect(Collectors.toList());
        scanResults.clear();

        final Set<BluetoothDevice> devices = new HashSet<>();
        for (ScanResultForProcessing scanResultForProcessing : scanResultsAndroid) {
            if (!devices.contains(scanResultForProcessing.scanResult.getDevice())) {
                processScanResult(context, scanResultForProcessing, beaconServiceUUIDPrefix, transmitter);
                devices.add(scanResultForProcessing.scanResult.getDevice());
            }
        }
        devices.clear();
        for (ScanResultForProcessing scanResultForProcessing : scanResultsAppleForeground) {
            if (!devices.contains(scanResultForProcessing.scanResult.getDevice())) {
                processScanResult(context, scanResultForProcessing, beaconServiceUUIDPrefix, transmitter);
                devices.add(scanResultForProcessing.scanResult.getDevice());
            }
        }
        for (ScanResultForProcessing scanResultForProcessing : scanResultsAppleBackground) {
            if (!devices.contains(scanResultForProcessing.scanResult.getDevice())) {
                processScanResult(context, scanResultForProcessing, beaconServiceUUIDPrefix, transmitter);
                devices.add(scanResultForProcessing.scanResult.getDevice());
            }
        }
    }

    private final static void processScanResult(final Context context, final ScanResultForProcessing scanResultForProcessing, final long serviceId, final Transmitter transmitter) {
        Logger.debug(tag, "Processing scan result (type={},scanResult={})", scanResultForProcessing.scanResultType, scanResultForProcessing.scanResult);

        final int rssi = scanResultForProcessing.scanResult.getRssi();
        final CompletableFuture<Long> future = new CompletableFuture<>();
        final AtomicBoolean gattOpen = new AtomicBoolean(true);
        final BluetoothGattCallback callback = new BluetoothGattCallback() {
            private Long transmitterBeaconCode = null;

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                Logger.debug(tag, "GATT client connection state change (device={},status={},newState={})",
                        gatt.getDevice(), status, onConnectionStatusChangeStateToString(newState));
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (gattOpen.compareAndSet(true, false)) {
                        gatt.close();
                        Logger.debug(tag, "GATT client closed");
                    }
                    future.complete(transmitterBeaconCode);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                Logger.debug(tag, "GATT client discovered services (device={},status={})", gatt.getDevice(), status);
                for (BluetoothGattService service : gatt.getServices()) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        if (characteristic.getUuid().getMostSignificantBits() == serviceId) {
                            transmitterBeaconCode = characteristic.getUuid().getLeastSignificantBits();
                            Logger.debug(tag, "GATT client discovered C19X service (device={},transmitterBeaconCode={})", gatt.getDevice(), transmitterBeaconCode);

                            if (!transmitter.isSupported()) {
                                Logger.debug(tag, "GATT client not associated with transmitter, sending data instead");
                                final BeaconCode beaconCode = transmitter.beaconCode();
                                final ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
                                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                                byteBuffer.putLong(0, beaconCode.value);
                                byteBuffer.putInt(Long.BYTES, rssi);
                                characteristic.setValue(byteBuffer.array());
                                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                                final boolean writeInitSuccess = gatt.writeCharacteristic(characteristic);
                                Logger.debug(tag, "GATT client initiated write (device={},receiverBeaconCode={},rssi={},success={})", gatt.getDevice(), beaconCode, rssi, writeInitSuccess);
                                // GATT close on write complete
                                return;
                            } else {
                                gatt.disconnect();
                                future.complete(transmitterBeaconCode);
                            }
                        }
                    }
                }
                gatt.disconnect();
                future.complete(transmitterBeaconCode);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Logger.debug(tag, "GATT client wrote characteristic (device={},success={},status={})", gatt.getDevice(), (status == BluetoothGatt.GATT_SUCCESS), onCharacteristicWriteStatusToString(status));
                gatt.disconnect();
                future.complete(transmitterBeaconCode);
            }
        };
        final BluetoothGatt gatt = scanResultForProcessing.scanResult.getDevice().connectGatt(context, false, callback);
        try {
            final Long transmitterBeaconCode = future.get(10, TimeUnit.SECONDS);
            if (transmitterBeaconCode != null) {
                Logger.debug(tag, "Detected beacon (beaconCode={},rssi={})", transmitterBeaconCode, rssi);
                delegates.forEach(d -> d.receiver(new BeaconCode(transmitterBeaconCode.longValue()), new RSSI(rssi)));
            }
        } catch (TimeoutException e) {
            Logger.warn(tag, "GATT client timeout", e);
        } catch (Throwable e) {
            Logger.warn(tag, "GATT client exception", e);
        }
        if (gattOpen.compareAndSet(true, false)) {
            try {
                gatt.disconnect();
            } catch (Throwable e) {
            }
            gatt.close();
            Logger.debug(tag, "GATT client closed");
        }
    }

    private final static String onCharacteristicWriteStatusToString(final int status) {
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                return "GATT_SUCCESS";
            case BluetoothGatt.GATT_CONNECTION_CONGESTED:
                return "GATT_CONNECTION_CONGESTED";
            case BluetoothGatt.GATT_FAILURE:
                return "GATT_FAILURE";
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
                return "GATT_INSUFFICIENT_AUTHENTICATION";
            case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
                return "GATT_INSUFFICIENT_ENCRYPTION";
            case BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH:
                return "GATT_INVALID_ATTRIBUTE_LENGTH";
            case BluetoothGatt.GATT_INVALID_OFFSET:
                return "GATT_INVALID_OFFSET";
            case BluetoothGatt.GATT_READ_NOT_PERMITTED:
                return "GATT_READ_NOT_PERMITTED";
            case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
                return "GATT_REQUEST_NOT_SUPPORTED";
            case BluetoothGatt.GATT_SERVER:
                return "GATT_SERVER";
            case BluetoothGatt.GATT_WRITE_NOT_PERMITTED:
                return "GATT_WRITE_NOT_PERMITTED";
            default:
                return "UNKNOWN_STATUS_" + status;
        }
    }

    private final static String onScanFailedErrorCodeToString(final int errorCode) {
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                return "SCAN_FAILED_ALREADY_STARTED";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return "SCAN_FAILED_INTERNAL_ERROR";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return "SCAN_FAILED_FEATURE_UNSUPPORTED";
            default:
                return "UNKNOWN_ERROR_CODE_" + errorCode;
        }
    }

    private final static String onConnectionStatusChangeStateToString(final int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "STATE_CONNECTED";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "STATE_DISCONNECTED";
            default:
                return "UNKNOWN_STATE_" + state;
        }
    }

}
