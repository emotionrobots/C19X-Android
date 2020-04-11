package org.c19x.util.security;

import org.c19x.util.Logger;

import java.math.BigInteger;
import java.text.SimpleDateFormat;

/**
 * Cryptographically generated alias device identifier for broadcasting by the bluetooth beacon.
 * This is based on concepts behind one-time-passcodes, where a sequence of hashes are generated
 * recursively from a seed then used in reverse to ensure it is cryptographically impossible to
 * predict the next value based on the current value.
 */
public class AliasIdentifier {
    private final static String tag = AliasIdentifier.class.getName();
    public final static long epoch = toMillis("2020-01-01 00:00");
    public final static int days = 365 * 10; // 10 years worth of daily aliases from epoch is more then plenty.
    private final static BigInteger aliasRange = new BigInteger(Long.toString(Long.MAX_VALUE));
    private final long[] identifiers = new long[days];
    private final PRNG prng = new PRNG();

    public AliasIdentifier() {
    }

    public AliasIdentifier(final byte[] sharedSecret) {
        initialise(sharedSecret);
    }

    /**
     * Get alias identifier to be introduced on a specific day.
     *
     * @param timestamp
     * @return
     */
    public synchronized long getAlias(final long timestamp) {
        final int day = timestampToDay(timestamp);
        if (day != -1) {
            return identifiers[day];
        } else {
            Logger.error(tag, "Alias out of range, contact app developer to change epoch (day={})", day);
            return -1;
        }
    }

    /**
     * Get randomly selected alias identifier for a time period.
     *
     * @param from From timestamp (inclusive)
     * @param to   To timestamp (inclusive)
     * @return
     */
    public synchronized long getAlias(final long from, final long to) {
        final int fromDay = timestampToDay(from);
        final int toDay = timestampToDay(to);
        if (fromDay != -1 && toDay != -1) {
            final int range = toDay - fromDay + 1;
            final int day = fromDay + prng.getInt(range);
            return identifiers[day];
        } else {
            Logger.error(tag, "Alias out of range, contact app developer to change epoch (fromDay={},toDay={})", fromDay, toDay);
            return -1;
        }
    }

    /**
     * Get alias identifiers for a time period.
     *
     * @param from From timestamp (inclusive)
     * @param to   To timestamp (inclusive)
     * @return
     */
    public synchronized long[] getAliases(final long from, final long to) {
        final int fromDay = timestampToDay(from);
        final int toDay = timestampToDay(to);
        if (fromDay != -1 && toDay != -1) {
            final long[] result = new long[toDay - fromDay + 1];
            System.arraycopy(identifiers, fromDay, result, 0, result.length);
            return result;
        } else {
            Logger.error(tag, "Alias out of range, contact app developer to change epoch (fromDay={},toDay={})", fromDay, toDay);
            return null;
        }
    }

    /**
     * Initialise alias identifier with shared secret to generate a forward sequence sequence of
     * alias identifiers, one per day.
     *
     * @param sharedSecret
     */
    private synchronized void initialise(final byte[] sharedSecret) {
        final SHA sha = new SHA();
        byte[] hash = sha.hash(sharedSecret);
        for (int i = identifiers.length; i-- > 0; ) {
            identifiers[i] = hashToLong(hash);
            hash = sha.hash(hash);
        }

    }

    /**
     * Convert timetamp to day since alias epoch.
     *
     * @param timestamp Timestamp in millis since system epoch.
     * @return Days since alias epoch, or -1 if out of range.
     */
    private final static int timestampToDay(final long timestamp) {
        final int day = (int) ((timestamp - epoch) / 86400000);
        return (day >= 0 && day < days ? day : -1);
    }

    /**
     * Convert date time in yyyy-MM-dd HH:mm format to millis.
     *
     * @param dateTime
     * @return
     */
    private final static long toMillis(final String dateTime) {
        try {
            final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            simpleDateFormat.setLenient(false);
            return simpleDateFormat.parse(dateTime).getTime();
        } catch (Exception e) {
            Logger.warn(tag, "Failed to parse date time, must be in yyyy-MM-dd HH:mm format", e);
            return -1;
        }
    }

    /**
     * Convert hash to long value as alias identifier.
     *
     * @param hash
     * @return
     */
    private final static long hashToLong(final byte[] hash) {
        final BigInteger integer = new BigInteger(hash);
        return integer.mod(aliasRange).longValue();
    }

}
