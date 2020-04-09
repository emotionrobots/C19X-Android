package org.c19x.util.security;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class KeyExchangeTest {

    @Test
    public void test() throws Exception {
        final KeyExchange alice = new KeyExchange();
        final KeyExchange bob = new KeyExchange(alice.getAlicePublicKey());
        alice.acceptBobPublicKey(bob.getBobPublicKey());

        final byte[] aliceSharedSecret = alice.getSharedSecret();
        final byte[] bobSharedSecret = bob.getSharedSecret();

        assertArrayEquals(aliceSharedSecret, bobSharedSecret);
    }
}
