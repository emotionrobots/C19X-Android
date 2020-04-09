package org.c19x.util.security;

public class Encryption {
    //        /*
//         * Now let's create a SecretKey object using the shared secret
//         * and use it for encryption. First, we generate SecretKeys for the
//         * "AES" algorithm (based on the raw shared secret data) and
//         * Then we use AES in CBC mode, which requires an initialization
//         * vector (IV) parameter. Note that you have to use the same IV
//         * for encryption and decryption: If you use a different IV for
//         * decryption than you used for encryption, decryption will fail.
//         *
//         * If you do not specify an IV when you initialize the Cipher
//         * object for encryption, the underlying implementation will generate
//         * a random one, which you have to retrieve using the
//         * javax.crypto.Cipher.getParameters() method, which returns an
//         * instance of java.security.AlgorithmParameters. You need to transfer
//         * the contents of that object (e.g., in encoded format, obtained via
//         * the AlgorithmParameters.getEncoded() method) to the party who will
//         * do the decryption. When initializing the Cipher for decryption,
//         * the (reinstantiated) AlgorithmParameters object must be explicitly
//         * passed to the Cipher.init() method.
//         */
//        System.out.println("Use shared secret as SecretKey object ...");
//        SecretKeySpec bobAesKey = new SecretKeySpec(bobSharedSecret, 0, 16, "AES");
//        SecretKeySpec aliceAesKey = new SecretKeySpec(aliceSharedSecret, 0, 16, "AES");
//
//        /*
//         * Bob encrypts, using AES in CBC mode
//         */
//        Cipher bobCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
//        bobCipher.init(Cipher.ENCRYPT_MODE, bobAesKey);
//        byte[] cleartext = "This is just an example".getBytes();
//        byte[] ciphertext = bobCipher.doFinal(cleartext);
//
//        // Retrieve the parameter that was used, and transfer it to Alice in
//        // encoded format
//        byte[] encodedParams = bobCipher.getParameters().getEncoded();
//
//        /*
//         * Alice decrypts, using AES in CBC mode
//         */
//
//        // Instantiate AlgorithmParameters object from parameter encoding
//        // obtained from Bob
//        AlgorithmParameters aesParams = AlgorithmParameters.getInstance("AES");
//        aesParams.init(encodedParams);
//        Cipher aliceCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
//        aliceCipher.init(Cipher.DECRYPT_MODE, aliceAesKey, aesParams);
//        byte[] recovered = aliceCipher.doFinal(ciphertext);
//        if (!java.util.Arrays.equals(cleartext, recovered))
//            throw new Exception("AES in CBC mode recovered text is " +
//                    "different from cleartext");
//        System.out.println("AES in CBC mode recovered text is "
//                "same as cleartext");
//    }
//
//    /*
//     * Converts a byte to hex digit and writes to the supplied buffer
//     */
//    private static void byte2hex(byte b, StringBuffer buf) {
//        char[] hexChars = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
//                '9', 'A', 'B', 'C', 'D', 'E', 'F' };
//        int high = ((b & 0xf0) >> 4);
//        int low = (b & 0x0f);
//        buf.append(hexChars[high]);
//        buf.append(hexChars[low]);
//    }
//
//    /*
//     * Converts a byte array to hex string
//     */
//    private static String toHexString(byte[] block) {
//        StringBuffer buf = new StringBuffer();
//        int len = block.length;
//        for (int i = 0; i < len; i++) {
//            byte2hex(block[i], buf);
//            if (i < len-1) {
//                buf.append(":");
//            }
//        }
//        return buf.toString();
//    }

}
