package org.c19x.util.security;

import org.c19x.util.Logger;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Diffie-Hellman key exchange for generating shared secret over unsecured channel
 */
public class KeyExchange {
    private final static String tag = KeyExchange.class.getName();
    private final static int keySize = 1024;
    private KeyAgreement aliceKeyAgree;
    private byte[] alicePublicKeyEncoded;
    private byte[] bobPublicKeyEncoded;
    private byte[] sharedSecret;
    private SecretKey sharedSecretKey;

    /**
     * Key exchange, playing the role of Alice, the initiator
     */
    public KeyExchange() throws Exception {
        // Alice creates her own DH key pair with 1024-bit key size
        Logger.debug(tag, "Alice generating DH key pair (size={})", keySize);
        final KeyPairGenerator aliceKpairGen = KeyPairGenerator.getInstance("DH");
        aliceKpairGen.initialize(keySize);
        final KeyPair aliceKpair = aliceKpairGen.generateKeyPair();

        // Alice creates and initializes her DH KeyAgreement object
        Logger.debug(tag, "Alice initialising key agreement object");
        aliceKeyAgree = KeyAgreement.getInstance("DH");
        aliceKeyAgree.init(aliceKpair.getPrivate());

        // Alice encodes her public key, and sends it over to Bob.
        final byte[] alicePubKeyEnc = aliceKpair.getPublic().getEncoded();
        this.alicePublicKeyEncoded = alicePubKeyEnc;
        Logger.debug(tag, "Alice generated key pair");
    }

    /**
     * Key exchange, playing the role of Bob, the responder
     *
     * @param alicePublicKeyEncoded
     * @throws Exception
     */
    public KeyExchange(final byte[] alicePublicKeyEncoded) throws Exception {
        this.alicePublicKeyEncoded = alicePublicKeyEncoded;

        // Bob has received Alice's public key in encoded format.
        // He instantiates a DH public key from the encoded key material.
        final KeyFactory bobKeyFac = KeyFactory.getInstance("DH");
        final X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(alicePublicKeyEncoded);
        final PublicKey alicePubKey = bobKeyFac.generatePublic(x509KeySpec);
        final DHParameterSpec dhParamFromAlicePubKey = ((DHPublicKey) alicePubKey).getParams();

        // Bob creates his own DH key pair
        final KeyPairGenerator bobKpairGen = KeyPairGenerator.getInstance("DH");
        bobKpairGen.initialize(dhParamFromAlicePubKey);
        final KeyPair bobKpair = bobKpairGen.generateKeyPair();

        // Bob creates and initializes his DH KeyAgreement object
        final KeyAgreement bobKeyAgree = KeyAgreement.getInstance("DH");
        bobKeyAgree.init(bobKpair.getPrivate());

        // Bob encodes his public key, and sends it over to Alice.
        final byte[] bobPubKeyEnc = bobKpair.getPublic().getEncoded();
        this.bobPublicKeyEncoded = bobPubKeyEnc;

        // Bob uses Alice's public key for DH key agreement
        bobKeyAgree.doPhase(alicePubKey, true);

        // Bob and Alice have completed the DH key agreement protocol
        final byte[] bobSharedSecret = bobKeyAgree.generateSecret();
        this.sharedSecret = bobSharedSecret;
        this.sharedSecretKey = new SecretKeySpec(sharedSecret, 0, 16, "AES");
    }

    /**
     * Key exchange, playing the role of Alice, accepting Bob's public key to complete key exchange.
     *
     * @param bobPublicKeyEncoded
     * @throws Exception
     */
    public void acceptBobPublicKey(final byte[] bobPublicKeyEncoded) throws Exception {
        this.bobPublicKeyEncoded = bobPublicKeyEncoded;

        // Alice uses Bob's public key for DH key agreement
        final KeyFactory aliceKeyFac = KeyFactory.getInstance("DH");
        final X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(bobPublicKeyEncoded);
        final PublicKey bobPubKey = aliceKeyFac.generatePublic(x509KeySpec);
        aliceKeyAgree.doPhase(bobPubKey, true);

        // Alice and Bob have completed the DH key agreement protocol
        final byte[] aliceSharedSecret = aliceKeyAgree.generateSecret();
        this.sharedSecret = aliceSharedSecret;
        this.sharedSecretKey = new SecretKeySpec(sharedSecret, 0, 16, "AES");
    }

    /**
     * Get Alice's public key in encoded format.
     *
     * @return
     */
    public byte[] getAlicePublicKey() {
        return alicePublicKeyEncoded;
    }

    /**
     * Get Bob's public key in encoded format.
     *
     * @return
     */
    public byte[] getBobPublicKey() {
        return bobPublicKeyEncoded;
    }

    /**
     * Get shared secret generated from key exchange.
     *
     * @return
     */
    public byte[] getSharedSecret() {
        return sharedSecret;
    }

    /**
     * Get shared secret key for AES crypto.
     *
     * @return
     */
    public SecretKey getSharedSecretKey() {
        return sharedSecretKey;
    }
}
