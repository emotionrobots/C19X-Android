package org.c19x.util.security;

import org.c19x.util.Logger;

import java.security.SecureRandom;

/**
 * Pseudo random number generator based on a securely seeded
 * instance of the SHA1 PRNG secure random algorithm.
 * <p><ul>
 * <li>Oracle Java SHA1 PRNG secure random algorithm.
 * </ul><p>
 * Performance in nanoseconds/byte (samples=1000, random output size=230400)
 * <p><ul>
 * <li>Generate random : median=15.526974230164884 (SD=1.0353219640696345)
 * </ul><p>
 */
public class PRNG {
    private final static String tag = PRNG.class.getName();
    private final SecureRandom random;

    public PRNG() {
        this.random = getSecureRandom();
    }

    /**
     * Get a securely seeded secure random number generator. The implementation
     * uses an instance of the SHA1 PRNG which is securely seeded using
     * a separate instance of secure random.
     *
     * @return
     */
    public final static SecureRandom getSecureRandom() {
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

    /**
     * Generate a random sequence of bytes.
     *
     * @param length
     * @return
     */
    public final byte[] getBytes(final int length) {
        final byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generate a random sequence of bytes.
     *
     * @param bytes Output array.
     */
    public final void getBytes(final byte[] bytes) {
        random.nextBytes(bytes);
    }

    /**
     * Generate a random integer [0,bound]
     *
     * @param bound Upper bound (exclusive).
     * @return
     */
    public final int getInt(final int bound) {
        return random.nextInt(bound);
    }
}

