package org.c19x.gui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import org.c19x.C19XApplication;
import org.c19x.R;
import org.c19x.beacon.BeaconListener;
import org.c19x.data.GlobalStatusLog;
import org.c19x.data.GlobalStatusLogListener;
import org.c19x.data.HealthStatus;
import org.c19x.logic.RiskAnalysisListener;
import org.c19x.util.Logger;
import org.c19x.util.bluetooth.BluetoothStateMonitorListener;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Minimalist app user interface for simplicity and usability
 */
public class MainActivity extends Activity {
    private final static String tag = MainActivity.class.getName();

    // Bluetooth state listener used by startBeacon and stopBeacon
    private BluetoothStateMonitorListener bluetoothStateMonitorListener;
    // Beacon listener used by startBeacon and stopBeacon to update UI on state change.
    private BeaconListener beaconListener;

    /**
     * Global status log listener for updating the status text on the GUI
     */
    private final GlobalStatusLogListener globalStatusLogListener = new GlobalStatusLogListener() {
        private final DateFormat dateFormat = new SimpleDateFormat("YYYYMMdd.HHmm");

        @Override
        public void updated(final long fromVersion, final long toVersion) {
            final TextView textView = (TextView) findViewById(R.id.dataVersion);
            if (toVersion == 0) {
                textView.setText(R.string.dataVersionDownloading);
                textView.setBackgroundResource(R.color.colorRed);
            } else {
                final Date date = new Date(toVersion);
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                final String version = dateFormat.format(date);
                textView.setText(version);
                textView.setBackgroundResource(R.color.colorGreen);
            }
        }
    };

