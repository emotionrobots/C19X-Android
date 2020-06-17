package org.c19x.beacon;

import org.c19x.data.Logger;
import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.BeaconCodeSeed;
import org.c19x.data.type.Day;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class ConcreteBeaconCodes implements BeaconCodes {
    private final static String tag = ConcreteBeaconCodes.class.getName();
    public final static int codesPerDay = 240;
    private final SecureRandom secureRandom;
    private final DayCodes dayCodes;
    private BeaconCodeSeed seed = null;
    private BeaconCode[] values = null;

    public ConcreteBeaconCodes(final DayCodes dayCodes) {
        this.dayCodes = dayCodes;
        secureRandom = getSecureRandom();
        get();
    }

    @Override
    public BeaconCode get() {
        if (seed == null) {
            final Tuple<BeaconCodeSeed, Day> seedToday = dayCodes.seed();
            if (seedToday != null) {
                seed = seedToday.a;
            }
        }
        if (seed == null) {
            Logger.warn(tag, "No seed code available");
            return null;
        }
        final Tuple<BeaconCodeSeed, Day> seedToday = dayCodes.seed();
        if (seedToday == null) {
            Logger.warn(tag, "No seed code available");
            return null;
        }
        if (values == null || seed.value != seedToday.a.value) {
            Logger.debug(tag, "Generating beacon codes for new day (day={},seed={})", dayCodes.day().value, seedToday.a);
            seed = seedToday.a;
            values = beaconCodes(seed, codesPerDay);
        }
        if (values == null) {
            Logger.warn(tag, "No beacon code available");
            return null;
        }

        return values[secureRandom.nextInt(values.length)];
    }

    private final static long longValue(final byte[] hash) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(hash);
        return byteBuffer.getLong(0);
    }

    private final static SecureRandom getSecureRandom() {
        try {
            // Get an instance of the SUN SHA1 PRNG
            final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            // Securely seed
            final SecureRandom randomForSeeding = new SecureRandom();
            // NIST SP800-90A suggests 440 bits for SHA1 seed
            final byte[] seed = randomForSeeding.generateSeed(55);
            random.setSeed(seed);
            // Securely start
            random.nextBytes(new byte[256 + random.nextInt(1024)]);
            return random;
        } catch (Exception e) {
            Logger.error(tag, "Unable to initialise pseudo random number generator", e);
            return null;
        }
    }

    public final static BeaconCode[] beaconCodes(final BeaconCodeSeed beaconCodeSeed, final int count) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES);
        byteBuffer.putLong(0, beaconCodeSeed.value);
        final byte[] data = byteBuffer.array();
        final BeaconCode[] codes = new BeaconCode[count];
        try {
            final MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(data);
            for (int i = codes.length; i-- > 0; ) {
                codes[i] = new BeaconCode(longValue(hash));
                sha.reset();
                hash = sha.digest(hash);
            }
        } catch (Throwable e) {
            Logger.warn(tag, "Failed to get codes", e);
        }
        return codes;
    }

}
