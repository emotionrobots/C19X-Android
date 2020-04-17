package org.c19x.data;

import android.util.LongSparseArray;

import org.c19x.C19XApplication;
import org.c19x.beacon.BeaconListener;
import org.c19x.data.primitive.MutableLong;
import org.c19x.util.Logger;

import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Detection event rolling log
 */
public class DetectionEventLog extends BeaconListener {
    private final static String tag = DetectionEventLog.class.getName();

    // Number of milliseconds per hour and day
    private final static long millisPerHour = 60 * 60 * 1000;
    private final static long millisPerDay = 24 * millisPerHour;

    // Backup file names
    private final static String lastTimestampFile = "lastTimestampLog";
    private final static String dailyEncounterFile = "dailyEncounterLog";

    /**
     * Retention period (days) for total durations
     */
    private int retentionPeriodDays = 15;
    /**
     * Signal strength threshold (dBm) is based on the mean + standard deviation of signal strength
     * measurements at 2 meter distance presented in the paper :
     * Sekara, Vedran & Lehmann, Sune. (2014). The Strength of Friendship Ties in Proximity Sensor
     * Data. PloS one. 9. e100915. 10.1371/journal.pone.0100915.
     */
    private double signalStrengthThreshold = -82.03 - 4.57;
    /**
     * Contact episode threshold (millis) is the maximum time period between two encounters of the
     * same device id that is considered to be a period of contact, rather than two separate
     * encounters.
     */
    private long contactEpisodeThreshold = 5 * 60 * 1000;

    // Timestamp for most recent encounter of device id
    private final LongSparseArray<MutableLong> lastTimestampLog = new LongSparseArray<>();
    // Daily summary of close proximity encounter durations for each device id
    private final LongSparseArray<LongSparseArray<MutableLong>> dailyEncounterLog = new LongSparseArray<>();
    private final Lock logLock = new ReentrantLock(true);

    // Backup log once every 5 minutes
    private final static long automaticLogBackupTaskScheduleMillis = 5 * 60 * 1000;
    private TimerTask complyWithRetentionPeriodTask = null;


