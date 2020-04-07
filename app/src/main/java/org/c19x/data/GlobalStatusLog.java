package org.c19x.data;

import org.c19x.C19XApplication;
import org.c19x.util.Logger;
import org.c19x.util.messaging.DefaultBroadcaster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Global status log for the most recent health status of every one. Please note
 * many of the entries are going to be decoys to maintain the privacy of
 * individuals.
 */
public class GlobalStatusLog extends DefaultBroadcaster<GlobalStatusLogListener> {
    private final static String tag = GlobalStatusLog.class.getName();

    // Byte buffer encoding scheme
    // 0..16 Digest (16-byte hash)
    // 16..24 Timestamp (8-byte long)
    // 24..32 IdentifierRange (8-byte long)
    // 32..33 GovernmentAdvice (1-byte byte)
    // 33..35 RetentionPeriod (2-byte short)
    // 35..39 SignalStrengthThreshold (4-byte double)
    // 39..43 ContactDurationThreshold (4-byte int)
    // 43..47 ExposureDurationThreshold (4-byte int)
    // 47..256 Reserved (209-byte data)
    // 256..384 ServerAddress (128-byte string)
    // 384.. HealthStatus (bit data)

    // Encoding scheme offset and length constants
    private final static int offDigest = 0, lenDigest = 16;
    private final static int offTimestamp = 16, lenTimestamp = 8;
    private final static int offIdentifierRange = 24, lenIdentifierRange = 8;
    private final static int offGovernmentAdvice = 32, lenGovernmentAdvice = 1;
    private final static int offRetentionPeriod = 33, lenRetentionPeriod = 2;
    private final static int offSignalStrengthThreshold = 35, lenSignalStrengthThreshold = 4;
    private final static int offContactDurationThreshold = 39, lenContactDurationThreshold = 4;
    private final static int offExposureDurationThreshold = 43, lenExposureDurationThreshold = 4;
    private final static int offReserved = 47, lenReserved = 209;
    private final static int offServerAddress = 256, lenServerAddress = 128;
    private final static int offHealthStatus = 384, lenHealthStatus = C19XApplication.anonymousIdRange / 8;

    // Byte buffer for log data
    private int byteBufferSize;
    private ByteBuffer byteBuffer;

    // Constants
    private final static int millisPerMinute = 60 * 1000;
    private final static byte[] bitmasks = new byte[]{Integer.valueOf("00000001", 2).byteValue(), 0,
            Integer.valueOf("00000010", 2).byteValue(), 0, Integer.valueOf("00000100", 2).byteValue(), 0,
            Integer.valueOf("00001000", 2).byteValue(), 0, Integer.valueOf("00010000", 2).byteValue(), 0,
            Integer.valueOf("00100000", 2).byteValue(), 0, Integer.valueOf("01000000", 2).byteValue(), 0,
            Integer.valueOf("10000000", 2).byteValue(), 0};

    /**
     * Create a new empty global status log
     */
    public GlobalStatusLog() {
        byteBufferSize = offHealthStatus + lenHealthStatus;
        byteBuffer = ByteBuffer.allocate(byteBufferSize);
        final byte zero = (byte) 0;
        for (int i = byteBufferSize; i-- > 0; ) {
            byteBuffer.put(i, zero);
        }
        setDefaultParameters();
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
        setSignalStrengthThreshold(-82.03 - 4.57);
        // Bluetooth discovery inquiry scan lasts for about 12 seconds, thus a multiple
        // of this value offers the sample period for consecutive timestamps, taking
        // into account signal drop outs.
        setContactDurationThreshold(5 * millisPerMinute);
        // Government advice
        setGovernmentAdvice(HealthStatus.STAY_AT_HOME);
        // Incubation of COVID-19 plus 1 day
        setRetentionPeriod((short) 15);
        // Exposure to infected person for over 30 minutes puts you at risk (Singapore
        // TraceTogether tracing criteria)
        setExposureDurationThreshold(30 * millisPerMinute);
        // Server address
        setServerAddress(C19XApplication.defaultServer);
    }

