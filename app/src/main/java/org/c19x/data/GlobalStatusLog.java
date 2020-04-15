package org.c19x.data;

import org.c19x.C19XApplication;
import org.c19x.util.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Global status log for the most recent health status of every one. Please note
 * many of the entries are going to be decoys to maintain the privacy of
 * individuals.
 */
public class GlobalStatusLog {
	private final static String tag = GlobalStatusLog.class.getName();
	private final static int identifierRange = C19XApplication.anonymousIdRange;
	private final static String logFilename = "globalStatusLog";
	private ByteBuffer byteBuffer;
	private final List<GlobalStatusLogListener> listeners = new ArrayList<>();

	// Encoding scheme
	// 0..16 Digest (16-byte hash)
	// 16..24 Timestamp (8-byte long)
	// 24..25 GovernmentAdvice (1-byte byte)
	// 25..27 RetentionPeriod (2-byte short)
	// 27..31 SignalStrengthThreshold (4-byte float)
	// 31..35 ContactDurationThreshold (4-byte int)
	// 35..39 ExposureDurationThreshold (4-byte int)
	// 39..43 BeaconReceiverOnDuration (4-byte int)
	// 43..47 BeaconReceiverOffDuration (4-byte int)
	// 47..128 Reserved
	// 128..256 ServerAddress (128-byte string)
	// 256.. Infectious (bit data)

	// Constants
	private final static int minuteInMillis = 60 * 1000;

	/**
	 * Create a new empty global status log.
	 */
	public GlobalStatusLog() {
		byteBuffer = ByteBuffer.allocate(256 + identifierRange / 8 + ((identifierRange % 8) == 0 ? 0 : 1));
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		setDefaultParameters();
		// Load from file
		C19XApplication.getStorage().atomicRead(logFilename, data -> {
			Logger.debug(tag, "Loaded global status log from storage");
			setUpdate(data);
		});
	}

	/**
	 * Create a new global status log based on a copy of the given log.
	 *
	 * @param globalStatusLog
	 */
	public GlobalStatusLog(final GlobalStatusLog globalStatusLog) {
		byteBuffer = ByteBuffer.allocate(globalStatusLog.byteBuffer.capacity());
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		System.arraycopy(globalStatusLog.byteBuffer.array(), 0, byteBuffer.array(), 0, byteBuffer.capacity());
	}

	/**
	 * Set default parameters
	 */
	private void setDefaultParameters() {
		setTimestamp(System.currentTimeMillis());
		// Signal strength threshold is based on the mean + standard deviation of signal
		// strength measurements at 2 meter distance presented in the paper :
		// Sekara, Vedran & Lehmann, Sune. (2014). The Strength of Friendship Ties in
		// Proximity Sensor Data. PloS one. 9. e100915. 10.1371/journal.pone.0100915.
		setSignalStrengthThreshold(-82.03f - 4.57f);
		// Bluetooth discovery inquiry scan lasts for about 12 seconds, thus a multiple
		// of this value offers the sample period for consecutive timestamps, taking
		// into account signal drop outs.
		setContactDurationThreshold(5 * minuteInMillis);
		// Government advice
		setGovernmentAdvice(HealthStatus.STAY_AT_HOME);
		// Incubation of COVID-19 plus 1 day
		setRetentionPeriod((short) 15);
		// Exposure to infected person for over 30 minutes puts you at risk (Singapore
		// TraceTogether tracing criteria)
		setExposureDurationThreshold(30 * minuteInMillis);
		// Beacon receiver duty cycle is 15 seconds ON, 85 seconds OFF (Singapore
		// TraceTogether duty cycle)
		setBeaconReceiverOnDuration(15000);
		setBeaconReceiverOffDuration(85000);
		// Server address
		setServerAddress(C19XApplication.defaultServer);
	}

	// 0..16 Digest (16-byte hash)
	// ================================================================

	/**
	 * Compute MD5 digest.
	 *
	 * @return 16-byte MD5 digest for data from byte 16 onwards.
	 */
	private final static byte[] computeDigest(final byte[] data) {
		try {
			final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
			messageDigest.update(data, 16, data.length - 16);
			return messageDigest.digest();
		} catch (Throwable e) {
			return null;
		}
	}

