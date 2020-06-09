package org.c19x.beacon;

import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.BeaconCodeSeed;
import org.c19x.data.type.Day;
import org.c19x.data.type.DayCode;
import org.c19x.data.type.SharedSecret;
import org.c19x.util.Logger;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;

public class ConcreteDayCodes implements DayCodes {
	private final static String tag = ConcreteDayCodes.class.getName();
	private final static long epoch = epoch();
	private final DayCode[] values;

	public ConcreteDayCodes(final SharedSecret sharedSecret) {
		final int days = 365 * 5;
		values = dayCodes(sharedSecret, days);
	}

	private final static long epoch() {
		try {
			final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			simpleDateFormat.setLenient(false);
			return simpleDateFormat.parse("2020-01-01 00:00").getTime();
		} catch (Throwable e) {
			Logger.warn(tag, "Failed to get epoch", e);
			return 0;
		}
	}

	private final static long longValue(final byte[] hash) {
		final ByteBuffer byteBuffer = ByteBuffer.wrap(hash);
		return byteBuffer.getLong(0);
	}

	private final static DayCode[] dayCodes(final SharedSecret sharedSecret, final int days) {
		final DayCode[] codes = new DayCode[days];
		try {
			final MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] hash = sha.digest(sharedSecret.value);
			for (int i = codes.length; i-- > 0; ) {
				codes[i] = new DayCode(longValue(hash));
				sha.reset();
				hash = sha.digest(hash);
			}
		} catch (Throwable e) {
			Logger.warn(tag, "Failed to get codes", e);
		}
		return codes;
	}

	private final static BeaconCodeSeed beaconCodeSeed(final DayCode dayCode, final Day day) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES);
		byteBuffer.putLong(0, dayCode.value);
		// Reverse bytes
		final byte[] data = byteBuffer.array();
		final byte[] reversed = new byte[]{data[7], data[6], data[5], data[4], data[3], data[2], data[1], data[0]};
		// Hash of reversed
		try {
            final MessageDigest sha = MessageDigest.getInstance("SHA-256");
            final byte[] hash = sha.digest(reversed);
            final long seed = longValue(hash);
            return new BeaconCodeSeed(seed);
        } catch (Throwable e) {
			Logger.warn(tag, "Failed to transform day code to beacon code seed", e);
			return null;
		}
	}

	@Override
	public Day day() {
		return new Day((int) ((System.currentTimeMillis() - epoch) / (24 * 60 * 60 * 1000)));
	}

	@Override
	public DayCode get() {
		try {
			return values[day().value];
		} catch (Throwable e) {
			Logger.warn(tag, "Day out of range");
			return null;
		}
	}

    @Override
    public Tuple<BeaconCodeSeed, Day> seed() {
        final Day day = day();
        try {
            final DayCode dayCode = values[day.value];
            final BeaconCodeSeed beaconCodeSeed = beaconCodeSeed(dayCode, day);
            return new Tuple<>(beaconCodeSeed, day);
        } catch (Throwable e) {
            Logger.warn(tag, "Day out of range");
            return null;
        }
    }
}