    /**
     * Create a global status log based on compressed data downloaded from the
     * server.
     *
     * @param compressedData
     * @throws Exception Downloaded data failed to be decoded.
     */
    private GlobalStatusLog(final byte[] compressedData) throws Exception {
        byteBuffer = ByteBuffer.wrap(decompress(compressedData));
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Generate compressed update data. The verification digest is set prior to
     * generation.
     *
     * @return Compressed update data.
     */
    public byte[] getUpdate() {
        byte[] update = null;
        try {
            if (setVerificationDigest()) {
                try {
                    update = compress(byteBuffer.array());
                } catch (Throwable e) {
                    Logger.warn(tag, "Update generation failed (exception)", e);
                }
            } else {
                Logger.warn(tag, "Update generation failed (cannot set verification digest)");
            }
        } finally {
        }
        return update;
    }

    /**
     * Update global status log with verified compressed update data.
     *
     * @param compressedData
     * @return True if update was successful.
     */
    public boolean setUpdate(final byte[] compressedData) {
        final long currentTimestamp = getTimestamp();
        boolean success = false;
        try {
            final GlobalStatusLog update = new GlobalStatusLog(compressedData);
            if (update.isVerified()) {
                try {
                    this.byteBuffer = update.byteBuffer;
                    success = true;
                } finally {
                }
            } else {
                Logger.warn(tag, "Update rejected because the content cannot be verified");
            }
        } catch (Throwable e) {
            Logger.warn(tag, "Update rejected because the content cannot be decompressed", e);
        }
        if (success) {
            final long newTimestamp = getTimestamp();
            broadcast(l -> l.updated(currentTimestamp, newTimestamp));
        }
        return success;
    }

    /**
     * Check integrity of log data by comparing the expected and actual MD5 message
     * digest.
     *
     * @return True means log data has been successfully verified.
     */
    private boolean isVerified() {
        // Lock unnecessary as it is only used by setUpdate
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byteBuffer.position(offDigest + lenDigest);
            messageDigest.update(byteBuffer);
            final byte[] actual = messageDigest.digest();
            final byte[] expected = new byte[lenDigest];
            byteBuffer.position(offDigest);
            byteBuffer.get(expected);
            return Arrays.equals(expected, actual);
        } catch (Throwable e) {
            Logger.warn(tag, "Message integrity verification failed", e);
            return false;
        }
    }

