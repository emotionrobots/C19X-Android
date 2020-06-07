package org.c19x.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.c19x.data.type.Registration;
import org.c19x.data.type.RegistrationState;
import org.c19x.data.type.SerialNumber;
import org.c19x.data.type.SharedSecret;
import org.c19x.logic.AES;
import org.c19x.util.Logger;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class Settings {
    private final static String tag = Settings.class.getName();
    private final Context context;
    private final SecretKey secretKey;
    private SharedPreferences sharedPreferences;
    private final static String keyStatusValue = "Status.Value";
    private final static String keyContactsCount = "Contacts.Count";
    private final static String keyAdviceValue = "Advice.Value";
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
    private final static String keyTimestampTime = "Timestamp.Time";
    private final static String keyTimestampStatus = "Timestamp.Status";
    private final static String keyTimestampStatusRemote = "Timestamp.Status.Remote";
    private final static String keyTimestampContact = "Timestamp.Contact";
    private final static String keyTimestampAdvice = "Timestamp.Advice";
    private final static String keyTimestampMessage = "Timestamp.Message";
    private final static String keyTimestampSettings = "Timestamp.Settings";
    private final static String keyTimestampInfectionData = "Timestamp.InfectionData";


    public Settings(Context context) {
        this.context = context;
        secretKey = getMasterKey();
        sharedPreferences = context.getSharedPreferences("C19X", Context.MODE_PRIVATE);
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
                new SerialNumber(sharedSecretString),
                new SharedSecret(sharedSecretString));
        return registration;
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
        return AES.decrypt(secretKey, encrypted);
    }

    private void setEncrypted(final String value, final String forKey) {
        final String encrypted = AES.encrypt(secretKey, value);
        if (encrypted == null) {
            return;
        }
        set(encrypted, forKey);
    }

    /**
     * Get master key for encrypted app data.
     *
     * @return
     */
    private final static SecretKey getMasterKey() {
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
}
