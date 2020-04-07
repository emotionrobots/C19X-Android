package org.c19x.beacon.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.ParcelUuid;

import org.c19x.C19XApplication;
import org.c19x.util.Logger;

import java.util.UUID;

/**
 * Bluetooth LE beacon for broadcasting an identifier to enable tracing. The identifier is encoded
 * into the lower 64-bit of the UUID.
 */
public class BluetoothLEBeaconService extends AbstractBeaconService {
    private final static String tag = BluetoothLEBeaconService.class.getName();
    private final AdvertiseSettings advertiseSettings;
    private final AdvertiseData advertiseData;
    private final AdvertiseData.Builder scanReponseDataBuilder;
    private final AdvertiseCallback advertiseCallback;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private AdvertiseData scanResponse;
    private UUID uuid;
    private long deviceId = 0;

    public BluetoothLEBeaconService() {
        this.advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(false)
                .build();
        this.advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(true)
                .build();
        this.scanReponseDataBuilder = new AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true);
        this.advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Logger.debug(tag, "Beacon started successfully");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Logger.debug(tag, "Beacon failed to start (error={})", errorCode);
            }
        };
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            Logger.debug(tag, "Bluetooth adapter found");
            if (bluetoothAdapter.isMultipleAdvertisementSupported()) {
                Logger.debug(tag, "Bluetooth LE advertising is supported");
                this.bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                if (this.bluetoothLeAdvertiser != null) {
                    Logger.debug(tag, "Bluetooth LE advertiser found");
                } else {
                    Logger.error(tag, "Bluetooth LE advertiser not found");
                }
            } else {
                Logger.error(tag, "Bluetooth LE advertising is not supported");
            }
        } else {
            Logger.error(tag, "Bluetooth is not supported on this device");
        }
    }

    /**
     * Set beacon id which is the lower 64-bit of the service UUID.
     *
     * @param id
     */
    private void setId(final long id) {
        this.deviceId = id;
        this.uuid = new UUID(C19XApplication.bluetoothLeServiceId, deviceId);
        this.scanResponse = scanReponseDataBuilder
                .addServiceUuid(new ParcelUuid(uuid))
                .build();
    }

    @Override
    public void startService(final long id) {
        setId(id);
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, scanResponse, advertiseCallback);
        }
    }

    /**
     * Stop advertising beacon.
     */
    @Override
    public void stopService() {
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        }
    }
}