    /**
     * Set MD5 message digest for the current log data.
     *
     * @return True means digest was successfully computed and set.
     */
    private boolean setVerificationDigest() {
        // Lock unnecessary as it is only used by getUpdate
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byteBuffer.position(offDigest + lenDigest);
            messageDigest.update(byteBuffer);
            final byte[] digest = messageDigest.digest();
            byteBuffer.position(offDigest);
            byteBuffer.put(digest);
            return true;
        } catch (Throwable e) {
            Logger.warn(tag, "Set digest failed, defaulting to zero", e);
            byteBuffer.position(offDigest);
            byteBuffer.put(new byte[lenDigest]);
            return false;
        }
    }

    /**
     * Compress byte array.
     *
     * @param data
     * @return
     * @throws IOException
     */
    private final static byte[] compress(final byte[] data) throws IOException {
        final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        deflater.finish();
        final byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            final int count = deflater.deflate(buffer); // returns the generated code... index
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        final byte[] output = outputStream.toByteArray();
        // Logger.debug(tag, "Compressed data (original={},compressed={})", data.length,
        // output.length);
        return output;
    }

    /**
     * Decompress byte array.
     *
     * @param data
     * @return
     * @throws IOException
     * @throws DataFormatException
     */
    private final static byte[] decompress(final byte[] data) throws IOException, DataFormatException {
        final Inflater inflater = new Inflater();
        inflater.setInput(data);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        final byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            final int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        final byte[] output = outputStream.toByteArray();
        // Logger.debug(tag, "Decompressed data (compressed={},original={})",
        // data.length, output.length);
        return output;
    }

    // Thread-safe getters and setters for primitives
    // =============================================

    private long getLong(final int index) {
        long n = 0;
        try {
            n = byteBuffer.getLong(index);
        } finally {
        }
        return n;
    }

    private double getDouble(final int index) {
        double n = 0;
        try {
            n = byteBuffer.getDouble(index);
        } finally {
        }
        return n;
    }

    private int getInt(final int index) {
        int n = 0;
        try {
            n = byteBuffer.getInt(index);
        } finally {
        }
        return n;
    }

    private short getShort(final int index) {
        short n = 0;
        try {
            n = byteBuffer.getShort(index);
        } finally {
        }
        return n;
    }

    private byte getByte(final int index) {
        byte n = 0;
        try {
            n = byteBuffer.get(index);
        } finally {
        }
        return n;
    }

    public String getString(final int index) {
        String n = null;
        try {
            final int length = byteBuffer.getShort(index);
            final byte[] bytes = new byte[length];
            byteBuffer.position(index + 2);
            byteBuffer.get(bytes, 0, length);
            n = new String(bytes);
        } finally {
        }
        return n;
    }

    private void setLong(final int index, final long n) {
        try {
            byteBuffer.putLong(index, n);
        } finally {
        }
    }

    private void setDouble(final int index, final double n) {
        try {
            byteBuffer.putDouble(index, n);
        } finally {
        }
    }

    private void setInt(final int index, final int n) {
        try {
            byteBuffer.putInt(index, n);
        } finally {
        }
    }

    private void setShort(final int index, final short n) {
        try {
            byteBuffer.putShort(index, n);
        } finally {
        }
    }

    private void setByte(final int index, final byte n) {
        try {
            byteBuffer.put(index, n);
        } finally {
        }
    }

    public void setString(final int index, final String n) {
        try {
            byteBuffer.putShort(index, (short) n.length());
            byteBuffer.position(index + 2);
            final byte[] bytes = n.getBytes();
            byteBuffer.put(bytes, 0, bytes.length);
        } finally {
        }
    }

    // Getters and setters for log information
    // ====================================================

    // Getters

    /**
     * Log timestamp (millisecond since epoch), also used as version number
     **/
    public long getTimestamp() {
        return getLong(offTimestamp);
    }

    /**
     * Device identifier range (maximum identifier value)
     **/
    public long getIdentifierRange() {
        return getLong(offIdentifierRange);
    }

    /**
     * Goverment advice for default action (byte code)
     **/
    public byte getGovernmentAdvice() {
        return getByte(offGovernmentAdvice);
    }

    /**
     * Data retention duration (days), equal to disease incubation period
     **/
    public short getRetentionPeriod() {
        return getShort(offRetentionPeriod);
    }

    /**
     * Signal strength threshold (dBm) for distinguishing close proximity and far
     * away contact based on RSSI
     **/
    public double getSignalStrengthThreshold() {
        return getDouble(offSignalStrengthThreshold);
    }

    /**
     * Contact duration threshold (milliseconds) for distinguishing continuous and
     * distinct contact periods
     **/
    public int getContactDurationThreshold() {
        return getInt(offContactDurationThreshold);
    }

    /**
     * Exposure duration threshold (milliseconds) for disease transmission.
     **/
    public int getExposureDurationThreshold() {
        return getInt(offExposureDurationThreshold);
    }

    /**
     * Server address for next update
     **/
    public String getServerAddress() {
        return getString(offServerAddress);
    }

    // Setters

    /**
     * Log timestamp (millisecond since epoch), also used as version number
     **/
    public void setTimestamp(final long value) {
        setLong(offTimestamp, value);
    }

    /**
     * Device identifier range (maximum identifier value)
     **/
    public void setIdentifierRange(final long value) {
        setLong(offIdentifierRange, value);
    }

    /**
     * Goverment advice for default action (byte code)
     **/
    public void setGovernmentAdvice(final byte value) {
        setByte(offGovernmentAdvice, value);
    }

    /**
     * Data retention duration (days), equal to disease incubation period
     **/
    public void setRetentionPeriod(final short value) {
        setShort(offRetentionPeriod, value);
    }

    /**
     * Signal strength threshold (dBm) for distinguishing close proximity and far
     * away contact based on RSSI
     **/
    public void setSignalStrengthThreshold(final double value) {
        setDouble(offSignalStrengthThreshold, value);
    }

    /**
     * Contact duration threshold (milliseconds) for distinguishing continuous and
     * distinct contact periods
     **/
    public void setContactDurationThreshold(final int value) {
        setInt(offContactDurationThreshold, value);
    }

    /**
     * Exposure duration threshold (milliseconds) for disease transmission.
     **/
    public void setExposureDurationThreshold(final int value) {
        setInt(offExposureDurationThreshold, value);
    }

    /**
     * Server address for next update
     **/
    public void setServerAddress(final String value) {
        setString(offServerAddress, value);
    }

    /**
     * Get health status for a device identifer.
     *
     * @param identifier Device identifier.
     * @return True for infectious, false otherwise.
     */
    public boolean getHealthStatus(final long identifier) {
        boolean infectious = false;
        final int id = (int) (identifier % C19XApplication.anonymousIdRange);
        final int blockIndex = offHealthStatus + id / 8;
        final int bitIndex = id % 8;
        try {
            final byte block = byteBuffer.get(blockIndex);
            final byte masked = (byte) (block & bitmasks[bitIndex]);
            infectious = (((masked >> bitIndex) & bitmasks[0]) == 1);
        } finally {
        }
        return infectious;
    }

    /**
     * Set health status for a device identifier.
     *
     * @param identifier Device identifier.
     * @param infectious True for infectious, false otherwise.
     */
    public void setHealthStatus(final long identifier, final boolean infectious) {
        final int id = (int) (identifier % C19XApplication.anonymousIdRange);
        final int blockIndex = offHealthStatus + id / 8;
        final int bitIndex = id % 8;
        try {
            byte block = (byte) (byteBuffer.get(blockIndex) & ~bitmasks[bitIndex]);
            if (infectious) {
                block |= (1 << bitIndex);
            } else {
                block &= ~(1 << bitIndex);
            }
            byteBuffer.put(blockIndex, block);
        } finally {
        }
    }

}
