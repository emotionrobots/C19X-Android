package org.c19x;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import org.c19x.beacon.BeaconReceiver;
import org.c19x.beacon.BeaconTransmitter;
import org.c19x.beacon.ble3.BLEReceiver;
import org.c19x.beacon.ble3.BLETransmitter;
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
import org.c19x.util.security.SymmetricCipher;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Application and singletons.
 */
public class C19XApplication extends Application {
    private final static String tag = C19XApplication.class.getName();

    /**
     * Bluetooth beacon service Id
     */
    public final static long bluetoothLeServiceId = 928918273491243897l;
    public final static long bluetoothLeGattServiceId = 928918273491243898l;
    public final static String notificationChannelId = "241209384";
    /**
     * Anonymous ID range, set to range=2^N where range is close to a multiple of population size.
     */
    public final static int anonymousIdRange = 67108864;

    /**
     * Default application server on first use, this will be changed by downloaded updates.
     */
    public final static String defaultServer = "http://c19x.servehttp.com:8080";


    private static Application application;
    private static Context context;
    private static ConcurrentLinkedQueue<Activity> activities = new ConcurrentLinkedQueue<>();

    private static SharedPreferences preferences;
    private static Storage storage;
    private static Timer timer;
    private static Timestamp timestamp;
    private static HealthStatus healthStatus;
    private static DetectionEventLog detectionEventLog;
    private static GlobalStatusLog globalStatusLog;
    private static DeviceRegistration deviceRegistration;
    private static BluetoothStateMonitor bluetoothStateMonitor;
    private static BeaconTransmitter beaconTransmitter;
    private static BeaconReceiver beaconReceiver;
    private static NetworkClient networkClient;
    private static RiskAnalysis riskAnalysis;
    private static PendingIntent globalStatusLogUpdateTask;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
        context = getApplicationContext();
        createNotificationChannel();


        storage = getStorage();
        timer = getTimer();
        timestamp = getTimestamp();
        healthStatus = getHealthStatus();
        detectionEventLog = getDetectionEventLog();
        bluetoothStateMonitor = getBluetoothStateMonitor();
        riskAnalysis = getRiskAnalysis();
        startGlobalStatusLogAutomaticUpdate();
    }

    @Override
    public void onTerminate() {
        terminate();
        super.onTerminate();
    }

    public final static void terminate() {
        Logger.info(tag, "Application terminating");
        getBeaconReceiver().removeListener(getDetectionEventLog());
        getDetectionEventLog().close();
        getBluetoothStateMonitor().stop();
        stopGlobalStatusLogAutomaticUpdate();
        getTimer().cancel();
        Logger.info(tag, "Application terminated");
    }

    /**
     * Register activity on create to reliably keep track of whether the application is still alive.
     *
     * @param activity
     */
    public final static void registerOnCreate(final Activity activity) {
        if (!activities.contains(activity)) {
            activities.add(activity);
            Logger.debug(tag, "Registered activity (activity={},activities={})", activity, activities);
        }
    }

    public final static Activity getCurrentActivity() {
        if (activities.isEmpty()) {
            return null;
        } else {
            final Activity[] list = activities.toArray(new Activity[activities.size()]);
            return list[list.length - 1];
        }
    }

    /**
     * Unregister activity on destroy to reliably keep track of whether the application is still alive.
     *
     * @param activity
     */
    public final static void unregisterOnDestroy(final Activity activity) {
        if (activities.contains(activity)) {
            activities.remove(activity);
            Logger.debug(tag, "Unregistered activity (activity={},activities={})", activity, activities);
        }
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
        String value = defaultValue;
        if (getDeviceRegistration().isRegistered()) {
            try {
                final String base64Encrypted = preferences.getString(key, null);
                if (base64Encrypted != null) {
                    value = new String(SymmetricCipher.decrypt(getDeviceRegistration().getSharedSecretKey(), Base64.decode(base64Encrypted, Base64.DEFAULT)));
                }
            } catch (Throwable e) {
                Logger.warn(tag, "Failed to decrypt preference (key={})", key, e);
            }
        }
        Logger.debug(tag, "Get preference (key={},value={})", key, value);
        return value;
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
        if (getDeviceRegistration().isRegistered()) {
            try {
                final String base64Encrypted = Base64.encodeToString(SymmetricCipher.encrypt(getDeviceRegistration().getSharedSecretKey(), value.getBytes()), Base64.DEFAULT);
                final SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, base64Encrypted);
                editor.commit();
                Logger.debug(tag, "Set preference (key={},value={})", key, value);
            } catch (Throwable e) {
                Logger.warn(tag, "Failed to encrypt preference (key={})", key, e);
            }
        }
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
            beaconTransmitter.addListener(getDetectionEventLog());
            /**
             * Refresh beacon transmitter alias identifier every 20 minutes
             */
            getTimer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    final long aliasIdentifier = getAliasIdentifier();
                    if (aliasIdentifier != -1) {
                        getBeaconTransmitter().setId(aliasIdentifier);
                    }
                }
            }, 0, 20 * 60 * 1000);
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
            beaconReceiver.setDutyCycle(
                    getGlobalStatusLog().getBeaconReceiverOnDuration(),
                    getGlobalStatusLog().getBeaconReceiverOffDuration());
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
            // Update risk analysis regularly
            getTimer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    riskAnalysis.updateAssessment();
                }
            }, 0, 5 * 60 * 1000);
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
        getBeaconReceiver().setDutyCycle(
                getGlobalStatusLog().getBeaconReceiverOnDuration(),
                getGlobalStatusLog().getBeaconReceiverOffDuration());
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
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, updateTime.getTimeInMillis(), AlarmManager.INTERVAL_DAY, globalStatusLogUpdateTask);
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

    public final static void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final CharSequence name = getContext().getString(R.string.notification_channel_name);
            final String description = getContext().getString(R.string.notification_channel_description);
            final int importance = NotificationManager.IMPORTANCE_DEFAULT;
            final NotificationChannel channel = new NotificationChannel(notificationChannelId, name, importance);
            channel.setDescription(description);
            final NotificationManager notificationManager = getContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


}
