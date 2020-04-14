package org.c19x.gui;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.c19x.C19XApplication;
import org.c19x.R;
import org.c19x.beacon.BeaconListener;
import org.c19x.data.GlobalStatusLogListener;
import org.c19x.data.GlobalStatusLogReceiver;
import org.c19x.data.HealthStatus;
import org.c19x.data.PersonalMessage;
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
    private String notificationText = null;

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
            setNewsFeedBasedOnRiskAnalysis();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        C19XApplication.registerOnCreate(this);

        // Setup app UI
        ActivityUtil.setFullscreen(this);
        setContentView(R.layout.activity_main);
        setDefaultState();

        // Start monitoring bluetooth state
        registerBluetoothBeaconListeners();

        // Start monitoring risk analysis results
        C19XApplication.getRiskAnalysis().addListener(riskAnalysisListener);
        C19XApplication.getRiskAnalysis().updateAssessment();

        // Start monitoring global status log version update
        C19XApplication.getGlobalStatusLog().addListener(globalStatusLogListener);

        setNotification(getString(R.string.app_notification));
    }

    @Override
    protected void onDestroy() {
        // Stop monitoring global status log version update
        C19XApplication.getGlobalStatusLog().removeListener(globalStatusLogListener);

        // Stop monitoring risk analysis results
        C19XApplication.getRiskAnalysis().removeListener(riskAnalysisListener);

        // Stop monitoring bluetooth state
        unregisterBluetoothBeaconListeners();

        setNotification(null);

        C19XApplication.unregisterOnDestroy(this);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        final Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    // BEACON ======================================================================================

    /**
     * Start beacon transmitter and receiver, and register this activity as listener
     */
    private final void registerBluetoothBeaconListeners() {
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
    private final void unregisterBluetoothBeaconListeners() {
        if (beaconListener == null) {
            return;
        }
        C19XApplication.getBluetoothStateMonitor().removeListener(bluetoothStateMonitorListener);
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
        setNewsFeedBasedOnRiskAnalysis();
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
        Logger.debug(tag, "Global status log update and detection event log backup requested manually");
        C19XApplication.getDetectionEventLog().backupToFile();
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
        final boolean infectedContacts = C19XApplication.getRiskAnalysis().getContact() == HealthStatus.INFECTIOUS;
        switch (C19XApplication.getRiskAnalysis().getAdvice()) {
            case HealthStatus.NO_RESTRICTION:
                ActivityUtil.showDialog(this,
                        R.string.advice_status_option_no_restriction,
                        (infectedContacts ?
                                R.string.advice_status_option_no_restriction_description_infected_contacts :
                                R.string.advice_status_option_no_restriction_description),
                        () -> {
                        }, null);
                break;
            case HealthStatus.STAY_AT_HOME:
                ActivityUtil.showDialog(this,
                        R.string.advice_status_option_stay_at_home,
                        (infectedContacts ?
                                R.string.advice_status_option_stay_at_home_description_infected_contacts :
                                R.string.advice_status_option_stay_at_home_description),
                        () -> {
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
        switch (status) {
            case HealthStatus.NO_REPORT: {
                option.setText(R.string.contact_status_option_no_report);
                option.setBackgroundResource(R.color.colorGreen);
                break;
            }
            case HealthStatus.INFECTIOUS: {
                option.setText(R.string.contact_status_option_infectious);
                option.setBackgroundResource(R.color.colorRed);
                setNotification(getString(R.string.contact_status_option_infectious_notification));
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
        switch (status) {
            case HealthStatus.NO_RESTRICTION: {
                option.setText(R.string.advice_status_option_no_restriction);
                option.setBackgroundResource(R.color.colorGreen);
                break;
            }
            case HealthStatus.STAY_AT_HOME: {
                option.setText(R.string.advice_status_option_stay_at_home);
                option.setBackgroundResource(R.color.colorAmber);
                break;
            }
            case HealthStatus.SELF_ISOLATION: {
                option.setText(R.string.advice_status_option_self_isolation);
                option.setBackgroundResource(R.color.colorRed);
                setNotification(getString(R.string.advice_status_option_self_isolation_notification));
                break;
            }
        }
    }

    /**
     * Set contact duration per day
     *
     * @param riskFactors
     */
    private void setContactDuration(final RiskFactors riskFactors) {
        final TextView textView = (TextView) findViewById(R.id.contactDuration);
        final long contactDurationThreshold = C19XApplication.getGlobalStatusLog().getContactDurationThreshold();
        final long exposureDurationThreshold = C19XApplication.getGlobalStatusLog().getExposureDurationThreshold();
        final long minutes = (riskFactors.closeContactDuration / riskFactors.detectionDays) / 60000;

        if (minutes < 2) {
            textView.setText(R.string.status_contact_none);
        } else if (minutes < 10) {
            textView.setText((minutes / 2) * 2 + " minutes");
        } else if (minutes < 60) {
            textView.setText((minutes / 10) * 10 + " minutes");
        } else {
            final int hours = (int) (minutes / 60);
            textView.setText(hours + " hour" + (hours == 1 ? "" : "s"));
        }

        if (riskFactors.closeContactDuration >= exposureDurationThreshold) {
            textView.setBackgroundResource(R.color.colorAmber);
        } else {
            textView.setBackgroundResource(R.color.colorGreen);
        }
    }

    private void setNewsFeedBasedOnRiskAnalysis() {
        int color = R.color.colorDarkGrey;
        final StringBuilder s = new StringBuilder();
//        switch (C19XApplication.getRiskAnalysis().getContact()) {
//            case HealthStatus.NO_REPORT: {
//                s.append(getString(R.string.contact_status_option_no_report));
//                s.append(" : ");
//                s.append(getString(R.string.contact_status_option_no_report_description));
//                break;
//            }
//            case HealthStatus.INFECTIOUS: {
//                s.append(getString(R.string.contact_status_option_infectious));
//                s.append(" : ");
//                s.append(getString(R.string.contact_status_option_infectious_description));
//                break;
//            }
//        }
//        s.append(" | ");
        final boolean infectionRisk = (C19XApplication.getRiskAnalysis().getContact() != HealthStatus.NO_REPORT);
        switch (C19XApplication.getRiskAnalysis().getAdvice()) {
            case HealthStatus.NO_RESTRICTION: {
                s.append(getString(R.string.advice_status_option_no_restriction));
                s.append(" : ");
                s.append(getString(
                        infectionRisk ?
                                R.string.advice_status_option_no_restriction_description_infected_contacts :
                                R.string.advice_status_option_no_restriction_description));
                // color = R.color.colorGreen;
                break;
            }
            case HealthStatus.STAY_AT_HOME: {
                s.append(getString(R.string.advice_status_option_stay_at_home));
                s.append(" : ");
                s.append(getString(
                        infectionRisk ?
                                R.string.advice_status_option_stay_at_home_description_infected_contacts :
                                R.string.advice_status_option_stay_at_home_description));
                // color = R.color.colorAmber;
                break;
            }
            case HealthStatus.SELF_ISOLATION: {
                s.append(getString(R.string.advice_status_option_self_isolation));
                s.append(" : ");
                s.append(getString(R.string.advice_status_option_self_isolation_description));
                // color = R.color.colorRed;
                break;
            }
        }
        setPersonalMessage(new PersonalMessage(s.toString(), color, null));
    }

    /**
     * Set scrolling news feed text.
     *
     * @param personalMessage
     */
    private void setPersonalMessage(PersonalMessage personalMessage) {
        final TextView newsFeed = (TextView) findViewById(R.id.personalMessage);
        newsFeed.setText(personalMessage.getText());
        newsFeed.setTextColor(getResources().getColor(personalMessage.getColor(), null));

        if (personalMessage.getUrl() != null) {
            newsFeed.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW);
                browserIntent.setData(Uri.parse(personalMessage.getUrl()));
                startActivity(browserIntent);
            });
        } else {
            newsFeed.setOnClickListener(null);
        }
        newsFeed.setSelected(true);
    }

    /**
     * Create notification
     */
    private final void setNotification(final String text) {
        final String channelId = getString(R.string.app_fullname);
        if (text != null) {
            if (!text.equals(notificationText)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    int importance = NotificationManager.IMPORTANCE_DEFAULT;
                    NotificationChannel channel = new NotificationChannel(channelId, channelId, importance);
                    channel.setDescription("Notification for " + channelId);
                    NotificationManager notificationManager = getSystemService(NotificationManager.class);
                    notificationManager.createNotificationChannel(channel);
                }

                final Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

                final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.virus)
                        .setContentTitle(channelId)
                        .setContentText(text)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
                notificationManager.notify(channelId.hashCode(), builder.build());
            }
        } else {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.deleteNotificationChannel(channelId);
        }
    }

}
