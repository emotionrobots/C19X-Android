package org.c19x.beacon.ble;

import android.bluetooth.BluetoothAdapter;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(0)
            .build();
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothLeScanner;
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            final long timestamp = C19XApplication.getTimestamp().getTime();
            final int rssi = result.getRssi();
            final List<ParcelUuid> parcelUuids = result.getScanRecord().getServiceUuids();
            Logger.debug(tag, "Beacon receiver found transmitter (timestamp={},rssi={},uuids={})", timestamp, rssi, parcelUuids);
            for (final Long id : getDeviceIds(parcelUuids)) {
                Logger.debug(tag, "Beacon receiver found transmitter id (timestamp={},id={},rssi={})", timestamp, id, rssi);
                broadcast(l -> l.detect(timestamp, id, rssi));
            }
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
            started = false;
        }
    };
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
        this.flipFlopTimer = new FlipFlopTimer(4000, 4000,
                () -> {
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                        try {
                            bluetoothLeScanner.startScan(bluetoothLeScanFilter, bluetoothLeScanSettings, scanCallback);
                        } catch (Throwable e) {
                            Logger.warn(tag, "Beacon receiver start exception", e);
                        }
                    }
                },
                () -> {
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                        try {
                            bluetoothLeScanner.stopScan(scanCallback);
                        } catch (Throwable e) {
                            Logger.warn(tag, "Beacon receiver stop exception", e);
                        }
                    }
                });
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
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                flipFlopTimer.stop();
            } else {
                Logger.warn(tag, "Beacon receiver stop failed (adapter={},enabled={})", bluetoothAdapter != null, (bluetoothAdapter != null && bluetoothAdapter.isEnabled()));
            }
            started = false;
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
