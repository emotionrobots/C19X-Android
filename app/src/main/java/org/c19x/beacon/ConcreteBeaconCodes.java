package org.c19x.beacon;

import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.BeaconCodeSeed;
import org.c19x.data.type.Day;
import org.c19x.util.Logger;
import org.c19x.util.security.PRNG;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

public class ConcreteBeaconCodes implements BeaconCodes {
    private final static String tag = ConcreteBeaconCodes.class.getName();
    private final static int codesPerDay = 240;
    private final PRNG prng = new PRNG();
    private final DayCodes dayCodes;
    private BeaconCodeSeed seed = null;
    private BeaconCode[] values = null;

    public ConcreteBeaconCodes(final DayCodes dayCodes) {
        this.dayCodes = dayCodes;
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
            Logger.debug(tag, "Generating beacon codes for new day (day={})", dayCodes.day().value);
            seed = seedToday.a;
            values = beaconCodes(seed, codesPerDay);
        }
        if (values == null) {
            Logger.warn(tag, "No beacon code available");
            return null;
        }

        return values[prng.getInt(values.length)];
    }

    private final static long longValue(final byte[] hash) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(hash);
        return byteBuffer.getLong(0);
    }

    private final static BeaconCode[] beaconCodes(final BeaconCodeSeed beaconCodeSeed, final int count) {
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
