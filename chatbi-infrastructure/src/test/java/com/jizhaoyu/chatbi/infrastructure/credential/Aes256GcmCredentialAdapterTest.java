package com.jizhaoyu.chatbi.infrastructure.credential;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class Aes256GcmCredentialAdapterTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DATA_SOURCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CREDENTIAL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final CredentialAad AAD = new CredentialAad(
            TENANT_ID, DATA_SOURCE_ID, CREDENTIAL_ID, "data-source-password", 7);
    private static final String SECRET_TEXT = "sensitive-password-value";

    @Test
    void roundTripsCredentialWithExplicitEnvelopeFields() {
        CredentialEncryptionKey key = key("current-key", (byte) 1);
        Aes256GcmCredentialAdapter adapter = new Aes256GcmCredentialAdapter(key);

        EncryptedCredential encrypted = adapter.encrypt(CredentialSecret.of(SECRET_TEXT), AAD);

        assertThat(encrypted.keyId()).isEqualTo("current-key");
        assertThat(encrypted.nonce()).hasSize(12);
        assertThat(encrypted.ciphertext()).hasSize(SECRET_TEXT.getBytes(StandardCharsets.UTF_8).length + 16);
        assertThat(adapter.decrypt(encrypted, AAD).reveal()).isEqualTo(SECRET_TEXT);
    }

    @Test
    void usesFreshRandomNonceForEveryEncryption() {
        Aes256GcmCredentialAdapter adapter = new Aes256GcmCredentialAdapter(key("current-key", (byte) 2));
        CredentialSecret secret = CredentialSecret.of(SECRET_TEXT);

        EncryptedCredential first = adapter.encrypt(secret, AAD);
        EncryptedCredential second = adapter.encrypt(secret, AAD);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void decryptsLegacyEnvelopeWhileEncryptingWithActiveKey() {
        CredentialEncryptionKey oldKey = key("old-key", (byte) 3);
        CredentialEncryptionKey activeKey = key("active-key", (byte) 4);
        EncryptedCredential legacy = new Aes256GcmCredentialAdapter(oldKey)
                .encrypt(CredentialSecret.of(SECRET_TEXT), AAD);
        Aes256GcmCredentialAdapter rotatingAdapter = new Aes256GcmCredentialAdapter(activeKey, oldKey);

        EncryptedCredential current = rotatingAdapter.encrypt(CredentialSecret.of(SECRET_TEXT), AAD);

        assertThat(rotatingAdapter.decrypt(legacy, AAD).reveal()).isEqualTo(SECRET_TEXT);
        assertThat(current.keyId()).isEqualTo("active-key");
    }

    @Test
    void rejectsWrongKeyWithoutLeakingProviderOrSecretDetails() {
        EncryptedCredential encrypted = new Aes256GcmCredentialAdapter(key("shared-key-id", (byte) 5))
                .encrypt(CredentialSecret.of(SECRET_TEXT), AAD);
        Aes256GcmCredentialAdapter wrongAdapter = new Aes256GcmCredentialAdapter(
                key("shared-key-id", (byte) 6));

        assertDecryptionFailed(() -> wrongAdapter.decrypt(encrypted, AAD));
    }

    @Test
    void rejectsUnknownKeyIdWithStableFailure() {
        Aes256GcmCredentialAdapter adapter = new Aes256GcmCredentialAdapter(key("known-key", (byte) 7));
        EncryptedCredential encrypted = adapter.encrypt(CredentialSecret.of(SECRET_TEXT), AAD);
        EncryptedCredential unknownKeyEnvelope = new EncryptedCredential(
                "unknown-key", encrypted.nonce(), encrypted.ciphertext());

        assertDecryptionFailed(() -> adapter.decrypt(unknownKeyEnvelope, AAD));
    }

    @ParameterizedTest
    @MethodSource("mismatchedAad")
    void rejectsEveryExchangedAadField(CredentialAad mismatchedAad) {
        Aes256GcmCredentialAdapter adapter = new Aes256GcmCredentialAdapter(key("current-key", (byte) 8));
        EncryptedCredential encrypted = adapter.encrypt(CredentialSecret.of(SECRET_TEXT), AAD);

        assertDecryptionFailed(() -> adapter.decrypt(encrypted, mismatchedAad));
    }

    @Test
    void rejectsTamperedNonceWithStableFailure() {
        Aes256GcmCredentialAdapter adapter = new Aes256GcmCredentialAdapter(key("current-key", (byte) 9));
        EncryptedCredential encrypted = adapter.encrypt(CredentialSecret.of(SECRET_TEXT), AAD);
        byte[] tamperedNonce = encrypted.nonce();
        tamperedNonce[0] ^= 1;

        EncryptedCredential tampered = new EncryptedCredential(
                encrypted.keyId(), tamperedNonce, encrypted.ciphertext());

        assertDecryptionFailed(() -> adapter.decrypt(tampered, AAD));
    }

    @Test
    void rejectsTamperedCiphertextWithStableFailure() {
        Aes256GcmCredentialAdapter adapter = new Aes256GcmCredentialAdapter(key("current-key", (byte) 10));
        EncryptedCredential encrypted = adapter.encrypt(CredentialSecret.of(SECRET_TEXT), AAD);
        byte[] tamperedCiphertext = encrypted.ciphertext();
        tamperedCiphertext[tamperedCiphertext.length - 1] ^= 1;

        EncryptedCredential tampered = new EncryptedCredential(
                encrypted.keyId(), encrypted.nonce(), tamperedCiphertext);

        assertDecryptionFailed(() -> adapter.decrypt(tampered, AAD));
    }

    @Test
    void rejectsMalformedEnvelopeWithStableFailure() {
        Aes256GcmCredentialAdapter adapter = new Aes256GcmCredentialAdapter(key("current-key", (byte) 11));
        EncryptedCredential malformed = new EncryptedCredential(
                "current-key", new byte[11], new byte[15]);

        assertDecryptionFailed(() -> adapter.decrypt(malformed, AAD));
    }

    @Test
    void envelopeDefensivelyCopiesNonceAndCiphertext() {
        byte[] nonce = new byte[12];
        byte[] ciphertext = new byte[16];
        nonce[0] = 1;
        ciphertext[0] = 2;
        EncryptedCredential encrypted = new EncryptedCredential("key-id", nonce, ciphertext);

        nonce[0] = 9;
        ciphertext[0] = 9;
        byte[] returnedNonce = encrypted.nonce();
        byte[] returnedCiphertext = encrypted.ciphertext();
        returnedNonce[0] = 8;
        returnedCiphertext[0] = 8;

        assertThat(encrypted.nonce()[0]).isEqualTo((byte) 1);
        assertThat(encrypted.ciphertext()[0]).isEqualTo((byte) 2);
    }

    @Test
    void secretBearingValuesRedactToString() {
        String base64Key = base64Key((byte) 12);
        CredentialEncryptionKey key = new CredentialEncryptionKey("key-id", base64Key);
        CredentialSecret secret = CredentialSecret.of(SECRET_TEXT);
        EncryptedCredential encrypted = new Aes256GcmCredentialAdapter(key).encrypt(secret, AAD);
        String encodedNonce = Base64.getEncoder().encodeToString(encrypted.nonce());
        String encodedCiphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext());

        assertThat(key.toString()).contains("<redacted>").doesNotContain(base64Key);
        assertThat(secret.toString()).contains("<redacted>").doesNotContain(SECRET_TEXT);
        assertThat(encrypted.toString())
                .contains("<redacted>")
                .doesNotContain(SECRET_TEXT, encodedNonce, encodedCiphertext);
    }

    @Test
    void requiresExactlyThirtyTwoDecodedKeyBytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);
        String longKey = Base64.getEncoder().encodeToString(new byte[33]);

        assertKeyRejected(shortKey);
        assertKeyRejected(longKey);
        assertKeyRejected("not-valid-base64!");
    }

    private static Stream<CredentialAad> mismatchedAad() {
        return Stream.of(
                new CredentialAad(UUID.randomUUID(), DATA_SOURCE_ID, CREDENTIAL_ID, "data-source-password", 7),
                new CredentialAad(TENANT_ID, UUID.randomUUID(), CREDENTIAL_ID, "data-source-password", 7),
                new CredentialAad(TENANT_ID, DATA_SOURCE_ID, UUID.randomUUID(), "data-source-password", 7),
                new CredentialAad(TENANT_ID, DATA_SOURCE_ID, CREDENTIAL_ID, "connection-password", 7),
                new CredentialAad(TENANT_ID, DATA_SOURCE_ID, CREDENTIAL_ID, "data-source-password", 8));
    }

    private static CredentialEncryptionKey key(String keyId, byte marker) {
        return new CredentialEncryptionKey(keyId, base64Key(marker));
    }

    private static String base64Key(byte marker) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, marker);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void assertDecryptionFailed(ThrowingCallable operation) {
        Throwable failure = catchThrowable(operation);

        assertThat(failure)
                .isExactlyInstanceOf(CredentialDecryptionException.class)
                .hasMessage(CredentialDecryptionException.ERROR_CODE)
                .hasNoCause();
        assertThat(failure.toString()).doesNotContain(SECRET_TEXT, "AEADBadTag", "Tag mismatch");
    }

    private static void assertKeyRejected(String encodedKey) {
        Throwable failure = catchThrowable(() -> new CredentialEncryptionKey("key-id", encodedKey));

        assertThat(failure)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("CREDENTIAL_ENCRYPTION_KEY_INVALID")
                .hasNoCause();
        assertThat(failure.toString()).doesNotContain(encodedKey);
    }
}
