package org.c19x.data.type;

import android.util.Base64;

import java.util.Arrays;

public class SharedSecret {
    public byte[] value;

    public SharedSecret(byte[] value) {
        this.value = value;
    }

    public SharedSecret(String base64EncodedString) {
        this.value = Base64.decode(base64EncodedString, Base64.DEFAULT);
    }

    @Override
    public String toString() {
        return "SharedSecret{" +
                "value=" + Arrays.toString(value) +
                '}';
    }
}
