package com.jizhaoyu.chatbi.infrastructure.credential;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public final class CredentialEncryptionKey {
    private static final int AES_256_KEY_BYTES = 32;
    private static final String INVALID_KEY = "CREDENTIAL_ENCRYPTION_KEY_INVALID";

    private final String keyId;
    private final SecretKey secretKey;

    public CredentialEncryptionKey(String keyId, String base64EncodedKey) {
        if (keyId == null || keyId.isBlank() || base64EncodedKey == null) {
            throw new IllegalArgumentException(INVALID_KEY);
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64EncodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(INVALID_KEY);
        }
        if (keyBytes.length != AES_256_KEY_BYTES) {
            throw new IllegalArgumentException(INVALID_KEY);
        }

        this.keyId = keyId;
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        java.util.Arrays.fill(keyBytes, (byte) 0);
    }

    public static CredentialEncryptionKey fromBase64(String keyId, String base64EncodedKey) {
        return new CredentialEncryptionKey(keyId, base64EncodedKey);
    }

    public String keyId() {
        return keyId;
    }

    SecretKey secretKey() {
        return secretKey;
    }

    @Override
    public String toString() {
        return "CredentialEncryptionKey[keyId=" + keyId + ", key=<redacted>]";
    }
}
