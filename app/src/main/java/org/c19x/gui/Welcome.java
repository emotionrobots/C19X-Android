package org.c19x.gui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import org.c19x.C19XApplication;
import org.c19x.R;
import org.c19x.beacon.BeaconListener;
import org.c19x.data.DeviceRegistration;
import org.c19x.data.DeviceRegistrationListener;
import org.c19x.data.GlobalStatusLogListener;
import org.c19x.data.GlobalStatusLogReceiver;
import org.c19x.network.response.NetworkResponse;
import org.c19x.util.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class Welcome extends Activity {
    private final static String tag = Welcome.class.getName();

    private final static int requestBluetooth = 1;
    private final static int requestLocation = 2;

    private final AtomicBoolean bluetoothPermissionComplete = new AtomicBoolean(false);
    private final AtomicBoolean locationPermissionComplete = new AtomicBoolean(false);
    private final AtomicBoolean deviceRegistrationComplete = new AtomicBoolean(false);
    private final AtomicBoolean bluetoothBeaconComplete = new AtomicBoolean(false);
    private final AtomicBoolean dataDownloadComplete = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityUtil.setFullscreen(this);
        setContentView(R.layout.activity_welcome);

        if (C19XApplication.getDeviceRegistration().isRegistered()) {
            // Fast track startup if device is already registered
            {
                // Device already registered
                deviceRegistrationComplete.set(true);
                final CheckBox checkBox = (CheckBox) findViewById(R.id.welcome_progress_id);
                checkBox.setChecked(true);
                checkBox.setTextColor(getResources().getColor(R.color.colorGreen, null));
            }
            {
                // Download data update in background later
                dataDownloadComplete.set(true);
            }
            startBluetoothLocationBeacon();
        } else {
            // Check connection with server
            C19XApplication.getNetworkClient().checkConnectionToServer(r -> {
                if (r.getValue()) {
                    // Sync time with server
                    C19XApplication.getTimestamp().getTime();
                    // Start device registration (which starts data download and bluetooth beacon upon successful registration)
                    startDeviceRegistration();
                } else {
                    tryAgainLater(r.getNetworkResponse());
                }
            });
        }
    }

    /**
     *
     */
    private void tryAgainLater(final NetworkResponse networkResponse) {
        if (networkResponse == NetworkResponse.NO_CONNECTION_ERROR) {
            ActivityUtil.showDialog(this,
                    R.string.dialog_welcome_tryAgainLater_noConnection_title,
                    R.string.dialog_welcome_tryAgainLater_noConnection_message,
                    () -> finish(), null);
        } else {
            ActivityUtil.showDialog(this,
                    R.string.dialog_welcome_tryAgainLater_serverBusy_title,
                    R.string.dialog_welcome_tryAgainLater_serverBusy_message,
                    () -> finish(), null);
        }
    }

    /**
     * Check overall progress, then move forward to main activity
     */
    private final synchronized void checkProgress() {
        Logger.debug(tag, "Progress (registration={},bluetooth={},data={},locationPermission={},bluetoothPermission={})", deviceRegistrationComplete, bluetoothBeaconComplete, dataDownloadComplete, locationPermissionComplete, bluetoothPermissionComplete);
        if (deviceRegistrationComplete.get() && bluetoothBeaconComplete.get() && dataDownloadComplete.get() && locationPermissionComplete.get() && bluetoothPermissionComplete.get()) {
            final Intent intent = new Intent(this, MainActivity.class);
            // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }

    // DEVICE REGISTRATION
    // ============================================================================================
    private final void startDeviceRegistration() {
        final Activity thisActivity = this;
        final DeviceRegistrationListener deviceRegistrationListener = new DeviceRegistrationListener() {
            @Override
            public void registration(boolean success, long identifier) {
                final CheckBox checkBox = (CheckBox) findViewById(R.id.welcome_progress_id);
                checkBox.setChecked(success);
                checkBox.setTextColor(getResources().getColor(success ? R.color.colorGreen : R.color.colorRed, null));

                // Start data download and bluetooth location beacon after registration
                startDataDownload();
                startBluetoothLocationBeacon();

                deviceRegistrationComplete.set(true);
                C19XApplication.getDeviceRegistration().removeListener(this);
                checkProgress();
            }
        };

        final DeviceRegistration deviceRegistration = C19XApplication.getDeviceRegistration();
        deviceRegistration.addListener(deviceRegistrationListener);
        if (!deviceRegistration.isRegistered()) {
            Logger.info(tag, "Device registration required");
            new Thread(() -> deviceRegistration.register()).start();
        } else {
            Logger.info(tag, "Device already registered");
            deviceRegistrationListener.registration(true, deviceRegistration.getIdentifier());
        }
    }

    // DOWNLOAD DATA
    // ============================================================================================
    private final void startDataDownload() {
        final GlobalStatusLogListener globalStatusLogListener = new GlobalStatusLogListener() {
            @Override
            public void updated(long fromVersion, long toVersion) {
                Logger.debug(tag, "Global status log updated (version={})", toVersion);
                final boolean success = toVersion > 0;
                final CheckBox checkBox = (CheckBox) findViewById(R.id.welcome_progress_data);
                checkBox.setChecked(success);
                checkBox.setTextColor(getResources().getColor(success ? R.color.colorGreen : R.color.colorRed, null));

                dataDownloadComplete.set(true);
                C19XApplication.getGlobalStatusLog().removeListener(this);
                checkProgress();
            }
        };

        C19XApplication.getGlobalStatusLog().addListener(globalStatusLogListener);
        final long timestamp = C19XApplication.getGlobalStatusLog().getTimestamp();
        if (timestamp == 0) {
            new GlobalStatusLogReceiver().onReceive(null, null);
        } else {
            globalStatusLogListener.updated(timestamp, timestamp);
        }
    }

    // BLUETOOTH BEACON
    // ============================================================================================

    /**
     * Start bluetooth location beacon transmitter and receiver
     */
    private final void startBluetoothLocationBeacon() {
        final AtomicBoolean transmitterStarted = new AtomicBoolean(false);
        final AtomicBoolean transmitterChecked = new AtomicBoolean(false);
        final AtomicBoolean receiverStarted = new AtomicBoolean(false);
        final AtomicBoolean receiverChecked = new AtomicBoolean(false);

        // Update GUI
        final Runnable uiUpdate = new Runnable() {
            @Override
            public void run() {
                final boolean transmitter = transmitterStarted.get();
                final boolean receiver = receiverStarted.get();
                Logger.debug(tag, "Beacon status update (transmitter={},receiver={})", transmitter, receiver);
                final CheckBox checkBox = (CheckBox) findViewById(R.id.welcome_progress_beacon);
                checkBox.setChecked(transmitter || receiver);
                int statusColor = R.color.colorRed;
                if (transmitter || receiver) {
                    statusColor = R.color.colorAmber;
                }
                if (transmitter && receiver) {
                    statusColor = R.color.colorGreen;
                }
                checkBox.setTextColor(getResources().getColor(statusColor, null));
                if (transmitterChecked.get() && receiverChecked.get()) {
                    Logger.debug(tag, "Beacon transmitter and receiver check completed (transmitter={},receiver={})", transmitter, receiver);
                    bluetoothBeaconComplete.set(true);
                    checkProgress();
                }
            }
        };

        final BeaconListener transmitterListener = new BeaconListener() {
            @Override
            public void start() {
                transmitterStarted.set(true);
                checked();
            }

            @Override
            public void startFailed(int errorCode) {
                transmitterStarted.set(false);
                checked();
            }

            private final void checked() {
                C19XApplication.getBeaconTransmitter().removeListener(this);
                transmitterChecked.set(true);
                uiUpdate.run();
            }
        };

        final BeaconListener receiverListener = new BeaconListener() {
            @Override
            public void start() {
                receiverStarted.set(true);
                checked();
            }

            @Override
            public void startFailed(int errorCode) {
                receiverStarted.set(false);
                checked();
            }

            private final void checked() {
                C19XApplication.getBeaconReceiver().removeListener(this);
                receiverChecked.set(true);
                uiUpdate.run();
            }
        };

        C19XApplication.getBeaconTransmitter().addListener(transmitterListener);
        C19XApplication.getBeaconReceiver().addListener(receiverListener);
        checkBluetoothAndLocationPermissions();
    }

    /**
     * Enable bluetooth on the device, and start beacon when bluetooth has been enabled.
     */
    private void checkBluetoothAndLocationPermissions() {
        if (!C19XApplication.getBluetoothStateMonitor().isSupported()) {
            Logger.error(tag, "Bluetooth is not supported on this device");
            ActivityUtil.showDialog(this,
                    R.string.dialog_welcome_permission_bluetooth_title,
                    R.string.dialog_welcome_hardware_bluetooth_description,
                    () -> finish(), null);
            return;
        }

        // Get location access permission which is required for bluetooth scanning
        final String locationPermission = Manifest.permission.ACCESS_FINE_LOCATION;
        if (ActivityCompat.checkSelfPermission(this, locationPermission) != PackageManager.PERMISSION_GRANTED) {
            Logger.debug(tag, "Requesting access location permission");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, locationPermission)) {
                ActivityUtil.showDialog(this,
                        R.string.dialog_welcome_permission_location_title,
                        R.string.dialog_welcome_permission_location_rationale,
                        () -> ActivityCompat.requestPermissions(this, new String[]{locationPermission}, requestLocation), () -> finish());
            } else {
                ActivityCompat.requestPermissions(this, new String[]{locationPermission}, requestLocation);
            }
        } else {
            locationPermissionComplete.set(true);
        }

        // Enable bluetooth
        if (!C19XApplication.getBluetoothStateMonitor().isEnabled()) {
            Logger.debug(tag, "Enabling bluetooth");
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), requestBluetooth);
        } else {
            bluetoothPermissionComplete.set(true);
        }

        // Check permissions and start bluetooth beacon
        checkPermissionRequestResult();
    }

    private synchronized void checkPermissionRequestResult() {
        if (locationPermissionComplete.get() && bluetoothPermissionComplete.get()) {
            if (!C19XApplication.getBeaconTransmitter().isStarted()) {
                Logger.debug(tag, "Starting bluetooth location beacon transmitter");
                C19XApplication.getBeaconTransmitter().start(C19XApplication.getAliasIdentifier());
            }
            if (!C19XApplication.getBeaconReceiver().isStarted()) {
                Logger.debug(tag, "Starting bluetooth location beacon receiver");
                C19XApplication.getBeaconReceiver().start();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Logger.debug(tag, "On request permission result (requestCode={},resultCode={})", requestCode, grantResults[0]);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == requestLocation) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionComplete.set(true);
                checkPermissionRequestResult();
                checkProgress();
            } else {
                ActivityUtil.showDialog(this,
                        R.string.dialog_welcome_permission_location_title,
                        R.string.dialog_welcome_permission_location_message, () -> finish(), null);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Logger.debug(tag, "On activity result (requestCode={},resultCode={})", requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == requestBluetooth) {
            if (resultCode != RESULT_CANCELED) {
                bluetoothPermissionComplete.set(true);
                checkPermissionRequestResult();
                checkProgress();
            } else {
                ActivityUtil.showDialog(this,
                        R.string.dialog_welcome_permission_bluetooth_title,
                        R.string.dialog_welcome_permission_bluetooth_message, () -> finish(), null);
            }
        }
    }
}
