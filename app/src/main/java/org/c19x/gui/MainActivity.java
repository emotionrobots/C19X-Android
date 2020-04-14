package org.c19x.gui;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import org.c19x.C19XApplication;
import org.c19x.R;
import org.c19x.beacon.BeaconListener;
import org.c19x.data.GlobalStatusLog;
import org.c19x.data.GlobalStatusLogListener;
import org.c19x.data.GlobalStatusLogReceiver;
import org.c19x.data.HealthStatus;
import org.c19x.logic.RiskAnalysisListener;
import org.c19x.logic.RiskFactors;
import org.c19x.util.Logger;
import org.c19x.util.bluetooth.BluetoothStateMonitorListener;

/**
 * Minimalist app user interface for simplicity and usability
 */
public class MainActivity extends Activity {
    private final static String tag = MainActivity.class.getName();
    private final static int notificationChannelId = 1;

    // Bluetooth state listener used by startBeacon and stopBeacon
    private BluetoothStateMonitorListener bluetoothStateMonitorListener = new BluetoothStateMonitorListener() {

        @Override
        public void enabled() {
            Logger.debug(tag, "Bluetooth is turned on, starting beacon");
            if (C19XApplication.getDeviceRegistration().isRegistered()) {
                C19XApplication.getBeaconTransmitter().start(C19XApplication.getAliasIdentifier());
            }
            C19XApplication.getBeaconReceiver().start();
        }

        @Override
        public void disabling() {
            Logger.debug(tag, "Bluetooth is turning off, stopping beacon");
            C19XApplication.getBeaconTransmitter().stop();
            C19XApplication.getBeaconReceiver().stop();
        }
    };

    // Beacon listener used by startBeacon and stopBeacon to update UI on state change.
    private BeaconListener beaconListener;

    /**
     * Global status log listener for updating the status text on the GUI
     */
    private final GlobalStatusLogListener globalStatusLogListener = new GlobalStatusLogListener() {
        private final long dayMillis = 24 * 60 * 60 * 1000;

        @Override
        public void updated(final long fromVersion, final long toVersion) {
            final TextView textView = (TextView) findViewById(R.id.dataUpdate);
            final long currentTime = C19XApplication.getTimestamp().getTime();
            final long delta = (currentTime - toVersion) / dayMillis;
            Logger.debug(tag, "Global status log updated (time={},toVersion={},delta={})", currentTime, toVersion, delta);
            if (delta >= C19XApplication.getGlobalStatusLog().getRetentionPeriod()) {
                textView.setText(R.string.status_data_update_download);
                textView.setBackgroundResource(R.color.colorRed);
            } else if (delta == 0) {
                textView.setText(R.string.status_data_update_latest);
                textView.setBackgroundResource(R.color.colorGreen);
            } else if (delta == 1) {
                textView.setText("Updated 1 day ago");
                textView.setBackgroundResource(R.color.colorAmber);
            } else {
                textView.setText("Updated " + delta + " day ago");
                textView.setBackgroundResource(R.color.colorAmber);
            }
        }
    };