	/**
	 * Set MD5 message digest for current log data.
	 *
	 * @return True means digest was successfully computed and set.
	 */
	public boolean setDigest() {
		final byte[] digest = computeDigest(byteBuffer.array());
		if (digest != null) {
			System.arraycopy(digest, 0, byteBuffer.array(), 0, 16);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Check data integrity by comparing the expected and actual MD5 message digest.
	 *
	 * @return True means log data has been successfully verified.
	 */
	public boolean getDigest() {
		final byte[] actual = computeDigest(byteBuffer.array());
		if (actual != null) {
			for (int i = 0; i < 16; i++) {
				if (actual[i] != byteBuffer.array()[i]) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	// 16..24 Timestamp (8-byte long)
	// =============================================================

	/**
	 * Set log timestamp.
	 *
	 * @param n Timestamp in milliseconds from epoch
	 */
	public void setTimestamp(long n) {
		byteBuffer.putLong(16, n);
	}

	/**
	 * Get log timestamp.
	 *
	 * @return Timestamp in milliseconds from epoch
	 */
	public long getTimestamp() {
		return byteBuffer.getLong(16);
	}

	// 24..25 GovernmentAdvice (1-byte byte)
	// ======================================================

	/**
	 * Set government default advice.
	 *
	 * @param n Advice code
	 */
	public void setGovernmentAdvice(byte n) {
		byteBuffer.put(24, n);
	}

	/**
	 * Get government default advice.
	 *
	 * @return Advice code
	 */
	public byte getGovernmentAdvice() {
		return byteBuffer.get(24);
	}

	// 25..27 RetentionPeriod (2-byte short)
	// ============================================================================================

	/**
	 * Set close contact log retention period, should be 1 + disease incubation
	 * period.
	 *
	 * @param n Days
	 */
	public void setRetentionPeriod(short n) {
		byteBuffer.putShort(25, n);
	}

	/**
	 * Get close contact log retention period, should be 1 + disease incubation
	 * period.
	 *
	 * @return Days
	 */
	public short getRetentionPeriod() {
		return byteBuffer.getShort(25);
	}

	// 27..31 SignalStrengthThreshold (4-byte float)
	// ============================================================================================

	/**
	 * Set contact duration threshold for distinguishing one continuous or two
	 * distinct contacts based on time duration between beacon signals.
	 *
	 * @param n Signal strength in dBm.
	 */
	public void setSignalStrengthThreshold(float n) {
		byteBuffer.putFloat(27, n);
	}

	/**
	 * Get contact duration threshold for distinguishing one continuous or two
	 * distinct contacts based on time duration between beacon signals.
	 *
	 * @return Signal strength in dBm.
	 */
	public float getSignalStrengthThreshold() {
		return byteBuffer.getFloat(27);
	}

	// 31..35 ContactDurationThreshold (4-byte int)
	// ============================================================================================

	/**
	 * Set contact duration threshold for distinguishing one continuous or two
	 * distinct contacts based on time duration between beacon signals.
	 *
	 * @param n Duration in milliseconds.
	 */
	public void setContactDurationThreshold(int n) {
		byteBuffer.putInt(31, n);
	}

	/**
	 * Get contact duration threshold for distinguishing one continuous or two
	 * distinct contacts based on time duration between beacon signals.
	 *
	 * @return Duration in milliseconds.
	 */
	public int getContactDurationThreshold() {
		return byteBuffer.getInt(31);
	}

	// 35..39 ExposureDurationThreshold (4-byte int)
	// ============================================================================================

	/**
	 * Set exposure duration threshold for determining how much close contact time
	 * is required for disease transmission.
	 *
	 * @param n Duration in milliseconds.
	 */
	public void setExposureDurationThreshold(int n) {
		byteBuffer.putInt(35, n);
	}

	/**
	 * Get exposure duration threshold for determining how much close contact time
	 * is required for disease transmission.
	 *
	 * @return Duration in milliseconds.
	 */
	public int getExposureDurationThreshold() {
		return byteBuffer.getInt(35);
	}

	// 39..43 BeaconReceiverOnDuration (4-byte int)
	// ============================================================================================

	/**
	 * Set beacon receiver duty cycle (ON duration) for determining how long to scan for beacons.
	 * BlueTrace recommends 15-20% on time.
	 *
	 * @param n Duration in milliseconds.
	 */
	public void setBeaconReceiverOnDuration(int n) {
		byteBuffer.putInt(39, n);
	}

	/**
	 * Get beacon receiver duty cycle (ON duration) for determining how long to scan for beacons.
	 * BlueTrace recommends 15-20% on time.
	 *
	 * @return Duration in milliseconds.
	 */
	public int getBeaconReceiverOnDuration() {
		return byteBuffer.getInt(39);
	}

	// 43..47 BeaconReceiverOffDuration (4-byte int)
	// ============================================================================================

	/**
	 * Set beacon receiver duty cycle (OFF duration) for determining how long to scan for beacons.
	 * BlueTrace recommends 80-85% off time.
	 *
	 * @param n Duration in milliseconds.
	 */
	public void setBeaconReceiverOffDuration(int n) {
		byteBuffer.putInt(43, n);
	}

	/**
	 * Get beacon receiver duty cycle (OFF duration) for determining how long to scan for beacons.
	 * BlueTrace recommends 80-85% off time.
	 *
	 * @return Duration in milliseconds.
	 */
	public int getBeaconReceiverOffDuration() {
		return byteBuffer.getInt(43);
	}

	// 128..256 ServerAddress (128-byte string)
	// ============================================================================================

	/**
	 * Set server address for next update.
	 *
	 * @param address
	 */
	public void setServerAddress(final String address) {
		assert (address != null && address.length() < 126);
		final byte[] bytes = address.getBytes();
		byteBuffer.putShort(128, (short) bytes.length);
		System.arraycopy(bytes, 0, byteBuffer.array(), 130, bytes.length);
	}

	/**
	 * Get server address.
	 *
	 * @return Server address for next update.
	 */
	public String getServerAddress() {
		final byte[] bytes = new byte[byteBuffer.getShort(128)];
		System.arraycopy(byteBuffer.array(), 130, bytes, 0, bytes.length);
		return new String(bytes);
	}

	// 256..end Infectious (1-bit boolean)
	// ============================================================================================

	/**
	 * Set infectious status of id.
	 *
	 * @param identifier
	 * @param infectious
	 */
	public void setInfectious(final long identifier, final boolean infectious) {
		final int id = Math.abs((int) (identifier % identifierRange));
		final int blockIndex = 256 + (id / 8);
		final int bitIndex = id % 8;
		if (infectious) {
			byteBuffer.array()[blockIndex] |= (1 << bitIndex);
		} else {
			byteBuffer.array()[blockIndex] &= ~(1 << bitIndex);
		}
	}

	/**
	 * Get infectious status of id.
	 *
	 * @param identifier
	 * @return True if infectious.
	 */
	public boolean getInfectious(final long identifier) {
		final int id = Math.abs((int) (identifier % identifierRange));
		final int blockIndex = 256 + (id / 8);
		final int bitIndex = id % 8;
		return ((byteBuffer.array()[blockIndex] >> bitIndex) & 1) != 0;
	}

	// Log update
	// ============================================================================================

	/**
	 * Get update for current log.
	 *
	 * @return Update for used by setUpdate(), null on failure.
	 */
	public byte[] getUpdate() {
		byte[] update = null;
		if (setDigest()) {
			update = compress(byteBuffer.array());
		}
		return update;
	}

	/**
	 * Set log to update after verification.
	 *
	 * @param update
	 * @return True if successful, false otherwise
	 */
	public boolean setUpdate(final byte[] update) {
		assert (update != null);
		boolean success = false;
		try {
			final byte[] data = decompress(update);
			final byte[] digest = computeDigest(data);
			success = true;
			for (int i = 0; i < 16; i++) {
				if (digest[i] != data[i]) {
					success = false;
					break;
				}
			}
			final long previousVersion = getTimestamp();
			if (success) {
				byteBuffer = ByteBuffer.wrap(data);
				byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
				C19XApplication.getStorage().atomicWrite(update, logFilename);
				success = true;
			}
			final long currentVersion = getTimestamp();
			broadcast(l -> l.updated(previousVersion, currentVersion));
		} catch (Throwable e) {
		}
		return success;
	}

	// Messaging
	// ============================================================================================

	/**
	 * Add listener.
	 *
	 * @param listener
	 * @return
	 */
	public boolean addListener(GlobalStatusLogListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Remove listener.
	 *
	 * @param listener
	 * @return
	 */
	public boolean removeListener(GlobalStatusLogListener listener) {
		if (listeners.contains(listener)) {
			listeners.remove(listener);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Broadcast action to all listeners.
	 *
	 * @param action Action on listener.
	 */
	public void broadcast(Consumer<GlobalStatusLogListener> action) {
		listeners.forEach(action);
	}

	// Byte array compression
	// ============================================================================================

	/**
	 * Compress data.
	 *
	 * @param data Source data.
	 * @return Compressed data, or null on failure.
	 */
	public static byte[] compress(byte[] data) {
		final Deflater deflater = new Deflater();
		deflater.setLevel(Deflater.BEST_COMPRESSION);
		deflater.setInput(data);
		deflater.finish();
		try {
			final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(data.length);
			final byte[] buffer = new byte[1024];
			while (!deflater.finished()) {
				int count = deflater.deflate(buffer); // returns the generated code... index
				byteArrayOutputStream.write(buffer, 0, count);
			}
			byteArrayOutputStream.close();
			final byte[] compressedData = byteArrayOutputStream.toByteArray();
			return compressedData;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Decompress data.
	 *
	 * @param compressedData Compressed data.
	 * @return Source data, or null on failure.
	 */
	public static byte[] decompress(byte[] compressedData) {
		final Inflater inflater = new Inflater();
		inflater.setInput(compressedData);
		try {
			final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(compressedData.length);
			final byte[] buffer = new byte[1024];
			while (!inflater.finished()) {
				int count = inflater.inflate(buffer);
				byteArrayOutputStream.write(buffer, 0, count);
			}
			byteArrayOutputStream.close();
			final byte[] data = byteArrayOutputStream.toByteArray();
			return data;
		} catch (IOException | DataFormatException e) {
			return null;
		}
	}
}