    public DetectionEventLog() {
        // Load data from internal storage
        restoreFromFile();
        // Start rolling log automatic deletion process
        setRetentionPeriod(retentionPeriodDays);
        // Start rolling log automatic backup process
        C19XApplication.getTimer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                new Thread(() -> backupToFile()).start();
            }
        }, automaticLogBackupTaskScheduleMillis, automaticLogBackupTaskScheduleMillis);
    }

    /**
     * Backup log to file on internal storage.
     */
    public boolean backupToFile() {
        complyWithRetentionPeriod();
        boolean result = true;
        logLock.lock();
        try {
            // Render last timestamp as CSV (identifer,timestamp)
            final StringBuilder lastTimestampCsv = new StringBuilder();
            for (int i = 0; i < lastTimestampLog.size(); i++) {
                lastTimestampCsv.append(lastTimestampLog.keyAt(i));
                lastTimestampCsv.append(',');
                lastTimestampCsv.append(lastTimestampLog.valueAt(i).value);
                lastTimestampCsv.append('\n');
            }
            // Render daily encounter log as CSV (identifier,duration) separated by #day line
            final StringBuilder dailyEncounterCsv = new StringBuilder();
            for (int i = 0; i < dailyEncounterLog.size(); i++) {
                dailyEncounterCsv.append('#');
                dailyEncounterCsv.append(dailyEncounterLog.keyAt(i));
                dailyEncounterCsv.append('\n');
                final LongSparseArray<MutableLong> dayEncounterLog = dailyEncounterLog.valueAt(i);
                for (int j = 0; j < dayEncounterLog.size(); j++) {
                    dailyEncounterCsv.append(dayEncounterLog.keyAt(j));
                    dailyEncounterCsv.append(',');
                    dailyEncounterCsv.append(dayEncounterLog.valueAt(j).value);
                    dailyEncounterCsv.append('\n');
                }
            }
            // Write logs to storage
            if (!C19XApplication.getStorage().atomicWriteText(lastTimestampCsv.toString(), lastTimestampFile)) {
                result = false;
            }
            if (!C19XApplication.getStorage().atomicWriteText(dailyEncounterCsv.toString(), dailyEncounterFile)) {
                result = false;
            }
            Logger.info(tag, "Backup detection event log successful");
        } catch (Throwable e) {
            Logger.warn(tag, "Backup failed", e);
        } finally {
            logLock.unlock();
        }
        return result;
    }

    /**
     * Restore log from file on internal storage.
     *
     * @return True if log file doe
     */
    public boolean restoreFromFile() {
        boolean result = true;
        logLock.lock();
        try {
            // Read last timestamp log from storage
            final LongSparseArray<MutableLong> newLastTimestampLog = new LongSparseArray<>();
            if (!C19XApplication.getStorage().atomicReadText(lastTimestampFile, line -> {
                try {
                    final int separator = line.indexOf(',');
                    if (separator != -1) {
                        newLastTimestampLog.put(
                                Long.parseLong(line.substring(0, separator)),
                                new MutableLong(Long.parseLong(line.substring(separator + 1))));
                    }
                } catch (Throwable e) {
                    Logger.warn(tag, "Failed to parse line (file={},line={})", lastTimestampFile, line, e);
                }
            })) {
                result = false;
            }

            // Read daily encounter log from storage
            final LongSparseArray<LongSparseArray<MutableLong>> newDailyEncounterLog = new LongSparseArray<>();
            if (!C19XApplication.getStorage().atomicReadText(dailyEncounterFile, new Consumer<String>() {
                private LongSparseArray<MutableLong> dayEncounterLog = null;

                @Override
                public void accept(String line) {
                    try {
                        if (line.length() > 0) {
                            if (line.charAt(0) == '#') {
                                // New day encounter log
                                final long day = Long.parseLong(line.substring(1));
                                dayEncounterLog = new LongSparseArray<>();
                                newDailyEncounterLog.put(day, dayEncounterLog);
                            } else {
                                final int separator = line.indexOf(',');
                                if (separator != -1) {
                                    if (dailyEncounterLog == null) {
                                        throw new Exception("Day encounter log has not been initialised");
                                    } else {
                                        dayEncounterLog.put(
                                                Long.parseLong(line.substring(0, separator)),
                                                new MutableLong(Long.parseLong(line.substring(separator + 1))));
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        Logger.warn(tag, "Failed to parse line (file={},line={})", dailyEncounterFile, line, e);
                    }
                }
            })) {
                result = false;
            }

            // Replace logs with restored data
            lastTimestampLog.clear();
            for (int i = 0; i < newLastTimestampLog.size(); i++) {
                lastTimestampLog.put(newLastTimestampLog.keyAt(i), newLastTimestampLog.valueAt(i));
            }
            dailyEncounterLog.clear();
            for (int i = 0; i < newDailyEncounterLog.size(); i++) {
                dailyEncounterLog.put(newDailyEncounterLog.keyAt(i), newDailyEncounterLog.valueAt(i));
            }
            Logger.info(tag, "Restore detection event log successful");
        } catch (Throwable e) {
            Logger.warn(tag, "Restore failed", e);
            result = false;
        } finally {
            logLock.unlock();
        }
        // Ensure compliance with retention period
        complyWithRetentionPeriod();
        return result;
    }

    /**
     * Set signal strength threshold for discarding devices that are too far away to be considered
     * as an instance of close contact.
     *
     * @param signalStrengthThreshold The signal strength in dBm of an event must be >= threshold to be considered a close contact.
     */
    public void setSignalStrengthThreshold(final double signalStrengthThreshold) {
        this.signalStrengthThreshold = signalStrengthThreshold;
        Logger.debug(tag, "Set signal strength threshold (threshold={})", signalStrengthThreshold);
    }

    /**
     * Set contact duration threshold for distinguishing a pair of detection events that represent a period
     * of contact, or distinct encounters.
     *
     * @param contactEpisodeThreshold The time period in milliseconds between two detection events must be <= threshold to be considered a period of contact.
     */
    public void setContactDurationThreshold(final int contactEpisodeThreshold) {
        this.contactEpisodeThreshold = contactEpisodeThreshold;
        Logger.debug(tag, "Set contact episode threshold (threshold={})", contactEpisodeThreshold);
    }

    @Override
    public synchronized void detect(final long timestamp, final long identifier, final float rssi) {
        if (rssi < signalStrengthThreshold) {
            Logger.debug(tag, "Detection event discarded, below signal strength threshold (timestamp={},id={},rssi={},threshold={})", timestamp, identifier, rssi, signalStrengthThreshold);
            return;
        }
        logLock.lock();
        try {
            // Check last timestamp
            final MutableLong lastTimestamp = lastTimestampLog.get(identifier, null);
            if (lastTimestamp == null) {
                // First encounter, just remember timestamp
                lastTimestampLog.put(identifier, new MutableLong(timestamp));
                Logger.debug(tag, "Detection event logged as initial contact (timestamp={},id={},rssi={})", timestamp, identifier, rssi);
            } else {
                // Repeated encounter, check elapsed time since last encounter
                final long contactDuration = timestamp - lastTimestamp.value;
                if (contactDuration < contactEpisodeThreshold) {
                    // Period of close encounter, accumulate period for device in daily log
                    final long today = (C19XApplication.getTimestamp().getTime() / millisPerDay) * millisPerDay;
                    // Get today's encounter log, or create one if doesn't exist
                    LongSparseArray<MutableLong> todayEncounterLog = dailyEncounterLog.get(today, null);
                    if (todayEncounterLog == null) {
                        todayEncounterLog = new LongSparseArray<>();
                        dailyEncounterLog.put(today, todayEncounterLog);
                    }
                    // Get today's total contact duration
                    MutableLong totalContactDuration = todayEncounterLog.get(identifier, null);
                    if (totalContactDuration == null) {
                        totalContactDuration = new MutableLong(0);
                        todayEncounterLog.put(identifier, totalContactDuration);
                    }
                    totalContactDuration.value += contactDuration;
                    // Update last timestamp for testing next encounter
                    lastTimestamp.value = timestamp;
                    Logger.debug(tag, "Detection event logged as contact episode (timestamp={},ownId={},id={},rssi={},contactDuration={},totalDuration={})", timestamp, C19XApplication.getBeaconTransmitter().getId(), identifier, rssi, contactDuration, totalContactDuration.value);
                } else {
                    // Separate instances of close encounter, just remember timestamp
                    lastTimestamp.value = timestamp;
                    Logger.debug(tag, "Detection event discarded, exceeds contact episode threshold (timestamp={},id={},rssi={},contactDuration={},threshold={})", timestamp, identifier, rssi, contactDuration, contactEpisodeThreshold);
                }
            }
        } finally {
            logLock.unlock();
        }
    }

    /**
     * Get total number of days covered by the detection log
     *
     * @return
     */
    public int getDays() {
        final long today = (C19XApplication.getTimestamp().getTime() / millisPerDay) * millisPerDay;
        long deltaMax = 0;
        for (int i = dailyEncounterLog.size(); i-- > 0; ) {
            final long delta = today - dailyEncounterLog.keyAt(i);
            if (delta > deltaMax) {
                deltaMax = delta;
            }
        }
        return (int) (deltaMax / millisPerDay) + 1;
    }

    /**
     * Get total duration of close contacts over log period.
     *
     * @return Map of device identifier and total close contact duration in milliseconds
     */
    public LongSparseArray<MutableLong> getContacts() {
        final LongSparseArray<MutableLong> sum = new LongSparseArray<>();
        complyWithRetentionPeriod();
        logLock.lock();
        try {
            for (int i = dailyEncounterLog.size(); i-- > 0; ) {
                final LongSparseArray<MutableLong> dayEncounterLog = dailyEncounterLog.valueAt(i);
                for (int j = dayEncounterLog.size(); j-- > 0; ) {
                    final long identifier = dayEncounterLog.keyAt(j);
                    final long duration = dayEncounterLog.valueAt(j).value;
                    MutableLong total = sum.get(identifier, null);
                    if (total == null) {
                        sum.put(identifier, new MutableLong(duration));
                    } else {
                        total.value += duration;
                    }
                }
            }
        } finally {
            logLock.unlock();
        }
        return sum;
    }

    /**
     * Set retention period for rolling log. The setting is immediately applied to the current log.
     * A recurring task is then executed every hour to ensure compliance.
     *
     * @param retentionPeriodDays
     */
    public void setRetentionPeriod(final int retentionPeriodDays) {
        if (complyWithRetentionPeriodTask != null) {
            complyWithRetentionPeriodTask.cancel();
        }
        complyWithRetentionPeriodTask = new TimerTask() {
            @Override
            public void run() {
                new Thread(() -> complyWithRetentionPeriod()).start();
            }
        };
        this.retentionPeriodDays = retentionPeriodDays;
        C19XApplication.getTimer().scheduleAtFixedRate(complyWithRetentionPeriodTask, 0, millisPerHour);
        Logger.debug(tag, "Set retention period (days={})", retentionPeriodDays);
    }

    /**
     * Delete last timestamp log and daily encounter log data exceeding retention period for compliance.
     */
    private void complyWithRetentionPeriod() {
        logLock.lock();
        try {
            // Calculate time limit based on retention
            final long today = (C19XApplication.getTimestamp().getTime() / millisPerDay) * millisPerDay;
            final long limit = today - (retentionPeriodDays * millisPerDay);
            // Clear daily encounter log
            for (int i = dailyEncounterLog.size(); i-- > 0; ) {
                if (dailyEncounterLog.keyAt(i) < limit) {
                    dailyEncounterLog.removeAt(i);
                }
            }
            // Clear last timestamp log
            for (int i = lastTimestampLog.size(); i-- > 0; ) {
                if (lastTimestampLog.valueAt(i).value < limit) {
                    lastTimestampLog.removeAt(i);
                }
            }
        } finally {
            logLock.unlock();
        }
    }

    /**
     * Close detection event log in preparation for graceful app shutdown.
     */
    public void close() {
        // Backup the log immediately
        backupToFile();
    }
}
