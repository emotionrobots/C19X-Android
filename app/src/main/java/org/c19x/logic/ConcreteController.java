package org.c19x.logic;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;

import org.c19x.beacon.ConcreteTransceiver;
import org.c19x.beacon.ReceiverDelegate;
import org.c19x.beacon.Transceiver;
import org.c19x.data.ConcreteDatabase;
import org.c19x.data.Database;
import org.c19x.data.Logger;
import org.c19x.data.Settings;
import org.c19x.data.primitive.Triple;
import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.BluetoothState;
import org.c19x.data.type.ContactPattern;
import org.c19x.data.type.ControllerState;
import org.c19x.data.type.InfectionData;
import org.c19x.data.type.Message;
import org.c19x.data.type.RSSI;
import org.c19x.data.type.Registration;
import org.c19x.data.type.RegistrationState;
import org.c19x.data.type.ServerSettings;
import org.c19x.data.type.Status;
import org.c19x.data.type.Time;
import org.c19x.data.type.TimeInterval;
import org.c19x.data.type.TimeMillis;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.stream.Collectors;

public class ConcreteController implements Controller, ReceiverDelegate {
    private final static String tag = ConcreteController.class.getName();
    private final Settings settings;
    private final Context context;
    private final Database database;
    private final Network network;
    private final RiskAnalysis riskAnalysis;
    private Transceiver transceiver;

    public ConcreteController(final Context context) {
        this.context = context;
        settings = new Settings(context);
        if (settings.registrationState() == RegistrationState.registering) {
            settings.registrationState(RegistrationState.unregistered);
        }
        network = new ConcreteNetwork(context, settings);
        database = new ConcreteDatabase(context, contacts -> {
        });
        riskAnalysis = new ConcreteRiskAnalysis();

        // TEST ONLY - REMOVE FOR PRODUCTION
        //reset();
//        database.remove(new Time().advanced(TimeInterval.day), contacts -> {});
    }

    public Settings settings() {
        return settings;
    }

    @Override
    public void reset() {
        settings.reset();
        database.remove(new Time().advanced(TimeInterval.day), contacts -> {
        });
    }

    @Override
    public void foreground() {
        Logger.debug(tag, "foreground");
        checkRegistration();
        initialiseTransceiver();
        applySettings();
        synchronise(false);
        delegates.forEach(d -> d.controller(ControllerState.foreground));
    }

    @Override
    public void background() {
        Logger.debug(tag, "background");
        delegates.forEach(d -> d.controller(ControllerState.background));
    }

    @Override
    public void synchronise(boolean immediately) {
        Logger.debug(tag, "synchronise (immediately={})", immediately);
        synchroniseTime(immediately);
        synchroniseStatus();
        synchroniseMessage(immediately);
        synchroniseSettings(immediately);
        synchroniseInfectionData(immediately);
    }

    @Override
    public void status(Status setTo) {
        final Triple<Status, Time, Time> settingsStatus = settings.status();
        Logger.debug(tag, "Set status (from={},to={})", settingsStatus.a, setTo);
        // Set status locally
        settings.status(setTo);
        applySettings();
        // Set status remotely
        synchroniseStatus();
    }

    @Override
    public void export() {
        Logger.debug(tag, "export");
        final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final String string = database.contacts.stream()
                .map(contact -> dateFormatter.format(contact.time.value) + "," + Integer.toString(contact.rssi.value))
                .collect(Collectors.joining("\n"));
        try {
            final File folder = new File(getRootFolder(context), "C19X");
            if (!folder.exists()) {
                folder.mkdirs();
                Logger.debug(tag, "Created folder (folder={})", folder);
            }
            final File file = new File(folder, "contacts.csv");
            final FileOutputStream fileOutputStream = new FileOutputStream(file);
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
            fileOutputStream.write(string.getBytes());
            fileOutputStream.flush();
            fileOutputStream.close();
            Logger.warn(tag, "Export successful (file={})", file);
        } catch (Throwable e) {
            Logger.warn(tag, "Export failed", e);
        }
    }

    /**
     * Get root folder for SD card or emulated external storage.
     *
     * @param context
     * @return
     */
    private final static File getRootFolder(final Context context) {
        // Get SD card or emulated external storage. By convention (really!?)
        // SD card is reported after emulated storage, so select the last folder
        final File[] externalMediaDirs = context.getExternalMediaDirs();
        if (externalMediaDirs.length > 0) {
            return externalMediaDirs[externalMediaDirs.length - 1];
        } else {
            return Environment.getExternalStorageDirectory();
        }
    }


