package org.c19x.gui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import org.c19x.service.BeaconService;
import org.c19x.util.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class Welcome extends Activity {
    private final static String tag = Welcome.class.getName();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final static int requestBluetooth = 1;
    private final static int requestLocation = 2;

    private final AtomicBoolean bluetoothPermissionComplete = new AtomicBoolean(false);
    private final AtomicBoolean locationPermissionComplete = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        C19XApplication.registerOnCreate(this);

        ActivityUtil.setFullscreen(this);
        setContentView(R.layout.activity_welcome);

        ActivityUtil.showDialog(this, R.string.trial_title, R.string.trial_description,
                () -> startApp(),
                () -> finish());

    }

    private final void startApp() {
        final Runnable checkProgressTask = new Runnable() {
            @Override
            public void run() {
                if (!checkProgress()) {
                    handler.postDelayed(this, 1000);
                }
            }
        };
        checkProgressTask.run();

        if (C19XApplication.getDeviceRegistration().isRegistered()) {
            // Fast track startup if device is already registered
            final CheckBox checkBox = (CheckBox) findViewById(R.id.welcome_progress_id);
            checkBox.setChecked(true);
            checkBox.setTextColor(getResources().getColor(R.color.colorGreen, null));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        C19XApplication.unregisterOnDestroy(this);
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
    private final synchronized boolean checkProgress() {
        final boolean deviceRegistrationComplete =
                C19XApplication.getDeviceRegistration().isRegistered();
        final boolean bluetoothBeaconComplete =
                (!C19XApplication.getBeaconTransmitter().isSupported() || (C19XApplication.getBeaconTransmitter().isSupported() && C19XApplication.getBeaconTransmitter().isStarted())) &&
                        (!C19XApplication.getBeaconReceiver().isSupported() || (C19XApplication.getBeaconReceiver().isSupported() && C19XApplication.getBeaconReceiver().isStarted()));
        final boolean dataDownloadComplete =
                C19XApplication.getGlobalStatusLog().getTimestamp() > 0;

        Logger.debug(tag, "Progress (registration={},bluetooth={}[TX={}|{},RX={}|{}],data={},locationPermission={},bluetoothPermission={})",
                deviceRegistrationComplete,
                bluetoothBeaconComplete,
                C19XApplication.getBeaconTransmitter().isSupported(),
                C19XApplication.getBeaconTransmitter().isStarted(),
                C19XApplication.getBeaconReceiver().isSupported(),
                C19XApplication.getBeaconReceiver().isStarted(),
                dataDownloadComplete, locationPermissionComplete, bluetoothPermissionComplete);
        if (deviceRegistrationComplete &&
                bluetoothBeaconComplete &&
                dataDownloadComplete &&
                locationPermissionComplete.get() &&
                bluetoothPermissionComplete.get()) {
            final Intent intent = new Intent(this, MainActivity.class);
            // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return true;
        }
        return false;
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

                C19XApplication.getDeviceRegistration().removeListener(this);
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
                C19XApplication.getGlobalStatusLog().removeListener(this);
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
        // Update GUI
        final Runnable uiUpdate = new Runnable() {
            @Override
            public synchronized void run() {
                final boolean transmitter = C19XApplication.getBeaconTransmitter().isStarted();
                final boolean receiver = C19XApplication.getBeaconReceiver().isStarted();
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
            }
        };

        final BeaconListener beaconListener = new BeaconListener() {
            @Override
            public void start() {
                uiUpdate.run();
            }

            @Override
            public void error(int errorCode) {
                uiUpdate.run();
            }

            @Override
            public void stop() {
                uiUpdate.run();
            }

            @Override
            public void detect(long timestamp, long id, float rssi) {
                uiUpdate.run();
            }
        };

        C19XApplication.getBeaconTransmitter().addListener(beaconListener);
        C19XApplication.getBeaconReceiver().addListener(beaconListener);
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
            // Start bluetooth as service
            Logger.info(tag, "Starting beacon service");
            final Intent intent = new Intent(this, BeaconService.class);
            intent.putExtra(BeaconService.keyId, C19XApplication.getAliasIdentifier());
            intent.putExtra(BeaconService.keyOnDuration, C19XApplication.getGlobalStatusLog().getBeaconReceiverOnDuration());
            intent.putExtra(BeaconService.keyOffDuration, C19XApplication.getGlobalStatusLog().getBeaconReceiverOffDuration());
            intent.putExtra(BeaconService.keyActive, true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
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
            } else {
                ActivityUtil.showDialog(this,
                        R.string.dialog_welcome_permission_bluetooth_title,
                        R.string.dialog_welcome_permission_bluetooth_message, () -> finish(), null);
            }
        }
    }
}
