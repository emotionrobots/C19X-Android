package org.c19x.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.c19x.data.primitive.Triple;
import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.Advice;
import org.c19x.data.type.ContactPattern;
import org.c19x.data.type.ExposurePeriod;
import org.c19x.data.type.InfectionData;
import org.c19x.data.type.Message;
import org.c19x.data.type.RSSI;
import org.c19x.data.type.Registration;
import org.c19x.data.type.RegistrationState;
import org.c19x.data.type.RetentionPeriod;
import org.c19x.data.type.SerialNumber;
import org.c19x.data.type.ServerAddress;
import org.c19x.data.type.ServerSettings;
import org.c19x.data.type.SharedSecret;
import org.c19x.data.type.Status;
import org.c19x.data.type.Time;
import org.c19x.data.type.TimeInterval;
import org.c19x.data.type.TimeMillis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class Settings {
    private final static String tag = Settings.class.getName();
    private final static String keyStatusValue = "Status.Value";
    private final static String keyContactsCount = "Contacts.Count";
    private final static String keyContactsStatus = "Contacts.Status";
    private final static String keyContactPattern = "Contacts.Pattern";
    private final static String keyAdviceValue = "Advice.Value";
    private final static String keyAdviceMessage = "Advice.Message";
    private final static String keyRegistrationState = "Registration.State";
    private final static String keyRegistrationSerialNumber = "Registration.SerialNumber";
    private final static String keyRegistrationSharedSecret = "Registration.SharedSecret";
    private final static String keySettings = "Settings";
    private final static String keySettingsTimeDelta = "Settings.TimeDelta";
    private final static String keySettingsServer = "Settings.Server";
    private final static String keySettingsRetentionPeriod = "Settings.RetentionPeriod";
    private final static String keySettingsProximity = "Settings.Proximity";
    private final static String keySettingsExposure = "Settings.Exposure";
    private final static String keySettingsDefaultAdvice = "Settings.DefaultAdvice";
    private final static String keySettingsInfectionData = "Settings.InfectionData";
    private final static String keyTimestampTime = "Timestamp.Time";
    private final static String keyTimestampStatus = "Timestamp.Status";
    private final static String keyTimestampStatusRemote = "Timestamp.Status.Remote";
    private final static String keyTimestampContact = "Timestamp.Contact";
    private final static String keyTimestampAdvice = "Timestamp.Advice";
    private final static String keyTimestampMessage = "Timestamp.Message";
    private final static String keyTimestampSettings = "Timestamp.Settings";
    private final static String keyTimestampInfectionData = "Timestamp.InfectionData";

    private final Context context;
    private final SharedPreferences sharedPreferences;


    public Settings(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences("C19X", Context.MODE_PRIVATE);
    }

    public void reset() {
        Logger.debug(tag, "reset");
        remove(keyStatusValue);
        remove(keyContactsCount);
        remove(keyContactsStatus);
        remove(keyContactPattern);
        remove(keyAdviceValue);
        remove(keyAdviceMessage);
        remove(keyRegistrationState);
        remove(keyRegistrationSerialNumber);
        remove(keyRegistrationSharedSecret);
        remove(keySettings);
        remove(keySettingsTimeDelta);
        remove(keySettingsServer);
        remove(keySettingsRetentionPeriod);
        remove(keySettingsProximity);
        remove(keySettingsExposure);
        remove(keySettingsDefaultAdvice);
        remove(keyTimestampTime);
        remove(keyTimestampStatus);
        remove(keyTimestampStatusRemote);
        remove(keyTimestampContact);
        remove(keyTimestampAdvice);
        remove(keyTimestampMessage);
        remove(keyTimestampSettings);
        remove(keyTimestampInfectionData);
        remove(context, keySettingsInfectionData);
        removeMasterKey();
    }

    public void set(ServerSettings setTo) {
        final Time timestamp = new Time();
        set(setTo.json, keySettings);
        set(Long.toString(timestamp.value.getTime()), keyTimestampSettings);
        if (setTo.value.containsKey(keySettingsServer)) {
            server(new ServerAddress(setTo.value.get(keySettingsServer)));
        }
        if (setTo.value.containsKey("retention")) {
            final String value = Long.toString(Long.parseLong(setTo.value.get("retention")) * TimeInterval.day.value);
            set(value, keySettingsRetentionPeriod);
        }
        if (setTo.value.containsKey("proximity")) {
            final String value = Integer.toString(Integer.parseInt(setTo.value.get("proximity")));
            set(value, keySettingsProximity);
        }
        if (setTo.value.containsKey("exposure")) {
            final String value = Integer.toString(Integer.parseInt(setTo.value.get("exposure")));
            set(value, keySettingsExposure);
        }
        if (setTo.value.containsKey("advice")) {
            final String value = Advice.forValue(Integer.parseInt(setTo.value.get("advice"))).name();
            set(value, keySettingsDefaultAdvice);
        }
    }

    public Tuple<ServerSettings, Time> get() {
        final String serverSettingsString = get(keySettings);
        final String timestampString = get(keyTimestampSettings);
        if (serverSettingsString == null || timestampString == null) {
            return new Tuple<>(null, Time.distantPast);
        }
        final ServerSettings serverSettings = new ServerSettings(serverSettingsString);
        final Time timestamp = new Time(Long.parseLong(timestampString));
        return new Tuple<>(serverSettings, timestamp);
    }

    /**
     * Set registration state.
     */
    public void registrationState(RegistrationState setTo) {
        set(setTo.name(), keyRegistrationState);
    }

    /**
     * Get registration state.
     */
    public RegistrationState registrationState() {
        final String s = get(keyRegistrationState);
        if (s == null) {
            return RegistrationState.unregistered;
        }
        final RegistrationState state = RegistrationState.valueOf(s);
        return state;
    }

    /**
     * Set registration.
     */
    public void registration(SerialNumber serialNumber, SharedSecret sharedSecret) {
        setEncrypted(Long.toString(serialNumber.value), keyRegistrationSerialNumber);
        setEncrypted(Base64.encodeToString(sharedSecret.value, Base64.DEFAULT), keyRegistrationSharedSecret);
    }

    /**
     * Get registration.
     */
    public Registration registration() {
        final String serialNumberString = getEncrypted(keyRegistrationSerialNumber);
        final String sharedSecretString = getEncrypted(keyRegistrationSharedSecret);
        if (serialNumberString == null || sharedSecretString == null) {
            return null;
        }
        final Registration registration = new Registration(
                new SerialNumber(serialNumberString),
                new SharedSecret(sharedSecretString));
        return registration;
    }

    /**
     * Set time delta between device and server.
     */
    public void timeDelta(TimeMillis setTo) {
        final Time timestamp = new Time();
        set(Long.toString(setTo.value), keySettingsTimeDelta);
        set(Long.toString(timestamp.value.getTime()), keyTimestampTime);
    }

    /**
     * Get time delta.
     */
    public Tuple<TimeMillis, Time> timeDelta() {
        final String timeDeltaString = get(keySettingsTimeDelta);
        final String timestampString = get(keyTimestampTime);
        if (timeDeltaString == null || timestampString == null) {
            return new Tuple<>(new TimeMillis(0), Time.distantPast);
        }
        final TimeMillis timeDelta = new TimeMillis(Long.parseLong(timeDeltaString));
        final Time timestamp = new Time(Long.parseLong(timestampString));
        return new Tuple<>(timeDelta, timestamp);
    }

    /**
     * Set health status.
     */
    public Time status(Status setTo) {
        setEncrypted(setTo.name(), keyStatusValue);
        final Time timestamp = new Time();
        set(Long.toString(timestamp.value.getTime()), keyTimestampStatus);
        return timestamp;
    }

    /**
     * Set status remote timestamp for tracking whether synchronise status is required.
     */
    public void statusDidUpdateAtServer() {
        final Time timestamp = new Time();
        set(Long.toString(timestamp.value.getTime()), keyTimestampStatusRemote);
    }

    /**
     * Get health status and last update timestamp.
     */
    public Triple<Status, Time, Time> status() {
        final String statusString = getEncrypted(keyStatusValue);
        final String timestampString = get(keyTimestampStatus);
        if (statusString == null || timestampString == null) {
            return new Triple<>(Status.healthy, Time.distantPast, Time.distantPast);
        }
        final Status status = Status.valueOf(statusString);
        final Time timestamp = new Time(Long.parseLong(timestampString));
        final String remoteTimestampString = get(keyTimestampStatusRemote);
        if (remoteTimestampString == null) {
            return new Triple<>(status, timestamp, Time.distantPast);
        }
        final Time remoteTimestamp = new Time(Long.parseLong(remoteTimestampString));
        return new Triple<>(status, timestamp, remoteTimestamp);
    }

    /**
     * Set contact pattern.
     */
    public void pattern(ContactPattern setTo) {
        setEncrypted(setTo.value, keyContactPattern);
    }

    /**
     * Get contact pattern.
     */
    public ContactPattern pattern() {
        final String patternString = getEncrypted(keyContactPattern);
        if (patternString == null) {
            return new ContactPattern("");
        }
        return new ContactPattern(patternString);
    }

    /**
     * Set contacts count and last update timestamp
     */
    public Time contacts(int setTo) {
        final Time timestamp = new Time();
        set(Integer.toString(setTo), keyContactsCount);
        set(Long.toString(timestamp.value.getTime()), keyTimestampContact);
        return timestamp;
    }

    /**
     * Set contacts count and last update timestamp
     */
    public void contacts(int setTo, Time lastUpdate) {
        if (lastUpdate == null) {
            return;
        }
        set(Integer.toString(setTo), keyContactsCount);
        set(Long.toString(lastUpdate.value.getTime()), keyTimestampContact);
    }

    /**
     * Set contacts status and last update timestamp
     */
    public Time contacts(Status setTo) {
        setEncrypted(setTo.name(), keyContactsStatus);
        final String timestampString = get(keyTimestampContact);
        if (timestampString == null) {
            return Time.distantPast;
        }
        final Time timestamp = new Time(Long.parseLong(timestampString));
        return timestamp;
    }

    /**
     * Get contacts status, count and last update timestamp
     */
    public Triple<Integer, Status, Time> contacts() {
        final String countString = get(keyContactsCount);
        final String statusString = getEncrypted(keyContactsStatus);
        final String timestampString = get(keyTimestampContact);
        if (countString == null) {
            return new Triple<>(0, Status.healthy, Time.distantPast);
        }
        final int count = Integer.parseInt(countString);
        final Status status = (statusString == null ? Status.healthy : Status.valueOf(statusString));
        final Time timestamp = (timestampString == null ? Time.distantPast : new Time(Long.parseLong(timestampString)));
        return new Triple<>(count, status, timestamp);
    }

    /**
     * Set isolation advice.
     */
    public Time advice(Advice setTo) {
        setEncrypted(setTo.name(), keyAdviceValue);
        final Time timestamp = new Time();
        set(Long.toString(timestamp.value.getTime()), keyTimestampAdvice);
        return timestamp;
    }

    /**
     * Get isolation advice and last update timestamp.
     */
    public Triple<Advice, Advice, Time> advice() {
        Advice defaultAdvice = Advice.stayAtHome;
        final String defaultAdviceString = get(keySettingsDefaultAdvice);
        if (defaultAdviceString != null) {
            defaultAdvice = Advice.valueOf(defaultAdviceString);
        }
        final String adviceString = getEncrypted(keyAdviceValue);
        final String timestampString = get(keyTimestampAdvice);
        if (adviceString == null || timestampString == null) {
            return new Triple<>(defaultAdvice, Advice.stayAtHome, Time.distantPast);
        }
        final Advice advice = Advice.valueOf(adviceString);
        final Time timestamp = new Time(Long.parseLong(timestampString));
        return new Triple<>(defaultAdvice, advice, timestamp);
    }

    /**
     * Set personal message.
     */
    public Time message(Message setTo) {
        setEncrypted(setTo.value, keyAdviceMessage);
        final Time timestamp = new Time();
        set(Long.toString(timestamp.value.getTime()), keyTimestampMessage);
        return timestamp;
    }

    /**
     * Get personal message and last update timestamp.
     */
    public Tuple<Message, Time> message() {
        final String messageString = getEncrypted(keyAdviceMessage);
        if (messageString == null) {
            return new Tuple<>(new Message(""), Time.distantPast);
        }
        final Message message = new Message(messageString);
        final String timestampString = get(keyTimestampMessage);
        final Time timestamp = new Time(Long.parseLong(timestampString));
        return new Tuple<>(message, timestamp);
    }

    /**
     * Set server address.
     */
    public void server(ServerAddress setTo) {
        set(setTo.value, keySettingsServer);
    }

    /**
     * Get server address.
     */
    public ServerAddress server() {
        final String value = get(keySettingsServer);
        if (value == null) {
            //return new ServerAddress("https://c19x-dev.servehttp.com/");
            return new ServerAddress("https://preprod.c19x.org/");
        }
        return new ServerAddress(value);
    }

    /**
     * Get retention period.
     */
    public RetentionPeriod retentionPeriod() {
        final String value = get(keySettingsRetentionPeriod);
        if (value == null) {
            return new RetentionPeriod(14 * TimeInterval.day.value);
        }
        final RetentionPeriod retentionPeriod = new RetentionPeriod(Long.parseLong(value));
        return retentionPeriod;
    }

    /**
     * Set infection data (stored in application support directory in clear text, as its public data)
     */
    public void infectionData(InfectionData setTo) {
        write(context, keySettingsInfectionData, setTo.json);
        final Time timestamp = new Time();
        set(Long.toString(timestamp.value.getTime()), keyTimestampInfectionData);
    }

    /**
     * Set infection data (stored in application support directory in clear text, as its public data)
     */
    public Tuple<InfectionData, Time> infectionData() {
        final String infectionDataString = read(context, keySettingsInfectionData);
        final String timestampString = get(keyTimestampInfectionData);
        if (infectionDataString == null || timestampString == null) {
            return new Tuple<>(new InfectionData(), Time.distantPast);
        }
        final InfectionData infectionData = new InfectionData(infectionDataString);
        final Time timestamp = new Time(Long.parseLong(timestampString));
        return new Tuple<>(infectionData, timestamp);
    }

    /**
     * Get proximity for disease transmission.
     */
    public RSSI proximity() {
        final String value = get(keySettingsProximity);
        if (value == null) {
            return new RSSI(-77);
        }
        return new RSSI(Integer.parseInt(value));
    }

    /**
     * Get exposure duration for disease transmission.
     */
    public ExposurePeriod exposure() {
        final String value = get(keySettingsExposure);
        if (value == null) {
            return new ExposurePeriod(15);
        }
        return new ExposurePeriod(Integer.parseInt(value));
    }

    // MARK:- Internal functions.

    private String get(final String forKey) {
        return sharedPreferences.getString(forKey, null);
    }

    private void set(final String value, final String forKey) {
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(forKey, value);
        editor.apply();
    }

    private void remove(final String forKey) {
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(forKey);
        editor.apply();
    }

    private String getEncrypted(final String forKey) {
        final String encrypted = get(forKey);
        if (encrypted == null) {
            return null;
        }
        return decrypt(getMasterKey(), encrypted);
    }

    private void setEncrypted(final String value, final String forKey) {
        final String encrypted = encrypt(getMasterKey(), value);
        if (encrypted == null) {
            return;
        }
        set(encrypted, forKey);
    }

    private final static String encrypt(final SecretKey masterKey, final String value) {
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey);
            final byte[] iv = cipher.getIV();
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final CipherOutputStream cipherOutputStream = new CipherOutputStream(byteArrayOutputStream, cipher);
            cipherOutputStream.write(value.getBytes());
            cipherOutputStream.flush();
            cipherOutputStream.close();
            final byte[] data = byteArrayOutputStream.toByteArray();
            return Base64.encodeToString(iv, Base64.DEFAULT) + "\t" + Base64.encodeToString(data, Base64.DEFAULT);
        } catch (Throwable e) {
            Logger.warn(tag, "Encrypt failed", e);
            return null;
        }
    }

    private final static String decrypt(final SecretKey masterKey, final String value) {
        try {
            final String[] fields = value.split("\t", 2);
            final byte[] iv = Base64.decode(fields[0], Base64.DEFAULT);
            final byte[] data = Base64.decode(fields[1], Base64.DEFAULT);
            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmParameterSpec);
            final CipherInputStream cipherInputStream = new CipherInputStream(byteArrayInputStream, cipher);
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int bytesRead;
            final byte[] buffer = new byte[1024];
            while ((bytesRead = cipherInputStream.read(buffer, 0, buffer.length)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            byteArrayOutputStream.flush();
            final byte[] byteArray = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();
            cipherInputStream.close();
            byteArrayInputStream.close();
            return new String(byteArray);
        } catch (Throwable e) {
            Logger.warn(tag, "Decrypt failed", e);
            return null;
        }
    }

    /**
     * Get master key for encrypted app data.
     *
     * @return
     */
    private final static synchronized SecretKey getMasterKey() {
        try {
            final String alias = "c19xMasterKey";
            final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (!keyStore.containsAlias(alias)) {
                Logger.debug(tag, "Generating master key");
                final KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                final KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(alias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build();
                keyGenerator.init(keyGenParameterSpec);
                final SecretKey masterKey = keyGenerator.generateKey();
                return masterKey;
            } else {
                Logger.debug(tag, "Getting existing master key");
                final SecretKey masterKey = ((KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null)).getSecretKey();
                return masterKey;
            }
        } catch (Throwable e) {
            Logger.error(tag, "Failed to get or create master key", e);
            return null;
        }
    }

    private final static synchronized void removeMasterKey() {
        try {
            final String alias = "c19xMasterKey";
            final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias);
                Logger.debug(tag, "Removed existing master key");
            }
        } catch (Throwable e) {
            Logger.error(tag, "Failed to remove master key", e);
        }
    }

    public final static boolean write(final Context context, final String filename, final String value) {
        try {
            final FileOutputStream fileOutputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
            fileOutputStream.write(value.getBytes());
            fileOutputStream.flush();
            fileOutputStream.close();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private final static String read(final Context context, final String filename) {
        try {
            final FileInputStream fileInputStream = context.openFileInput(filename);
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int bytesRead;
            final byte[] buffer = new byte[1024];
            while ((bytesRead = fileInputStream.read(buffer, 0, buffer.length)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            byteArrayOutputStream.flush();
            final byte[] bytes = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();
            fileInputStream.close();
            return new String(bytes);
        } catch (Throwable e) {
            return null;
        }
    }

    private final static boolean remove(final Context context, final String filename) {
        try {
            context.deleteFile(filename);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
