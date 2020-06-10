package org.c19x.util.security;

import org.c19x.data.PRNG;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class AliasIdentifierTest {

    @Test
    public void random() {
        final PRNG prng = new PRNG();
        for (int i = 0; i < 1000; i++) {
            System.err.println(prng.getInt(1));
        }
    }

    @Test
    public void test() throws Exception {
        // Key exchange to generate shared secret
        final KeyExchange alice = new KeyExchange();
        final KeyExchange bob = new KeyExchange(alice.getAlicePublicKey());
        alice.acceptBobPublicKey(bob.getBobPublicKey());

        final byte[] aliceSharedSecret = alice.getSharedSecret();
        final byte[] bobSharedSecret = bob.getSharedSecret();

        assertArrayEquals(aliceSharedSecret, bobSharedSecret);

        // Generate aliases
        final AliasIdentifier aliceAliases = new AliasIdentifier(aliceSharedSecret);
        final AliasIdentifier bobAliases = new AliasIdentifier(bobSharedSecret);

        // Aliases should be the same for Alice and Bob
        for (long day = 0; day < AliasIdentifier.days; day++) {
            final long timestamp = AliasIdentifier.epoch + (day * 24 * 60 * 60 * 1000);
            assertTrue(aliceAliases.getAlias(timestamp) != 0);
            assertTrue(aliceAliases.getAlias(timestamp) != -1);
            assertEquals(aliceAliases.getAlias(timestamp), bobAliases.getAlias(timestamp));
            if (day < AliasIdentifier.days - 1) {
                assertNotEquals(aliceAliases.getAlias(timestamp + 24 * 60 * 60 * 1000), aliceAliases.getAlias(timestamp));
            }
        }

        assertEquals(2, aliceAliases.getAliases(AliasIdentifier.epoch, AliasIdentifier.epoch + (1 * 24 * 60 * 60 * 1000)).length);
    }
}
