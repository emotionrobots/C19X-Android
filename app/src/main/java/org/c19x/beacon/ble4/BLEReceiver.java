package org.c19x.beacon.ble4;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import org.c19x.C19XApplication;
import org.c19x.beacon.BeaconListener;
import org.c19x.beacon.BeaconReceiver;
import org.c19x.util.FlipFlopTimer;
import org.c19x.util.Logger;
import org.c19x.util.messaging.Broadcaster;
import org.c19x.util.messaging.DefaultBroadcaster;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class BLEReceiver extends DefaultBroadcaster<BeaconListener> implements BeaconReceiver {
    private final static String tag = BLEReceiver.class.getName();
    private final static int manufacturerIdForApple = 76;
    private final BLEReceiver self = this;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<ScanResult> scanResults = new ConcurrentLinkedQueue<>();
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            scanResults.add(result);
        }

        @Override
        public void onBatchScanResults(@NonNull List<ScanResult> results) {
            scanResults.addAll(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            if (errorCode != SCAN_FAILED_ALREADY_STARTED) {
                started.set(false);
            }
        }
    };
    private final List<ScanFilter> scanFilters = Arrays.asList(
            new ScanFilter.Builder().setManufacturerData(manufacturerIdForApple, new byte[0], new byte[0]).build(),
            new ScanFilter.Builder().setServiceUuid(new ParcelUuid(new UUID(BLETransmitter.bleServiceId, 0))).build()
    );
    private final FlipFlopTimer flipFlopTimer = new FlipFlopTimer(5000, 5000, () -> startScan(scanFilters, scanCallback), () -> stopScan(scanCallback, scanResults, self));


    private final static class ServerConnection extends BleManager {
        private final BluetoothDevice bluetoothDevice;
        private final Broadcaster<BeaconListener> broadcaster;
        private final long beaconCode;
        private final int rssi;
        private final byte[] value;

        public ServerConnection(@NonNull Context context, final BluetoothDevice bluetoothDevice, final long beaconCode, final int rssi, final Broadcaster<BeaconListener> broadcaster) {
            super(context);
            this.bluetoothDevice = bluetoothDevice;
            this.beaconCode = beaconCode;
            this.rssi = rssi;
            this.broadcaster = broadcaster;
            final ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.putLong(0, beaconCode);
            byteBuffer.putInt(Long.BYTES, rssi);
            this.value = byteBuffer.array();
        }

        @NonNull
        @Override
        protected BleManager.BleManagerGattCallback getGattCallback() {
            return new BleManagerGattCallback() {
                private BluetoothGattCharacteristic c19xCharacteristic = null;
                private Long transmitterBeaconCode = null;

                @Override
                protected boolean isRequiredServiceSupported(@NonNull BluetoothGatt gatt) {
                    for (final BluetoothGattService service : gatt.getServices()) {
                        for (final BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            if (characteristic.getUuid().getMostSignificantBits() == BLETransmitter.bleServiceId) {
                                c19xCharacteristic = characteristic;
                                c19xCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                                transmitterBeaconCode = characteristic.getUuid().getLeastSignificantBits();
                                Logger.debug(tag, "GATT client found C19X service (device={},transmitter={})", gatt.getDevice(), transmitterBeaconCode);
                                return true;
                            }
                        }
                    }
                    return false;
                }

                @Override
                protected void initialize() {
                    super.initialize();
                    if (isConnected()) {
                        Logger.debug(tag, "GATT client write data (device={},transmitter={},receiver={},rssi={})", bluetoothDevice, transmitterBeaconCode, beaconCode, rssi);
                        writeCharacteristic(c19xCharacteristic, value)
                                .done(d -> Logger.debug(tag, "GATT client wrote data (device={},value={})", d, Arrays.toString(value)))
                                .fail((d, s) -> Logger.debug(tag, "GATT client write failed (device={},status={})", d, s))
                                .enqueue();
                        if (transmitterBeaconCode != null) {
                            final long timestamp = C19XApplication.getTimestamp().getTime();
                            broadcaster.broadcast(l -> l.detect(timestamp, transmitterBeaconCode, rssi));
                        }
                    }
                }

                @Override
                protected void onDeviceReady() {
                    super.onDeviceReady();
                    Logger.debug(tag, "GATT client ready (device={})", bluetoothDevice);
                }

                @Override
                protected void onDeviceDisconnected() {
                    Logger.debug(tag, "GATT client disconnected (device={})", bluetoothDevice);
                    c19xCharacteristic = null;
                    transmitterBeaconCode = null;
                }
            };
        }
    }

    private final static void writeToServer(final Context context, final BluetoothDevice device, final long beaconCode, final int rssi, final Broadcaster<BeaconListener> broadcaster) {
        final ServerConnection serverConnection = new ServerConnection(context, device, beaconCode, rssi, broadcaster);
        serverConnection.connect(device)
                .timeout(10000)
                .done(d -> Logger.debug(tag, "GATT client connected (device={})", d))
                .fail((d, s) -> Logger.warn(tag, "GATT client connect failed (device={},status={})", d, s))
                .enqueue();
    }

    private final static void startScan(final List<ScanFilter> scanFilters, final ScanCallback scanCallback) {
        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        final ScanSettings settings = new ScanSettings.Builder()
                .setLegacy(false)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(1000)
                .setUseHardwareBatchingIfSupported(true)
                .build();
        scanner.startScan(scanFilters, settings, scanCallback);
        Logger.debug(tag, "Scan started");
    }

    private final static void stopScan(final ScanCallback scanCallback, final ConcurrentLinkedQueue<ScanResult> scanResults, final Broadcaster<BeaconListener> broadcaster) {
        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        scanner.stopScan(scanCallback);
        Logger.debug(tag, "Scan stopped");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }
        processScanResults(scanResults, BLETransmitter.bleServiceId, C19XApplication.getBeaconTransmitter().getId(), broadcaster);
    }


    private final static void processScanResults(final ConcurrentLinkedQueue<ScanResult> scanResults, final long serviceId, final long beaconCode, final Broadcaster<BeaconListener> broadcaster) {
        // Separate results and sort by RSSI
        final List<ScanResult> scanResultsApple = scanResults.stream()
                .filter(r -> r.getScanRecord() != null && r.getScanRecord().getManufacturerSpecificData(manufacturerIdForApple) != null)
                .collect(Collectors.toList());
        final List<ScanResult> scanResultsAppleBackground = scanResultsApple.stream()
                .filter(r -> r.getScanRecord().getServiceUuids() == null || r.getScanRecord().getServiceUuids().size() == 0)
                .sorted((a, b) -> Integer.compare(b.getRssi(), a.getRssi()))
                .collect(Collectors.toList());
        final List<ScanResult> scanResultsAppleForeground = scanResultsApple.stream()
                .filter(r -> r.getScanRecord().getServiceUuids() != null &&
                        r.getScanRecord().getServiceUuids().stream().filter(u -> u.getUuid().getMostSignificantBits() == serviceId).count() > 0)
                .sorted((a, b) -> Integer.compare(b.getRssi(), a.getRssi()))
                .collect(Collectors.toList());
        final List<ScanResult> scanResultsAndroid = scanResults.stream()
                .filter(r -> !scanResultsApple.contains(r) && r.getScanRecord() != null && r.getScanRecord().getServiceUuids().stream().filter(u -> u.getUuid().getMostSignificantBits() == serviceId).count() > 0)
                .sorted((a, b) -> Integer.compare(b.getRssi(), a.getRssi()))
                .collect(Collectors.toList());


        final Set<BluetoothDevice> devices = new HashSet<>();
        for (ScanResult scanResult : scanResultsAndroid) {
            if (!devices.contains(scanResult.getDevice())) {
                processScanResult("ANDROID", scanResult, beaconCode, broadcaster);
                devices.add(scanResult.getDevice());
            }
        }
        devices.clear();
        for (ScanResult scanResult : scanResultsAppleForeground) {
            if (!devices.contains(scanResult.getDevice())) {
                processScanResult("APPLE_FOREGROUND", scanResult, beaconCode, broadcaster);
                devices.add(scanResult.getDevice());
            }
        }
        for (ScanResult scanResult : scanResultsAppleBackground) {
            if (!devices.contains(scanResult.getDevice())) {
                processScanResult("APPLE_BACKGROUND", scanResult, beaconCode, broadcaster);
                devices.add(scanResult.getDevice());
            }
        }

        scanResults.clear();
    }

    private final static void processScanResult(final String type, final ScanResult scanResult, final long beaconCode, final Broadcaster<BeaconListener> broadcaster) {
        Logger.debug(tag, "Processing scan result (type={},scanResult={})", type, scanResult);
        writeToServer(C19XApplication.getContext(), scanResult.getDevice(), beaconCode, scanResult.getRssi(), broadcaster);
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            flipFlopTimer.start();
        }
    }

    @Override
    public void stop() {
        if (started.get()) {
            flipFlopTimer.stop();
            started.set(false);
        }
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isSupported() {
        return BluetoothLeScannerCompat.getScanner() != null;
    }

    @Override
    public void setDutyCycle(int onDuration, int offDuration) {
//        flipFlopTimer.setOnDuration(onDuration);
//        flipFlopTimer.setOffDuration(offDuration);
    }
}