    // MARK:- Internal functions

    /**
     * Register device if required.
     */
    private void checkRegistration() {
        Logger.debug(tag, "Registration (state={})", settings.registrationState());
        if (settings.registrationState() != RegistrationState.unregistered) {
            return;
        }
        Logger.debug(tag, "Registration required");
        settings.registrationState(RegistrationState.registering);
        network.getRegistration((serialNumber, sharedSecret, error) -> {
            if (serialNumber == null || sharedSecret == null || error != null) {
                settings.registrationState(RegistrationState.unregistered);
                Logger.warn(tag, "Registration failed (error={})", error);
                return;
            }
            settings.registration(serialNumber, sharedSecret);
            Logger.debug(tag, "Registration successful (serialNumber={})", serialNumber.value);
            delegates.forEach(d -> d.registration(serialNumber));

            // Tasks after registration
            initialiseTransceiver();
            synchroniseStatus();
            synchroniseMessage(true);
        });
    }

    /**
     * Initialise transceiver.
     */
    private void initialiseTransceiver() {
        if (transceiver != null) {
            return;
        }
        Logger.debug(tag, "Initialise transceiver");
        final Registration registration = settings.registration();
        if (registration == null) {
            Logger.warn(tag, "Initialise transceiver failed (error=unregistered)");
            return;
        }
        transceiver = new ConcreteTransceiver(context, registration.sharedSecret, new TimeInterval(120));
        transceiver.delegates.add(this);
        Logger.debug(tag, "Initialise transceiver successful (serialNumber={})", registration.serialNumber);
        delegates.forEach(d -> d.transceiver(transceiver));
    }

    /**
     * Synchronise time with server (once a day)
     */
    private void synchroniseTime(boolean immediately) {
        final Tuple<TimeMillis, Time> settingsTimeDelta = settings.timeDelta();
        if (!(immediately || settingsTimeDelta == null || oncePerDay(settingsTimeDelta.b))) {
            Logger.debug(tag, "Synchronise time deferred (timestamp={})", settingsTimeDelta.b);
            return;
        }
        Logger.debug(tag, "Synchronise time");
        network.synchroniseTime((timeDelta, error) -> {
            if (timeDelta == null || error != null) {
                Logger.warn(tag, "Synchronise time failed (error={})", error);
                return;
            }
            Logger.debug(tag, "Synchronise time successful");
            settings.timeDelta(timeDelta);
        });
    }

    /**
     * Synchronise status with server
     */
    private void synchroniseStatus() {
        final Triple<Status, Time, Time> settingsStatus = settings.status();
        final ContactPattern pattern = settings.pattern();
        if (settingsStatus == null || settingsStatus.b.equals(Time.distantPast)) {
            // Only share authorised status data (local time != distant past)
            return;
        }
        if (settingsStatus.c.value.getTime() >= settingsStatus.b.value.getTime()) {
            // Synchronised already (remote time >= local time)
            return;
        }
        Logger.debug(tag, "Synchronise status");
        final Registration registration = settings.registration();
        if (registration == null) {
            Logger.warn(tag, "Synchronise status failed (error=unregistered)");
            return;
        }
        network.postStatus(settingsStatus.a, pattern, registration.serialNumber, registration.sharedSecret, (status, error) -> {
            if (error != null) {
                Logger.warn(tag, "Synchronise status failed (error={})", error);
                return;
            }
            if (status == null || status != settingsStatus.a) {
                Logger.warn(tag, "Synchronise status failed (error=mismatch)");
                return;
            }
            Logger.debug(tag, "Synchronise status successful (remote={})", status);
            settings.statusDidUpdateAtServer();
        });
    }

    /**
     * Synchronise personal message with server (once a day)
     */
    private void synchroniseMessage(boolean immediately) {
        final Tuple<Message, Time> settingsMessage = settings.message();
        if (!(immediately || settingsMessage == null || oncePerDay(settingsMessage.b))) {
            Logger.debug(tag, "Synchronise message deferred (timestamp={})", settingsMessage.b);
            return;
        }
        Logger.debug(tag, "Synchronise message");
        final Registration registration = settings.registration();
        if (registration == null) {
            Logger.warn(tag, "Synchronise message failed (error=unregistered)");
            return;
        }
        network.getMessage(registration.serialNumber, registration.sharedSecret, (message, error) -> {
            if (message == null || error != null) {
                Logger.warn(tag, "Synchronise message failed (error={})", error);
                return;
            }
            settings.message(message);
            Logger.debug(tag, "Synchronise message successful");
            delegates.forEach(d -> d.message(message));
        });
    }

