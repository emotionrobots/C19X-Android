package org.c19x.gui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import org.c19x.C19XApplication;
import org.c19x.R;
import org.c19x.beacon.BeaconListener;
import org.c19x.data.DeviceRegistration;
import org.c19x.data.DeviceRegistrationListener;
import org.c19x.data.GlobalStatusLogListener;
import org.c19x.data.GlobalStatusLogReceiver;
import org.c19x.util.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class Welcome extends Activity {
    private final static String tag = Welcome.class.getName();

    private final static int requestBluetooth = 1;
    private final AtomicBoolean deviceRegistrationComplete = new AtomicBoolean(false);
    private final AtomicBoolean bluetoothBeaconComplete = new AtomicBoolean(false);
    private final AtomicBoolean dataDownloadComplete = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityUtil.setFullscreen(this);
        setContentView(R.layout.activity_welcome);

        // Sync time with server
        C19XApplication.getTimestamp().getTime();

        // Start device registration (which starts data download and bluetooth beacon upon successful registration)
        startDeviceRegistration();
    }

    private final void checkProgress() {
        Logger.debug(tag, "Progress (registration={},bluetooth={},data={})", deviceRegistrationComplete, bluetoothBeaconComplete, dataDownloadComplete);
        if (deviceRegistrationComplete.get() && bluetoothBeaconComplete.get() && dataDownloadComplete.get()) {
            final Button nextButton = (Button) findViewById(R.id.welcome_button_next);
            nextButton.setVisibility(View.VISIBLE);

        }
    }

    // DEVICE REGISTRATION
    // ============================================================================================
    private final void startDeviceRegistration() {
        final Activity thisActivity = this;
        final DeviceRegistrationListener deviceRegistrationListener = new DeviceRegistrationListener() {
            @Override
            public void registration(boolean success, long identifier) {
                final TextView textView = (TextView) findViewById(R.id.welcome_progress_id);
                textView.setText(success ? R.string.welcome_progress_id_done : R.string.welcome_progress_id_failed);
                textView.setTextColor(getResources().getColor(success ? R.color.colorGreen : R.color.colorRed, null));

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
                final boolean success = toVersion > fromVersion;
                final TextView textView = (TextView) findViewById(R.id.welcome_progress_data);
                textView.setText(success ? R.string.welcome_progress_data_done : R.string.welcome_progress_data_failed);
                textView.setTextColor(getResources().getColor(success ? R.color.colorGreen : R.color.colorRed, null));

                dataDownloadComplete.set(true);
                C19XApplication.getGlobalStatusLog().removeListener(this);
                checkProgress();
            }
        };

        C19XApplication.getGlobalStatusLog().addListener(globalStatusLogListener);
        new GlobalStatusLogReceiver().onReceive(null, null);
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
                final TextView textView = (TextView) findViewById(R.id.welcome_progress_beacon);
                if (!transmitter && !receiver) {
                    textView.setText(R.string.welcome_progress_beacon_failed);
                    textView.setTextColor(getResources().getColor(R.color.colorRed, null));
                } else if (transmitter && !receiver) {
                    textView.setText(R.string.welcome_progress_beacon_done);
                    textView.setTextColor(getResources().getColor(R.color.colorAmber, null));
                } else if (!transmitter && receiver) {
                    textView.setText(R.string.welcome_progress_beacon_done);
                    textView.setTextColor(getResources().getColor(R.color.colorAmber, null));
                } else {
                    textView.setText(R.string.welcome_progress_beacon_done);
                    textView.setTextColor(getResources().getColor(R.color.colorGreen, null));
                }
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
        checkBluetoothAndStartBeacon();
    }

    /**
     * Enable bluetooth on the device, and start beacon when bluetooth has been enabled.
     */
    private void checkBluetoothAndStartBeacon() {
        if (!C19XApplication.getBluetoothStateMonitor().isSupported()) {
            Logger.error(tag, "Bluetooth is not supported on this device");
            ActivityUtil.showDialog(this, R.string.dialogBluetoothUnsupported, () -> finish(), null);
            return;
        }

        // Get location access permission which is required for bluetooth scanning
        final String locationPermission = Manifest.permission.ACCESS_FINE_LOCATION;
        if (ActivityCompat.checkSelfPermission(this, locationPermission) != PackageManager.PERMISSION_GRANTED) {
            Logger.debug(tag, "Requesting access location permission");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, locationPermission)) {
                ActivityUtil.showDialog(this, R.string.dialogLocationPermissionRationale, () -> ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 1), () -> finish());
            } else {
                ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 1);
            }
        }

        // Enable bluetooth
        if (!C19XApplication.getBluetoothStateMonitor().isEnabled()) {
            Logger.debug(tag, "Enabling bluetooth");
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), requestBluetooth);
        } else {
            Logger.debug(tag, "Bluetooth already enabled");
            onActivityResult(requestBluetooth, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == requestBluetooth && resultCode != RESULT_CANCELED) {
            Logger.debug(tag, "Starting bluetooth location beacon transmitter");
            C19XApplication.getBeaconTransmitter().start(C19XApplication.getAliasIdentifier());
            Logger.debug(tag, "Starting bluetooth location beacon receiver");
            C19XApplication.getBeaconReceiver().start();
        }
    }
}
