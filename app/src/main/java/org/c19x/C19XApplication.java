package org.c19x;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.c19x.beacon.BeaconReceiver;
import org.c19x.beacon.BeaconTransmitter;
import org.c19x.beacon.ble.BLEReceiver;
import org.c19x.beacon.ble.BLETransmitter;
import org.c19x.data.DetectionEventLog;
import org.c19x.data.DeviceRegistration;
import org.c19x.data.GlobalStatusLog;
import org.c19x.data.GlobalStatusLogReceiver;
import org.c19x.data.HealthStatus;
import org.c19x.logic.RiskAnalysis;
import org.c19x.network.NetworkClient;
import org.c19x.util.Logger;
import org.c19x.util.Storage;
import org.c19x.util.bluetooth.BluetoothStateMonitor;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;

public class C19XApplication extends Application {
    private final static String tag = C19XApplication.class.getName();

    /**
     * Bluetooth beacon service Id
     */
    public final static long bluetoothLeServiceId = 1234567890123456789l;
    /**
     * Anonymous ID range, set to range=2^N where range is close to a multiple of population size.
     */
    public final static int anonymousIdRange = 67108864;

    /**
     * Default application server on first use, this will be changed by downloaded updates.
     */
    public final static String defaultServer = "http://711.no-ip.biz:80";


    private static Application application;
    private static Context context;
    private static Storage storage;

    private static DeviceRegistration deviceRegistration;