    /**
     * Synchronise settings with server (once a day)
     */
    private void synchroniseSettings(boolean immediately) {
        final Tuple<ServerSettings, Time> settingsServerSettings = settings.get();
        if (!(immediately || settingsServerSettings == null || oncePerDay(settingsServerSettings.b))) {
            Logger.debug(tag, "Synchronise settings deferred (timestamp={})", settingsServerSettings.b);
            return;
        }
        Logger.debug(tag, "Synchronise settings");
        network.getSettings((serverSettings, error) -> {
            if (serverSettings == null || error != null) {
                Logger.warn(tag, "Synchronise settings failed (error={})", error);
                return;
            }
            settings.set(serverSettings);
            Logger.debug(tag, "Synchronise settings successful");
            applySettings();
        });
    }

    /**
     * Synchronise infection data with server (once a day)
     */
    private void synchroniseInfectionData(boolean immediately) {
        final Tuple<InfectionData, Time> settingsInfectionData = settings.infectionData();
        if (!(immediately || settingsInfectionData == null || oncePerDay(settingsInfectionData.b))) {
            Logger.debug(tag, "Synchronise infection data deferred (timestamp={})", settingsInfectionData.b);
            return;
        }
        Logger.debug(tag, "Synchronise infection data");
        network.getInfectionData((infectionData, error) -> {
            if (infectionData == null || error != null) {
                Logger.warn(tag, "Synchronise infection data failed (error={})", error);
                return;
            }
            settings.infectionData(infectionData);
            Logger.debug(tag, "Synchronise infection data successful");
            applySettings();
        });
    }

    /**
     * Apply current settings now.
     */
    private void applySettings() {
        // Enforce retention period
        final Time removeBefore = new Time().subtractingTimeInterval(settings.retentionPeriod());
        database.remove(removeBefore, contacts -> {
            final Time timestamp = (contacts.size() == 0 ? Time.distantPast : contacts.getLast().time);
            settings.contacts(contacts.size(), timestamp);
            delegates.forEach(d -> d.database(contacts));

            // Conduct risk analysis
            riskAnalysis.advice(contacts, settings, (advice, contactStatus, exposureOverTime, exposureProximity) -> {
                settings.advice(advice);
                settings.contacts(contactStatus);
                settings.pattern(exposureProximity.contactPattern());
                delegates.forEach(d -> d.advice(advice, contactStatus));
            });
        });
    }

    /**
     * Tests whether daily update should be executed.
     */
    protected final static boolean oncePerDay(Time lastUpdate) {
        return oncePerDay(lastUpdate, new Time());
    }

    protected final static boolean oncePerDay(Time lastUpdate, Time now) {
        // Must update if over one day has elapsed
        if (lastUpdate.timeIntervalSince(now).value > TimeInterval.day.value) {
            return true;
        }
        // Otherwise update overnight only
        final int windowStart = 0, windowEnd = 6;
        final Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(now.value);
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (!(hour >= windowStart && hour < windowEnd)) {
            return false;
        }
        // Ensure update wasn't completely recently
        final TimeInterval window = new TimeInterval((windowEnd - windowStart) * 60 * 60);
        final TimeInterval elapsed = new TimeInterval(Math.abs(lastUpdate.timeIntervalSince(now).value));
        if (elapsed.value <= window.value) {
            return false;
        }
        return true;
    }


    // MARK:- ReceiverDelegate

    public void receiver(BeaconCode didDetect, RSSI rssi) {
        database.insert(new Time(), didDetect, rssi, contacts -> {
            final Time timestamp = settings.contacts(database.contacts.size());
            Logger.debug(tag, "Contact logged (code={},rssi={},count={},timestamp={})", didDetect.value, rssi.value, database.contacts.size(), timestamp);
            delegates.forEach(d -> d.transceiver(timestamp));
        });
    }

    public void receiver(BluetoothState didUpdateState) {
        Logger.debug(tag, "Bluetooth state updated (state={})", didUpdateState);
        delegates.forEach(d -> d.transceiver(didUpdateState));
    }

}
