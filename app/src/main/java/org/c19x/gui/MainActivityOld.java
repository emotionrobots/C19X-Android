package org.c19x.gui;

import android.app.Activity;
//import android.content.Intent;
//import android.net.Uri;
//import android.os.Bundle;
//import android.os.PowerManager;
//import android.view.View;
//import android.widget.RadioButton;
//import android.widget.TextView;
//
//import org.c19x.AppDelegate;
//import org.c19x.R;
//import org.c19x.old.beacon.BeaconListener;
//import org.c19x.old.data.GlobalStatusLogListener;
//import org.c19x.old.data.GlobalStatusLogReceiver;
//import org.c19x.old.data.HealthStatus;
//import org.c19x.old.data.PersonalMessage;
//import org.c19x.old.logic.RiskAnalysisListener;
//import org.c19x.old.logic.RiskFactors;
//import org.c19x.data.Logger;
//import org.c19x.util.bluetooth.BluetoothStateMonitorListener;
//

/**
 * Minimalist app user interface for simplicity and usability
 */
public class MainActivityOld extends Activity {
//    private final static String tag = MainActivity.class.getName();
//    private PowerManager.WakeLock wakeLock;
//
//    // Beacon listener used by startBeacon and stopBeacon to update UI on state change.
//    private BluetoothStateMonitorListener bluetoothStateMonitorListener;
//    private BeaconListener beaconListener;
//
//    /**
//     * Global status log listener for updating the status text on the GUI
//     */
//    private final GlobalStatusLogListener globalStatusLogListener = new GlobalStatusLogListener() {
//        private final long dayMillis = 24 * 60 * 60 * 1000;
//
//        @Override
//        public void updated(final long fromVersion, final long toVersion) {
//            runOnUiThread(() -> {
//                final TextView textView = (TextView) findViewById(R.id.dataUpdate);
//                final long currentTime = AppDelegate.getTimestamp().getTime();
//                final long delta = (currentTime - toVersion) / dayMillis;
//                Logger.debug(tag, "Global status log updated (time={},toVersion={},delta={})", currentTime, toVersion, delta);
//                if (delta >= AppDelegate.getGlobalStatusLog().getRetentionPeriod()) {
//                    textView.setText(R.string.status_data_update_download);
//                    textView.setBackgroundResource(R.color.colorRed);
//                } else if (delta == 0) {
//                    textView.setText(R.string.status_data_update_latest);
//                    textView.setBackgroundResource(R.color.colorGreen);
//                } else if (delta == 1) {
//                    textView.setText("Updated 1 day ago");
//                    textView.setBackgroundResource(R.color.colorAmber);
//                } else {
//                    textView.setText("Updated " + delta + " day ago");
//                    textView.setBackgroundResource(R.color.colorAmber);
//                }
//            });
//        }
//    };
//
//    /**
//     * Risk analysis listener for updating the status texts on the GUI
//     */
//    private final RiskAnalysisListener riskAnalysisListener = new RiskAnalysisListener() {
//        @Override
//        public void update(final RiskFactors riskFactors, final byte contact, final byte advice) {
//            runOnUiThread(() -> {
//                setContactStatus(contact);
//                setAdviceStatus(advice);
//                setContactDuration(riskFactors);
//                setNewsFeedBasedOnRiskAnalysis();
//            });
//        }
//    };
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        AppDelegate.registerOnCreate(this);
//        wakeLock = ActivityUtil.getWakeLock(this);
//
//        // Setup app UI
//        ActivityUtil.setFullscreen(this);
//        setContentView(R.layout.activity_main_old);
//
//        // Start monitoring bluetooth state
//        registerBluetoothStateMonitorListener();
//        registerBluetoothBeaconListeners();
//
//        // Start monitoring risk analysis results
//        AppDelegate.getRiskAnalysis().addListener(riskAnalysisListener);
//
//        // Start monitoring global status log version update
//        AppDelegate.getGlobalStatusLog().addListener(globalStatusLogListener);
//
//        setDefaultState();
//        AppDelegate.getRiskAnalysis().updateAssessment();
//    }
//
//    @Override
//    protected void onDestroy() {
//        // Stop monitoring global status log version update
//        AppDelegate.getGlobalStatusLog().removeListener(globalStatusLogListener);
//
//        // Stop monitoring risk analysis results
//        AppDelegate.getRiskAnalysis().removeListener(riskAnalysisListener);
//
//        // Stop monitoring bluetooth state
//        unregisterBluetoothBeaconListeners();
//        unregisterBluetoothStateMonitorListener();
//
//        ActivityUtil.setNotification(this, null);
//
//        AppDelegate.unregisterOnDestroy(this);
//
//        if (wakeLock != null) {
//            wakeLock.release();
//        }
//        super.onDestroy();
//    }
//
//    @Override
//    public void onBackPressed() {
//        final Intent startMain = new Intent(Intent.ACTION_MAIN);
//        startMain.addCategory(Intent.CATEGORY_HOME);
//        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(startMain);
//    }
//
//    // BEACON ======================================================================================
//
//    private final void registerBluetoothStateMonitorListener() {
//        final Activity self = this;
//        this.bluetoothStateMonitorListener = new BluetoothStateMonitorListener() {
//            @Override
//            public void enabled() {
//                ActivityUtil.setNotification(self, getString(R.string.app_notification));
//            }
//
//            @Override
//            public void disabling() {
//                ActivityUtil.setNotification(self, getString(R.string.status_beacon_off_notification));
//            }
//        };
//        AppDelegate.getBluetoothStateMonitor().addListener(bluetoothStateMonitorListener);
//    }
//
//    private final void unregisterBluetoothStateMonitorListener() {
//        AppDelegate.getBluetoothStateMonitor().removeListener(bluetoothStateMonitorListener);
//    }
//
//    /**
//     * Start beacon transmitter and receiver, and register this activity as listener
//     */
//    private final void registerBluetoothBeaconListeners() {
//        this.beaconListener = new BeaconListener() {
//            @Override
//            public void start() {
//                update();
//            }
//
//            @Override
//            public void error(int errorCode) {
//                update();
//            }
//
//            @Override
//            public void stop() {
//                update();
//            }
//
//            /**
//             * Update beacon status on UI
//             */
//            private void update() {
//                final boolean transmitter = AppDelegate.getBeaconTransmitter().isStarted();
//                final boolean receiver = AppDelegate.getBeaconReceiver().isStarted();
//                Logger.debug(tag, "Beacon status update (transmitter={},receiver={})", transmitter, receiver);
//
//                runOnUiThread(() -> {
//                    final TextView textView = (TextView) findViewById(R.id.beaconStatus);
//                    if (!transmitter && !receiver) {
//                        textView.setText(R.string.status_beacon_off);
//                        textView.setBackgroundResource(R.color.colorRed);
//                    } else if (transmitter && !receiver) {
//                        textView.setText(R.string.status_beacon_tx);
//                        textView.setBackgroundResource(R.color.colorAmber);
//                    } else if (!transmitter && receiver) {
//                        textView.setText(R.string.status_beacon_rx);
//                        textView.setBackgroundResource(R.color.colorAmber);
//                    } else {
//                        textView.setText(R.string.status_beacon_on);
//                        textView.setBackgroundResource(R.color.colorGreen);
//                    }
//                });
//            }
//        };
//        AppDelegate.getBeaconTransmitter().addListener(beaconListener);
//        AppDelegate.getBeaconReceiver().addListener(beaconListener);
//        beaconListener.start();
//    }
//
//    /**
//     * Stop beacon transmitter and receiver, and unregister this activity as listener
//     */
//    private final void unregisterBluetoothBeaconListeners() {
//        if (beaconListener == null) {
//            return;
//        }
//        AppDelegate.getBeaconTransmitter().removeListener(beaconListener);
//        AppDelegate.getBeaconReceiver().removeListener(beaconListener);
//        beaconListener = null;
//    }
//
//    // GUI =========================================================================================
//
//    /**
//     * Set default UI state
//     */
//    private final void setDefaultState() {
//        setHealthStatus(AppDelegate.getHealthStatus().getStatus());
//        setContactStatus(AppDelegate.getRiskAnalysis().getContact());
//        setAdviceStatus(AppDelegate.getRiskAnalysis().getAdvice());
//        setNewsFeedBasedOnRiskAnalysis();
//    }
//
//    /**
//     * Update beacon identity now, triggered from GUI
//     *
//     * @param view
//     */
//    public void updateBeacon(View view) {
//        Logger.debug(tag, "Beacon transmitter change identity requested manually");
//        AppDelegate.getBeaconTransmitter().setId(AppDelegate.getAliasIdentifier());
//    }
//
//    /**
//     * Update close contact data now, triggered from GUI
//     *
//     * @param view
//     */
//    public void updateContactDuration(View view) {
//        Logger.debug(tag, "Contact duration update requested manually");
//        AppDelegate.getRiskAnalysis().updateAssessment();
//    }
//
//
//    /**
//     * Update global status log data now, triggered from GUI
//     *
//     * @param view
//     */
//    public void updateData(View view) {
//        Logger.debug(tag, "Global status log update and detection event log backup requested manually");
//        AppDelegate.getDetectionEventLog().backupToFile();
//        new GlobalStatusLogReceiver().onReceive(null, null);
//    }
//
//    public void onClickContactStatus(View view) {
//        switch (AppDelegate.getRiskAnalysis().getContact()) {
//            case HealthStatus.NO_REPORT:
//                ActivityUtil.showDialog(this, R.string.contact_status_option_no_report, R.string.contact_status_option_no_report_description, () -> {
//                }, null);
//                break;
//            case HealthStatus.INFECTIOUS:
//                ActivityUtil.showDialog(this, R.string.contact_status_option_infectious, R.string.contact_status_option_infectious_description, () -> {
//                }, null);
//                break;
//        }
//    }
//
//    public void onClickAdviceStatus(View view) {
//        final boolean infectedContacts = AppDelegate.getRiskAnalysis().getContact() == HealthStatus.INFECTIOUS;
//        switch (AppDelegate.getRiskAnalysis().getAdvice()) {
//            case HealthStatus.NO_RESTRICTION:
//                ActivityUtil.showDialog(this,
//                        R.string.advice_status_option_no_restriction,
//                        (infectedContacts ?
//                                R.string.advice_status_option_no_restriction_description_infected_contacts :
//                                R.string.advice_status_option_no_restriction_description),
//                        () -> {
//                        }, null);
//                break;
//            case HealthStatus.STAY_AT_HOME:
//                ActivityUtil.showDialog(this,
//                        R.string.advice_status_option_stay_at_home,
//                        (infectedContacts ?
//                                R.string.advice_status_option_stay_at_home_description_infected_contacts :
//                                R.string.advice_status_option_stay_at_home_description),
//                        () -> {
//                        }, null);
//                break;
//            case HealthStatus.SELF_ISOLATION:
//                ActivityUtil.showDialog(this, R.string.advice_status_option_self_isolation, R.string.advice_status_option_self_isolation_description, () -> {
//                }, null);
//                break;
//        }
//    }
//
//    /**
//     * Set health status from GUI
//     *
//     * @param view
//     */
//    public void setHealthStatus(View view) {
//        if (((RadioButton) view).isChecked()) {
//            // Get target health status
//            byte viewHealthStatus = HealthStatus.NO_SYMPTOM;
//            switch (view.getId()) {
//                case R.id.healthStatusOptionNoSymptom: {
//                    viewHealthStatus = HealthStatus.NO_SYMPTOM;
//                    break;
//                }
//                case R.id.healthStatusOptionHasSymptom: {
//                    viewHealthStatus = HealthStatus.HAS_SYMPTOM;
//                    break;
//                }
//                case R.id.healthStatusOptionConfirmedDiagnosis: {
//                    viewHealthStatus = HealthStatus.CONFIRMED_DIAGNOSIS;
//                    break;
//                }
//                default: {
//                    Logger.warn(tag, "Unknown target health status (viewId={})", view.getId());
//                    break;
//                }
//            }
//
//            // Post status report
//            final byte currentHealthStatus = AppDelegate.getHealthStatus().getStatus();
//            final byte targetHealthStatus = viewHealthStatus;
//            if (targetHealthStatus != currentHealthStatus) {
//                ActivityUtil.showDialog(this,
//                        R.string.health_status_dialog_title,
//                        R.string.health_status_dialog_description,
//                        () -> {
//                            if (AppDelegate.getDeviceRegistration().isRegistered()) {
//                                final long id = AppDelegate.getDeviceRegistration().getIdentifier();
//                                Logger.debug(tag, "Self-report status update (current={},target={})", HealthStatus.toString(currentHealthStatus), HealthStatus.toString(targetHealthStatus));
//                                AppDelegate.getNetworkClient().postHealthStatus(id, AppDelegate.getDeviceRegistration().getSharedSecretKey(), targetHealthStatus, r -> {
//                                    if (r.getValue()) {
//                                        AppDelegate.getHealthStatus().setStatus(targetHealthStatus);
//                                        Logger.debug(tag, "Self-report status update successful (current={},previous={})", AppDelegate.getHealthStatus(), HealthStatus.toString(currentHealthStatus));
//                                        ActivityUtil.showDialog(this, R.string.health_status_dialog_title, R.string.health_status_dialog_submit_success, () -> {
//                                        }, null);
//                                    } else {
//                                        Logger.warn(tag, "Self-report status update failed, reverting to previous status (target={},revertingBackTo={})", HealthStatus.toString(targetHealthStatus), HealthStatus.toString(currentHealthStatus));
//                                        setHealthStatus(currentHealthStatus);
//                                        ActivityUtil.showDialog(this, R.string.health_status_dialog_title, R.string.health_status_dialog_failed_network, () -> {
//                                        }, null);
//                                    }
//                                });
//                            } else {
//                                Logger.warn(tag, "Self-report status update failed, reverting to previous status (target={},revertingBackTo={})", HealthStatus.toString(targetHealthStatus), HealthStatus.toString(currentHealthStatus));
//                                setHealthStatus(currentHealthStatus);
//                                ActivityUtil.showDialog(this, R.string.health_status_dialog_title, R.string.health_status_dialog_failed_registration, () -> {
//                                }, null);
//                            }
//                        },
//                        () -> setHealthStatus(currentHealthStatus));
//            }
//        }
//    }
//
//    /**
//     * Set health status programmatically, e.g. to last status when post update fails.
//     *
//     * @param status
//     */
//    private void setHealthStatus(final byte status) {
//        switch (status) {
//            case HealthStatus.NO_SYMPTOM: {
//                ((RadioButton) findViewById(R.id.healthStatusOptionNoSymptom)).setChecked(true);
//                AppDelegate.getHealthStatus().setStatus(HealthStatus.NO_SYMPTOM);
//                break;
//            }
//            case HealthStatus.HAS_SYMPTOM: {
//                ((RadioButton) findViewById(R.id.healthStatusOptionHasSymptom)).setChecked(true);
//                AppDelegate.getHealthStatus().setStatus(HealthStatus.HAS_SYMPTOM);
//                break;
//            }
//            case HealthStatus.CONFIRMED_DIAGNOSIS: {
//                ((RadioButton) findViewById(R.id.healthStatusOptionConfirmedDiagnosis)).setChecked(true);
//                AppDelegate.getHealthStatus().setStatus(HealthStatus.CONFIRMED_DIAGNOSIS);
//                break;
//            }
//        }
//    }
//
//    /**
//     * Set recent contact status.
//     *
//     * @param status
//     */
//    private void setContactStatus(final byte status) {
//        final TextView option = (TextView) findViewById(R.id.contactStatusOption);
//        switch (status) {
//            case HealthStatus.NO_REPORT: {
//                option.setText(R.string.contact_status_option_no_report);
//                option.setBackgroundResource(R.color.colorGreen);
//                break;
//            }
//            case HealthStatus.INFECTIOUS: {
//                option.setText(R.string.contact_status_option_infectious);
//                option.setBackgroundResource(R.color.colorRed);
//                if (AppDelegate.getRiskAnalysis().getAdvice() != HealthStatus.SELF_ISOLATION) {
//                    ActivityUtil.setNotification(this, getString(R.string.contact_status_option_infectious_notification));
//                }
//                break;
//            }
//        }
//    }
//
//    /**
//     * Set advice status.
//     *
//     * @param status
//     */
//    private void setAdviceStatus(final byte status) {
//        final TextView option = (TextView) findViewById(R.id.adviceOption);
//        switch (status) {
//            case HealthStatus.NO_RESTRICTION: {
//                option.setText(R.string.advice_status_option_no_restriction);
//                option.setBackgroundResource(R.color.colorGreen);
//                break;
//            }
//            case HealthStatus.STAY_AT_HOME: {
//                option.setText(R.string.advice_status_option_stay_at_home);
//                option.setBackgroundResource(R.color.colorAmber);
//                break;
//            }
//            case HealthStatus.SELF_ISOLATION: {
//                option.setText(R.string.advice_status_option_self_isolation);
//                option.setBackgroundResource(R.color.colorRed);
//                ActivityUtil.setNotification(this, getString(R.string.advice_status_option_self_isolation_notification));
//                break;
//            }
//        }
//    }
//
//    /**
//     * Set contact duration per day
//     *
//     * @param riskFactors
//     */
//    private void setContactDuration(final RiskFactors riskFactors) {
//        final TextView textView = (TextView) findViewById(R.id.contactDuration);
//        final long contactDurationThreshold = AppDelegate.getGlobalStatusLog().getContactDurationThreshold();
//        final long exposureDurationThreshold = AppDelegate.getGlobalStatusLog().getExposureDurationThreshold();
//        final long minutes = (riskFactors.closeContactDuration / riskFactors.detectionDays) / 60000;
//
//        if (minutes == 0) {
//            textView.setText(R.string.status_contact_none);
//        } else if (minutes < 120) {
//            textView.setText(minutes + " minutes");
//        } else {
//            final int hours = (int) (minutes / 60);
//            textView.setText(hours + " hours");
//        }
//
//        // textView.setText(((riskFactors.closeContactDuration / riskFactors.detectionDays) / 1000) + " seconds");
//
//        if (riskFactors.closeContactDuration >= exposureDurationThreshold) {
//            textView.setBackgroundResource(R.color.colorAmber);
//        } else {
//            textView.setBackgroundResource(R.color.colorGreen);
//        }
//    }
//
//    private void setNewsFeedBasedOnRiskAnalysis() {
//        int color = R.color.colorDarkGrey;
//        final StringBuilder s = new StringBuilder();
////        switch (C19XApplication.getRiskAnalysis().getContact()) {
////            case HealthStatus.NO_REPORT: {
////                s.append(getString(R.string.contact_status_option_no_report));
////                s.append(" : ");
////                s.append(getString(R.string.contact_status_option_no_report_description));
////                break;
////            }
////            case HealthStatus.INFECTIOUS: {
////                s.append(getString(R.string.contact_status_option_infectious));
////                s.append(" : ");
////                s.append(getString(R.string.contact_status_option_infectious_description));
////                break;
////            }
////        }
////        s.append(" | ");
//        final boolean infectionRisk = (AppDelegate.getRiskAnalysis().getContact() != HealthStatus.NO_REPORT);
//        switch (AppDelegate.getRiskAnalysis().getAdvice()) {
//            case HealthStatus.NO_RESTRICTION: {
//                s.append(getString(R.string.advice_status_option_no_restriction));
//                s.append(" : ");
//                s.append(getString(
//                        infectionRisk ?
//                                R.string.advice_status_option_no_restriction_description_infected_contacts :
//                                R.string.advice_status_option_no_restriction_description));
//                // color = R.color.colorGreen;
//                break;
//            }
//            case HealthStatus.STAY_AT_HOME: {
//                s.append(getString(R.string.advice_status_option_stay_at_home));
//                s.append(" : ");
//                s.append(getString(
//                        infectionRisk ?
//                                R.string.advice_status_option_stay_at_home_description_infected_contacts :
//                                R.string.advice_status_option_stay_at_home_description));
//                // color = R.color.colorAmber;
//                break;
//            }
//            case HealthStatus.SELF_ISOLATION: {
//                s.append(getString(R.string.advice_status_option_self_isolation));
//                s.append(" : ");
//                s.append(getString(R.string.advice_status_option_self_isolation_description));
//                // color = R.color.colorRed;
//                break;
//            }
//        }
//        setPersonalMessage(new PersonalMessage(s.toString(), color, null));
//    }
//
//    /**
//     * Set scrolling news feed text.
//     *
//     * @param personalMessage
//     */
//    private void setPersonalMessage(PersonalMessage personalMessage) {
//        final TextView newsFeed = (TextView) findViewById(R.id.personalMessage);
//        newsFeed.setText(personalMessage.getText());
//        newsFeed.setTextColor(getResources().getColor(personalMessage.getColor(), null));
//
//        if (personalMessage.getUrl() != null) {
//            newsFeed.setOnClickListener(v -> {
//                Intent browserIntent = new Intent(Intent.ACTION_VIEW);
//                browserIntent.setData(Uri.parse(personalMessage.getUrl()));
//                startActivity(browserIntent);
//            });
//        } else {
//            newsFeed.setOnClickListener(null);
//        }
//        newsFeed.setSelected(true);
//    }
}
