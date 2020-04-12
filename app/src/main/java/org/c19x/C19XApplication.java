package org.c19x;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.c19x.beacon.BeaconReceiver;
import org.c19x.beacon.BeaconTransmitter;
import org.c19x.beacon.ble.BLEReceiver;
import org.c19x.beacon.ble.BLETransmitter;
import org.c19x.data.DetectionEventLog;
import org.c19x.data.DeviceRegistration;
import org.c19x.data.GlobalStatusLog;
import org.c19x.data.GlobalStatusLogReceiver;
import org.c19x.data.HealthStatus;
import org.c19x.data.Timestamp;
import org.c19x.logic.RiskAnalysis;
import org.c19x.network.NetworkClient;
import org.c19x.network.response.NetworkResponse;
import org.c19x.util.Logger;
import org.c19x.util.Storage;
import org.c19x.util.bluetooth.BluetoothStateMonitor;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Application and singletons.
 */
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
    public final static String defaultServer = "http://c19x.servehttp.com:80";


    private static Application application;
    private static Context context;
    private static SharedPreferences preferences;
    private static Storage storage;
    private static Timer timer;

    private static Timestamp timestamp;
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
        timer = getTimer();

        timestamp = getTimestamp();
        bluetoothStateMonitor = getBluetoothStateMonitor();
        riskAnalysis = getRiskAnalysis();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        getBluetoothStateMonitor().stop();
        getBeaconReceiver().removeListener(getDetectionEventLog());
        getDetectionEventLog().close();
        stopGlobalStatusLogAutomaticUpdate();
        getTimer().cancel();
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
     * Get app preference.
     *
     * @param key          Value key.
     * @param defaultValue Default value.
     * @return
     */
    public final static synchronized String getPreference(final String key, final String defaultValue) {
        if (preferences == null) {
            preferences = getContext().getSharedPreferences(getContext().getString(R.string.file_preferences), Context.MODE_PRIVATE);
        }
        return preferences.getString(key, defaultValue);
    }

    /**
     * Set app preference.
     *
     * @param key   Value key.
     * @param value Value.
     */
    public final static synchronized void setPreference(final String key, final String value) {
        if (preferences == null) {
            preferences = getContext().getSharedPreferences(getContext().getString(R.string.file_preferences), Context.MODE_PRIVATE);
        }
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, value);
        editor.commit();
    }

    /**
     * Get internal storage.
     *
     * @return
     */
    public final static synchronized Storage getStorage() {
        if (storage == null) {
            storage = new Storage();
        }
        return storage;
    }

    /**
     * Get timer.
     *
     * @return
     */
    public final static Timer getTimer() {
        if (timer == null) {
            timer = new Timer();
        }
        return timer;
    }

    /**
     * Get timestamp.
     *
     * @return
     */
    public final static Timestamp getTimestamp() {
        if (timestamp == null) {
            timestamp = new Timestamp();
            // Sync time with server once every hour
            getTimer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    getNetworkClient().getTimestampFromBob(r -> {
                        if (r.getNetworkResponse() == NetworkResponse.OK) {
                            getTimestamp().setServerTime(r.getValue());
                        }
                    });
                }
            }, 0, 60 * 60 * 1000);
        }
        return timestamp;
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
     * Get randomly selected alias identifier for current retention period.
     *
     * @return
     */
    public final static long getAliasIdentifier() {
        final long toTime = getTimestamp().getTime();
        final long fromTime = toTime - (getGlobalStatusLog().getRetentionPeriod() * 86400000);
        long aliasIdentifier = -1;
        if (getDeviceRegistration().isRegistered()) {
            aliasIdentifier = getDeviceRegistration().getAliasIdentifier().getAlias(fromTime, toTime);
        }
        return aliasIdentifier;
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
            /**
             * Refresh beacon transmitter alias identifier every 40 minutes
             */
            getTimer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    final long aliasIdentifier = getAliasIdentifier();
                    if (aliasIdentifier != -1) {
                        getBeaconTransmitter().setId(aliasIdentifier);
                    }
                }
            }, 0, 60000); //Math.round(getGlobalStatusLog().getExposureDurationThreshold() * 1.5 * 60 * 1000));
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
    public final static void startGlobalStatusLogAutomaticUpdate() {
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
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 2000, globalStatusLogUpdateTask);
        }
    }

    /**
     * Cancel daily task to update global status log.
     */
    public final static void stopGlobalStatusLogAutomaticUpdate() {
        if (globalStatusLogUpdateTask != null) {
            final AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(globalStatusLogUpdateTask);
            globalStatusLogUpdateTask = null;
        }
    }

}