    private static HealthStatus healthStatus;
    private static BluetoothStateMonitor bluetoothStateMonitor;
    private static BeaconTransmitter beaconTransmitter;
    private static BeaconReceiver beaconReceiver;
    private static DetectionEventLog detectionEventLog;
    private static GlobalStatusLog globalStatusLog;
    private static NetworkClient networkClient;
    private static RiskAnalysis riskAnalysis;
    private static PendingIntent globalStatusLogUpdateTask;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
        context = getApplicationContext();
        storage = getStorage();

//        bluetoothStateMonitor = getBluetoothStateMonitor();
//        this.riskAnalysis = getRiskAnalysis();
//        startGlobalStatusLogAutomaticUpdate();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        getBluetoothStateMonitor().stop();
        getBeaconReceiver().removeListener(getDetectionEventLog());
        getDetectionEventLog().close();
        stopGlobalStatusLogAutomaticUpdate();
    }

    /**
     * Get application context.
     *
     * @return
     */
    public final static Context getContext() {
        return C19XApplication.context;
    }

    /**
     * Get internal storage.
     *
     * @return
     */
    public final static Storage getStorage() {
        if (storage == null) {
            storage = new Storage();
        }
        return storage;
    }

    /**
     * Get singleton health status register.
     *
     * @return
     */
    public final static HealthStatus getHealthStatus() {
        if (healthStatus == null) {
            healthStatus = new HealthStatus();
        }
        return healthStatus;
    }

    /**
     * Get device identifier.
     *
     * @return
     */
    public final static DeviceRegistration getDeviceRegistration() {
        if (deviceRegistration == null) {
            deviceRegistration = new DeviceRegistration();
        }
        return deviceRegistration;
    }

    /**
     * Get singleton bluetooth state monitor for bluetooth on/off events.
     *
     * @return
     */
    public final static BluetoothStateMonitor getBluetoothStateMonitor() {
        if (bluetoothStateMonitor == null) {
            bluetoothStateMonitor = new BluetoothStateMonitor();
            bluetoothStateMonitor.start();
        }
        return bluetoothStateMonitor;
    }

    /**
     * Get singleton beacon transmitter.
     *
     * @return
     */
    public final static BeaconTransmitter getBeaconTransmitter() {
        if (beaconTransmitter == null) {
            beaconTransmitter = new BLETransmitter();
        }
        return beaconTransmitter;
    }

    /**
     * Get singleton beacon receiver.
     *
     * @return
     */
    public final static BeaconReceiver getBeaconReceiver() {
        if (beaconReceiver == null) {
            beaconReceiver = new BLEReceiver();
            beaconReceiver.addListener(getDetectionEventLog());
        }
        return beaconReceiver;
    }

    /**
     * Get singleton detection event log.
     *
     * @return
     */
    public final static DetectionEventLog getDetectionEventLog() {
        if (detectionEventLog == null) {
            detectionEventLog = new DetectionEventLog();
        }
        return detectionEventLog;
    }

    /**
     * Get singleton global status log.
     *
     * @return
     */
    public final static GlobalStatusLog getGlobalStatusLog() {
        if (globalStatusLog == null) {
            globalStatusLog = new GlobalStatusLog();
        }
        return globalStatusLog;
    }

    /**
     * Get singleton network client.
     *
     * @return
     */
    public final static NetworkClient getNetworkClient() {
        if (networkClient == null) {
            networkClient = new NetworkClient();
        }
        return networkClient;
    }

    public final static RiskAnalysis getRiskAnalysis() {
        if (riskAnalysis == null) {
            riskAnalysis = new RiskAnalysis();
            // Update risk analysis on health status update
            getHealthStatus().addListener((fromStatus, toStatus) -> {
                if (fromStatus != toStatus) {
                    riskAnalysis.updateAssessment();
                }
            });
            // Update risk analysis on global status log update
            getGlobalStatusLog().addListener((fromVersion, toVersion) -> {
                if (fromVersion != toVersion) {
                    riskAnalysis.updateAssessment();
                }
            });
        }
        return riskAnalysis;
    }

    /**
     * Update all application parameters according to global status log.
     */
    public final static synchronized void updateAllParameters() {
        if (getGlobalStatusLog().getServerAddress() != null) {
            getNetworkClient().setServer(getGlobalStatusLog().getServerAddress());
        }
        getDetectionEventLog().setRetentionPeriod(getGlobalStatusLog().getRetentionPeriod());
        getDetectionEventLog().setContactDurationThreshold(getGlobalStatusLog().getContactDurationThreshold());
        getDetectionEventLog().setSignalStrengthThreshold(getGlobalStatusLog().getSignalStrengthThreshold());
    }

    /**
     * Modulo of SHA-256 cryptographic hash for data obfuscation. Using hash collision as feature
     * for ensuring anonymity.
     *
     * @param text   Plain text data.
     * @param modulo Hash value range.
     * @return Hash value.
     */
    public final static long hash(final String text, final BigInteger modulo) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(text.getBytes());
            final BigInteger value = new BigInteger(1, hash);
            return value.mod(modulo).longValue();
        } catch (Throwable e) {
            Logger.error(tag, "Failed to hash text", e);
            return 0;
        }
    }

    /**
     * Schedule daily task to update global status log.
     */
    private final static void startGlobalStatusLogAutomaticUpdate() {
        if (globalStatusLogUpdateTask == null) {
            // Randomised update time between 00:00 and 04:59
            final Random random = new Random();
            final Calendar updateTime = Calendar.getInstance();
            updateTime.setTimeZone(TimeZone.getTimeZone("GMT"));
            updateTime.set(Calendar.HOUR_OF_DAY, random.nextInt(4));
            updateTime.set(Calendar.MINUTE, random.nextInt(59));

            // Set alarm
            final Intent intent = new Intent(getContext(), GlobalStatusLogReceiver.class);
            globalStatusLogUpdateTask = PendingIntent.getBroadcast(getContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            final AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
//            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, updateTime.getTimeInMillis(), AlarmManager.INTERVAL_DAY, globalStatusLogUpdateTask);
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 10000, globalStatusLogUpdateTask);
        }
    }

    /**
     * Cancel daily task to update global status log.
     */
    private final static void stopGlobalStatusLogAutomaticUpdate() {
        if (globalStatusLogUpdateTask != null) {
            final AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(globalStatusLogUpdateTask);
            globalStatusLogUpdateTask = null;
        }
    }

}