    /**
     * Risk analysis listener for updating the status texts on the GUI
     */
    private final RiskAnalysisListener riskAnalysisListener = new RiskAnalysisListener() {
        @Override
        public void update(final RiskFactors riskFactors, final byte contact, final byte advice) {
            setContactStatus(contact);
            setAdviceStatus(advice);
            setContactDuration(riskFactors);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Logger.info(tag, "Starting main activity");

        // Setup app UI
        ActivityUtil.setFullscreen(this);
        setContentView(R.layout.activity_main);
        setDefaultState();

        // Start monitoring bluetooth state
        startBluetoothStateMonitor();

        // Display risk analysis results
        C19XApplication.getRiskAnalysis().addListener(riskAnalysisListener);

        // Display global status log version update
        final GlobalStatusLog globalStatusLog = C19XApplication.getGlobalStatusLog();
        globalStatusLog.addListener(globalStatusLogListener);
        C19XApplication.startGlobalStatusLogAutomaticUpdate();
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
    private final void startBluetoothStateMonitor() {
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

                final TextView textView = (TextView) findViewById(R.id.beaconStatus);
                if (!transmitter && !receiver) {
                    textView.setText(R.string.status_beacon_off);
                    textView.setBackgroundResource(R.color.colorRed);
                } else if (transmitter && !receiver) {
                    textView.setText(R.string.status_beacon_transmitter);
                    textView.setBackgroundResource(R.color.colorAmber);
                } else if (!transmitter && receiver) {
                    textView.setText(R.string.status_beacon_receiver);
                    textView.setBackgroundResource(R.color.colorAmber);
                } else {
                    textView.setText(R.string.status_beacon_on);
                    textView.setBackgroundResource(R.color.colorGreen);
                }
            }
        };
        C19XApplication.getBeaconTransmitter().addListener(beaconListener);
        C19XApplication.getBeaconReceiver().addListener(beaconListener);
        C19XApplication.getBluetoothStateMonitor().addListener(bluetoothStateMonitorListener);
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

    // GUI =========================================================================================

    /**
     * Set default UI state
     */
    private final void setDefaultState() {
        setHealthStatus(C19XApplication.getHealthStatus().getStatus());
        setContactStatus(C19XApplication.getRiskAnalysis().getContact());
        setAdviceStatus(C19XApplication.getRiskAnalysis().getAdvice());
    }

    /**
     * Update beacon identity now, triggered from GUI
     *
     * @param view
     */
    public void updateBeacon(View view) {
        Logger.debug(tag, "Beacon transmitter change identity requested manually");
        if (C19XApplication.getBeaconTransmitter().isStarted() && C19XApplication.getDeviceRegistration().isRegistered()) {
            C19XApplication.getBeaconTransmitter().stop();
            C19XApplication.getBeaconTransmitter().start(C19XApplication.getAliasIdentifier());
        }
    }

    /**
     * Update close contact data now, triggered from GUI
     *
     * @param view
     */
    public void updateContactDuration(View view) {
        Logger.debug(tag, "Contact duration update requested manually");
        C19XApplication.getRiskAnalysis().updateAssessment();
    }


    /**
     * Update global status log data now, triggered from GUI
     *
     * @param view
     */
    public void updateData(View view) {
        Logger.debug(tag, "Global status log update requested manually");
        new GlobalStatusLogReceiver().onReceive(null, null);
    }

    public void onClickContactStatus(View view) {
        switch (C19XApplication.getRiskAnalysis().getContact()) {
            case HealthStatus.NO_REPORT:
                ActivityUtil.showDialog(this, R.string.contact_status_option_no_report, R.string.contact_status_option_no_report_description, () -> {
                }, null);
                break;
            case HealthStatus.INFECTIOUS:
                ActivityUtil.showDialog(this, R.string.contact_status_option_infectious, R.string.contact_status_option_infectious_description, () -> {
                }, null);
                break;
        }
    }

    public void onClickAdviceStatus(View view) {
        switch (C19XApplication.getRiskAnalysis().getAdvice()) {
            case HealthStatus.NO_RESTRICTION:
                ActivityUtil.showDialog(this, R.string.advice_status_option_no_restriction, R.string.advice_status_option_no_restriction_description, () -> {
                }, null);
                break;
            case HealthStatus.STAY_AT_HOME:
                ActivityUtil.showDialog(this, R.string.advice_status_option_stay_at_home, R.string.advice_status_option_stay_at_home_description, () -> {
                }, null);
                break;
            case HealthStatus.SELF_ISOLATION:
                ActivityUtil.showDialog(this, R.string.advice_status_option_self_isolation, R.string.advice_status_option_self_isolation_description, () -> {
                }, null);
                break;
        }
    }

    /**
     * Set health status from GUI
     *
     * @param view
     */
    public void setHealthStatus(View view) {
        if (((RadioButton) view).isChecked()) {
            // Get target health status
            byte viewHealthStatus = HealthStatus.NO_SYMPTOM;
            switch (view.getId()) {
                case R.id.healthStatusOptionNoSymptom: {
                    viewHealthStatus = HealthStatus.NO_SYMPTOM;
                    break;
                }
                case R.id.healthStatusOptionHasSymptom: {
                    viewHealthStatus = HealthStatus.HAS_SYMPTOM;
                    break;
                }
                case R.id.healthStatusOptionConfirmedDiagnosis: {
                    viewHealthStatus = HealthStatus.CONFIRMED_DIAGNOSIS;
                    break;
                }
                default: {
                    Logger.warn(tag, "Unknown target health status (viewId={})", view.getId());
                    break;
                }
            }

            // Post status report
            final byte currentHealthStatus = C19XApplication.getHealthStatus().getStatus();
            final byte targetHealthStatus = viewHealthStatus;
            if (targetHealthStatus != currentHealthStatus) {
                ActivityUtil.showDialog(this,
                        R.string.health_status_dialog_title,
                        R.string.health_status_dialog_description,
                        () -> {
                            if (C19XApplication.getDeviceRegistration().isRegistered()) {
                                final long id = C19XApplication.getDeviceRegistration().getIdentifier();
                                Logger.debug(tag, "Self-report status update (current={},target={})", HealthStatus.toString(currentHealthStatus), HealthStatus.toString(targetHealthStatus));
                                C19XApplication.getNetworkClient().postHealthStatus(id, C19XApplication.getDeviceRegistration().getSharedSecretKey(), targetHealthStatus, r -> {
                                    if (r.getValue()) {
                                        C19XApplication.getHealthStatus().setStatus(targetHealthStatus);
                                        Logger.debug(tag, "Self-report status update successful (current={},previous={})", C19XApplication.getHealthStatus(), HealthStatus.toString(currentHealthStatus));
                                        ActivityUtil.showDialog(this, R.string.health_status_dialog_title, R.string.health_status_dialog_submit_success, () -> {
                                        }, null);
                                    } else {
                                        Logger.warn(tag, "Self-report status update failed, reverting to previous status (target={},revertingBackTo={})", HealthStatus.toString(targetHealthStatus), HealthStatus.toString(currentHealthStatus));
                                        setHealthStatus(currentHealthStatus);
                                        ActivityUtil.showDialog(this, R.string.health_status_dialog_title, R.string.health_status_dialog_failed_network, () -> {
                                        }, null);
                                    }
                                });
                            } else {
                                Logger.warn(tag, "Self-report status update failed, reverting to previous status (target={},revertingBackTo={})", HealthStatus.toString(targetHealthStatus), HealthStatus.toString(currentHealthStatus));
                                setHealthStatus(currentHealthStatus);
                                ActivityUtil.showDialog(this, R.string.health_status_dialog_title, R.string.health_status_dialog_failed_registration, () -> {
                                }, null);
                            }
                        },
                        () -> setHealthStatus(currentHealthStatus));
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
                ((RadioButton) findViewById(R.id.healthStatusOptionNoSymptom)).setChecked(true);
                C19XApplication.getHealthStatus().setStatus(HealthStatus.NO_SYMPTOM);
                break;
            }
            case HealthStatus.HAS_SYMPTOM: {
                ((RadioButton) findViewById(R.id.healthStatusOptionHasSymptom)).setChecked(true);
                C19XApplication.getHealthStatus().setStatus(HealthStatus.HAS_SYMPTOM);
                break;
            }
            case HealthStatus.CONFIRMED_DIAGNOSIS: {
                ((RadioButton) findViewById(R.id.healthStatusOptionConfirmedDiagnosis)).setChecked(true);
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
        final TextView option = (TextView) findViewById(R.id.contactStatusOption);
        final TextView description = (TextView) findViewById(R.id.contactStatusOptionDescription);
        switch (status) {
            case HealthStatus.NO_REPORT: {
                option.setText(R.string.contact_status_option_no_report);
                option.setBackgroundResource(R.color.colorGreen);
                description.setText(R.string.contact_status_option_no_report_description);
                break;
            }
            case HealthStatus.INFECTIOUS: {
                option.setText(R.string.contact_status_option_infectious);
                option.setBackgroundResource(R.color.colorRed);
                description.setText(R.string.contact_status_option_infectious_description);
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
                option.setText(R.string.advice_status_option_no_restriction);
                option.setBackgroundResource(R.color.colorGreen);
                description.setText(R.string.advice_status_option_no_restriction_description);
                break;
            }
            case HealthStatus.STAY_AT_HOME: {
                option.setText(R.string.advice_status_option_stay_at_home);
                option.setBackgroundResource(R.color.colorAmber);
                description.setText(R.string.advice_status_option_stay_at_home_description);
                break;
            }
            case HealthStatus.SELF_ISOLATION: {
                option.setText(R.string.advice_status_option_self_isolation);
                option.setBackgroundResource(R.color.colorRed);
                description.setText(R.string.advice_status_option_self_isolation_description);
                break;
            }
        }
    }

    /**
     * Set contact duration
     *
     * @param riskFactors
     */
    private void setContactDuration(final RiskFactors riskFactors) {
        final TextView textView = (TextView) findViewById(R.id.contactDuration);
        final long contactDurationThreshold = C19XApplication.getGlobalStatusLog().getContactDurationThreshold();
        final long exposureDurationThreshold = C19XApplication.getGlobalStatusLog().getExposureDurationThreshold();
        final long minutes = riskFactors.closeContactDuration / 60000;
        if (minutes <= 1) {
            textView.setText(R.string.status_contact_none);
        } else if (minutes <= 5) {
            textView.setText(R.string.status_contact_5);
        } else if (minutes <= 10) {
            textView.setText(R.string.status_contact_10);
        } else if (minutes <= 30) {
            textView.setText(R.string.status_contact_30);
        } else if (minutes <= 120) {
            textView.setText(R.string.status_contact_1h);
        } else {
            final long hours = minutes / 60;
            if (hours <= 48) {
                textView.setText(hours + " hours");
            } else {
                final long days = hours / 24;
                textView.setText(days + " days");
            }
        }

        if (riskFactors.closeContactDuration < contactDurationThreshold) {
            textView.setText(R.string.status_contact_none);
            textView.setBackgroundResource(R.color.colorGreen);
        } else if (riskFactors.closeContactDuration < exposureDurationThreshold) {
            textView.setBackgroundResource(R.color.colorAmber);
        } else {
            textView.setBackgroundResource(R.color.colorRed);
        }
    }


    private final void createNotification() {
        final String channelId = getString(R.string.app_fullname);
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, channelId, importance);
            channel.setDescription("Notification for " + channelId);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, Welcome.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.virus)
                .setContentTitle(channelId)
                .setContentText("Hello")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

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