    /**
     * Risk analysis listener for updating the status texts on the GUI
     */
    private final RiskAnalysisListener riskAnalysisListener = new RiskAnalysisListener() {
        @Override
        public void update(final byte contact, final byte advice) {
            setContactStatus(contact);
            setAdviceStatus(advice);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Logger.info(tag, "Starting main activity");

        // Setup app UI
        setFullscreen();
        setContentView(R.layout.activity_main);
        setDefaultState();

        // Start monitoring
        startBeacon();

        // Display risk analysis results
        C19XApplication.getRiskAnalysis().addListener(riskAnalysisListener);

        // Display global status log version update
        final GlobalStatusLog globalStatusLog = C19XApplication.getGlobalStatusLog();
        globalStatusLog.addListener(globalStatusLogListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop beacon
        stopBeacon();

        // Stop display global status log version update
        final GlobalStatusLog globalStatusLog = C19XApplication.getGlobalStatusLog();
        globalStatusLog.removeListener(globalStatusLogListener);

        // Stop display risk analysis results
        C19XApplication.getRiskAnalysis().removeListener(riskAnalysisListener);

        Logger.info(tag, "Stopped main activity");
    }


    // BEACON ======================================================================================

    /**
     * Start beacon transmitter and receiver, and register this activity as listener
     */
    private final void startBeacon() {
        if (beaconListener != null) {
            Logger.warn(tag, "Ignored call to startBeacon(), as beacon already started");
            return;
        }
        // Make this activity a listener for beacon transmitter and receiver status to update UI
        this.beaconListener = new BeaconListener() {
            @Override
            public void start() {
                update();
            }

            @Override
            public void startFailed(int errorCode) {
                update();
            }

            @Override
            public void stop() {
                update();
            }

            /**
             * Update beacon status on UI
             */
            private void update() {
                final boolean transmitter = C19XApplication.getBeaconTransmitter().isStarted();
                final boolean receiver = C19XApplication.getBeaconReceiver().isStarted();
                Logger.debug(tag, "Beacon status update (transmitter={},receiver={})", transmitter, receiver);

                final TextView textView = (TextView) findViewById(R.id.protectionStatus);
                if (!transmitter && !receiver) {
                    textView.setText(R.string.statusProtectionOptionNone);
                    textView.setBackgroundResource(R.color.colorRed);
                } else if (transmitter && !receiver) {
                    textView.setText(R.string.statusProtectionOptionOthersOnly);
                    textView.setBackgroundResource(R.color.colorAmber);
                } else if (!transmitter && receiver) {
                    textView.setText(R.string.statusProtectionOptionSelfOnly);
                    textView.setBackgroundResource(R.color.colorAmber);
                } else {
                    textView.setText(R.string.statusProtectionOptionCommunity);
                    textView.setBackgroundResource(R.color.colorGreen);
                }
            }
        };
        C19XApplication.getBeaconTransmitter().addListener(beaconListener);
        C19XApplication.getBeaconReceiver().addListener(beaconListener);

        // Use bluetooth state monitor to ensure beacon is switched on/off upon bluetooth on/off by other sources (e.g. manual control on the tablet)
        this.bluetoothStateMonitorListener = new BluetoothStateMonitorListener() {
            @Override
            public void enabled() {
                Logger.debug(tag, "Bluetooth is turned on, starting beacon");
                C19XApplication.getBeaconTransmitter().start(C19XApplication.getDeviceId().get());
                C19XApplication.getBeaconReceiver().start();
            }

            @Override
            public void disabling() {
                Logger.debug(tag, "Bluetooth is turning off, stopping beacon");
                C19XApplication.getBeaconTransmitter().stop();
                C19XApplication.getBeaconReceiver().stop();
            }
        };
        C19XApplication.getBluetoothStateMonitor().addListener(bluetoothStateMonitorListener);
        // Check permissions for bluetooth and ensure it is enabled
        checkBluetoothAndStartBeacon();
    }

    /**
     * Stop beacon transmitter and receiver, and unregister this activity as listener
     */
    private final void stopBeacon() {
        if (beaconListener == null) {
            Logger.warn(tag, "Ignored call to stopBeacon(), as beacon already stopped");
            return;
        }
        C19XApplication.getBluetoothStateMonitor().removeListener(bluetoothStateMonitorListener);
        bluetoothStateMonitorListener.disabling();
        C19XApplication.getBeaconTransmitter().removeListener(beaconListener);
        C19XApplication.getBeaconReceiver().removeListener(beaconListener);

        bluetoothStateMonitorListener = null;
        beaconListener = null;
    }

    /**
     * Enable bluetooth on the device, and start beacon when bluetooth has been enabled.
     */
    private void checkBluetoothAndStartBeacon() {
        if (!C19XApplication.getBluetoothStateMonitor().isSupported()) {
            Logger.error(tag, "Bluetooth is not supported on this device");
            showDialog(R.string.dialogBluetoothUnsupported, () -> finish(), null);
            return;
        }

        // Get location access permission which is required for bluetooth scanning
        final String locationPermission = Manifest.permission.ACCESS_FINE_LOCATION;
        if (ActivityCompat.checkSelfPermission(this, locationPermission) != PackageManager.PERMISSION_GRANTED) {
            Logger.debug(tag, "Requesting access location permission");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, locationPermission)) {
                showDialog(R.string.dialogLocationPermissionRationale, () -> ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 1), () -> finish());
            } else {
                ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 1);
            }
        }

        // Enable bluetooth
        if (!C19XApplication.getBluetoothStateMonitor().isEnabled()) {
            Logger.debug(tag, "Enabling bluetooth");
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            bluetoothStateMonitorListener.enabled();
        }
    }

    // GUI =========================================================================================

    /**
     * Set app as fullscreen app.
     */
    private final void setFullscreen() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /**
     * Set default UI state
     */
    private final void setDefaultState() {
        setHealthStatus(C19XApplication.getHealthStatus().getStatus());
        setContactStatus(HealthStatus.NO_SYMPTOM);
        setAdviceStatus(HealthStatus.STAY_AT_HOME);
    }

    /**
     * Set health status
     *
     * @param view
     */
    public void setHealthStatus(View view) {
        if (((RadioButton) view).isChecked()) {
            // Get target health status
            byte viewHealthStatus = HealthStatus.NO_SYMPTOM;
            switch (view.getId()) {
                case R.id.healthOptionNoSymptom: {
                    viewHealthStatus = HealthStatus.NO_SYMPTOM;
                    break;
                }
                case R.id.healthOptionHasSymptom: {
                    viewHealthStatus = HealthStatus.HAS_SYMPTOM;
                    break;
                }
                case R.id.healthOptionConfirmedDiagnosis: {
                    viewHealthStatus = HealthStatus.CONFIRMED_DIAGNOSIS;
                    break;
                }
                default: {
                    Logger.warn(tag, "Unknown target health status (viewId={})", view.getId());
                    break;
                }
            }

            // Post status report
            final long id = C19XApplication.getBeaconTransmitter().getId();
            final byte currentHealthStatus = C19XApplication.getHealthStatus().getStatus();
            final byte targetHealthStatus = viewHealthStatus;
            if (targetHealthStatus != currentHealthStatus) {
                Logger.debug(tag, "Self-report status update (current={},target={})", HealthStatus.toString(currentHealthStatus), HealthStatus.toString(targetHealthStatus));
                C19XApplication.getNetworkClient().postStatus(id, targetHealthStatus, success -> {
                    if (success) {
                        C19XApplication.getHealthStatus().setStatus(targetHealthStatus);
                        Logger.debug(tag, "Self-report status update successful (current={},previous={})", C19XApplication.getHealthStatus(), HealthStatus.toString(currentHealthStatus));
                    } else {
                        Logger.warn(tag, "Self-report status update failed, reverting to previous status (target={},revertingBackTo={})", HealthStatus.toString(targetHealthStatus), HealthStatus.toString(currentHealthStatus));
                        setHealthStatus(currentHealthStatus);
                    }
                });
            }
        }
    }

    /**
     * Set health status programmatically, e.g. to last status when post update fails.
     *
     * @param status
     */
    private void setHealthStatus(final byte status) {
        switch (status) {
            case HealthStatus.NO_SYMPTOM: {
                ((RadioButton) findViewById(R.id.healthOptionNoSymptom)).setChecked(true);
                C19XApplication.getHealthStatus().setStatus(HealthStatus.NO_SYMPTOM);
                break;
            }
            case HealthStatus.HAS_SYMPTOM: {
                ((RadioButton) findViewById(R.id.healthOptionHasSymptom)).setChecked(true);
                C19XApplication.getHealthStatus().setStatus(HealthStatus.HAS_SYMPTOM);
                break;
            }
            case HealthStatus.CONFIRMED_DIAGNOSIS: {
                ((RadioButton) findViewById(R.id.healthOptionConfirmedDiagnosis)).setChecked(true);
                C19XApplication.getHealthStatus().setStatus(HealthStatus.CONFIRMED_DIAGNOSIS);
                break;
            }
        }
    }

    /**
     * Set recent contact status.
     *
     * @param status
     */
    private void setContactStatus(final byte status) {
        final TextView option = (TextView) findViewById(R.id.contactOption);
        final TextView description = (TextView) findViewById(R.id.contactOptionDescription);
        switch (status) {
            case HealthStatus.NO_REPORT: {
                option.setText(R.string.contactOptionNoReport);
                option.setBackgroundResource(R.color.colorGreen);
                description.setText(R.string.contactOptionNoReportDescription);
                break;
            }
            case HealthStatus.INFECTIOUS: {
                option.setText(R.string.contactOptionInfectious);
                option.setBackgroundResource(R.color.colorRed);
                description.setText(R.string.contactOptionInfectiousDescription);
                break;
            }
        }
    }

    /**
     * Set advice status.
     *
     * @param status
     */
    private void setAdviceStatus(final byte status) {
        final TextView option = (TextView) findViewById(R.id.adviceOption);
        final TextView description = (TextView) findViewById(R.id.adviceOptionDescription);
        switch (status) {
            case HealthStatus.NO_RESTRICTION: {
                option.setText(R.string.adviceOptionNoRestriction);
                option.setBackgroundResource(R.color.colorGreen);
                description.setText(R.string.adviceOptionNoRestrictionDescription);
                break;
            }
            case HealthStatus.STAY_AT_HOME: {
                option.setText(R.string.adviceOptionStayAtHome);
                option.setBackgroundResource(R.color.colorAmber);
                description.setText(R.string.adviceOptionStayAtHomeDescription);
                break;
            }
            case HealthStatus.SELF_ISOLATION: {
                option.setText(R.string.adviceOptionSelfIsolation);
                option.setBackgroundResource(R.color.colorRed);
                description.setText(R.string.adviceOptionSelfIsolationDescription);
                break;
            }
        }
    }

    /**
     * Show dialog with OK and CANCEL buttons.
     *
     * @param messageId Message to display.
     * @param positive  Action on OK, null to exclude OK button.
     * @param negative  Action on CANCEL, null to exclude CANCEL button.
     */
    private void showDialog(final int messageId, final Runnable positive, final Runnable negative) {
        final Activity activity = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setMessage(messageId);
        if (positive != null) {
            builder = builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    positive.run();
                }
            });
        }
        if (negative != null) {
            builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    negative.run();
                }
            });
        }
        builder.setCancelable(false).show();
    }

//    /**
//     * Make this application a foreground application with an active notification. This is necessary to get round request limits.
//     *
//     * @return
//     */
//    private final Notification getNotification() {
//        NotificationChannel channel = new NotificationChannel("c19xChannel1", "C19X Channel", NotificationManager.IMPORTANCE_DEFAULT);
//        NotificationManager notificationManager = getSystemService(NotificationManager.class);
//        notificationManager.createNotificationChannel(channel);
//        Notification.Builder builder = new Notification.Builder(C19XApplication.getContext(), "c19xChannel1").setAutoCancel(true);
//        return builder.build();
//    }
//
}
