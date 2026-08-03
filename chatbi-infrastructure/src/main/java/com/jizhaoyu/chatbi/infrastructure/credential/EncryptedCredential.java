package com.jizhaoyu.chatbi.infrastructure.credential;

import java.util.Arrays;
import java.util.Objects;

public final class EncryptedCredential {
    private final String keyId;
    private final byte[] nonce;
    private final byte[] ciphertext;

    public EncryptedCredential(String keyId, byte[] nonce, byte[] ciphertext) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("CREDENTIAL_ENVELOPE_INVALID");
        }
        this.keyId = keyId;
        this.nonce = Objects.requireNonNull(nonce, "nonce").clone();
        this.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext").clone();
    }

    public String keyId() {
        return keyId;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof EncryptedCredential other)) {
            return false;
        }
        return keyId.equals(other.keyId)
                && Arrays.equals(nonce, other.nonce)
                && Arrays.equals(ciphertext, other.ciphertext);
    }

    @Override
    public int hashCode() {
        int result = keyId.hashCode();
        result = 31 * result + Arrays.hashCode(nonce);
        result = 31 * result + Arrays.hashCode(ciphertext);
        return result;
    }

    @Override
    public String toString() {
        return "EncryptedCredential[keyId=" + keyId + ", nonce=<redacted>, ciphertext=<redacted>]";
    }
}
