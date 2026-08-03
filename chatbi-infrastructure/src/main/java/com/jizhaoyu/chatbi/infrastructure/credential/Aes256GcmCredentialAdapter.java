package com.jizhaoyu.chatbi.infrastructure.credential;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Aes256GcmCredentialAdapter {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / Byte.SIZE;

    private final CredentialEncryptionKey activeKey;
    private final Map<String, CredentialEncryptionKey> keysById;
    private final SecureRandom secureRandom;

    public Aes256GcmCredentialAdapter(
            CredentialEncryptionKey activeKey,
            CredentialEncryptionKey... decryptionKeys) {
        this(activeKey, new SecureRandom(), decryptionKeys);
    }

    Aes256GcmCredentialAdapter(
            CredentialEncryptionKey activeKey,
            SecureRandom secureRandom,
            CredentialEncryptionKey... decryptionKeys) {
        this.activeKey = Objects.requireNonNull(activeKey, "activeKey");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");

        Map<String, CredentialEncryptionKey> keyring = new LinkedHashMap<>();
        addKey(keyring, activeKey);
        if (decryptionKeys != null) {
            for (CredentialEncryptionKey key : decryptionKeys) {
                addKey(keyring, Objects.requireNonNull(key, "decryptionKey"));
            }
        }
        this.keysById = Map.copyOf(keyring);
    }

    public EncryptedCredential encrypt(CredentialSecret secret, CredentialAad aad) {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(aad, "aad");

        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] plaintext = secret.utf8Bytes();
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, activeKey.secretKey(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad.encoded());
            return new EncryptedCredential(activeKey.keyId(), nonce, cipher.doFinal(plaintext));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("CREDENTIAL_ENCRYPTION_FAILED");
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    public CredentialSecret decrypt(EncryptedCredential encrypted, CredentialAad aad) {
        try {
            Objects.requireNonNull(encrypted, "encrypted");
            Objects.requireNonNull(aad, "aad");

            CredentialEncryptionKey key = keysById.get(encrypted.keyId());
            byte[] nonce = encrypted.nonce();
            byte[] ciphertext = encrypted.ciphertext();
            if (key == null || nonce.length != NONCE_BYTES || ciphertext.length < TAG_BYTES) {
                throw new CredentialDecryptionException();
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key.secretKey(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad.encoded());
            byte[] plaintext = cipher.doFinal(ciphertext);
            try {
                return CredentialSecret.of(new String(plaintext, java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new CredentialDecryptionException();
        }
    }

    private static void addKey(
            Map<String, CredentialEncryptionKey> keyring,
            CredentialEncryptionKey key) {
        if (keyring.putIfAbsent(key.keyId(), key) != null) {
            throw new IllegalArgumentException("CREDENTIAL_ENCRYPTION_KEY_ID_DUPLICATE");
        }
    }
}
