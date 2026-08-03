package com.jizhaoyu.chatbi.infrastructure.credential;

import com.jizhaoyu.chatbi.application.datasource.CredentialVaultPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;
import java.util.UUID;

public final class JdbcCredentialVault implements CredentialVaultPort {
    private static final String PURPOSE = "data-source-password";
    private static final String REFERENCE_PREFIX = "credential/";

    private final JdbcTemplate jdbc;
    private final Aes256GcmCredentialAdapter cipher;

    public JdbcCredentialVault(JdbcTemplate jdbc, Aes256GcmCredentialAdapter cipher) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    @Override
    public String store(UUID tenantId, UUID dataSourceId, String secret) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(secret, "secret");
        UUID credentialId = UUID.randomUUID();
        Integer currentVersion = jdbc.query(
                "SELECT credential_version FROM data_source_credential "
                        + "WHERE tenant_id = ? AND data_source_id = ? "
                        + "ORDER BY credential_version DESC LIMIT 1 FOR UPDATE",
                result -> result.next() ? result.getInt(1) : null,
                tenantId.toString(), dataSourceId.toString());
        int version = currentVersion == null ? 1 : currentVersion + 1;
        CredentialAad aad = new CredentialAad(tenantId, dataSourceId, credentialId, PURPOSE, version);
        EncryptedCredential encrypted = cipher.encrypt(CredentialSecret.of(secret), aad);

        jdbc.update("UPDATE data_source_credential SET active = FALSE "
                        + "WHERE tenant_id = ? AND data_source_id = ? AND active = TRUE",
                tenantId.toString(), dataSourceId.toString());
        jdbc.update("INSERT INTO data_source_credential "
                        + "(id, tenant_id, data_source_id, credential_version, key_id, nonce, ciphertext, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)",
                credentialId.toString(), tenantId.toString(), dataSourceId.toString(), version,
                encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext());
        return REFERENCE_PREFIX + credentialId;
    }

    @Override
    public String resolve(UUID tenantId, UUID dataSourceId, String credentialRef) {
        UUID credentialId = parseReference(credentialRef);
        StoredCredential stored = jdbc.query(
                "SELECT credential_version, key_id, nonce, ciphertext FROM data_source_credential "
                        + "WHERE id = ? AND tenant_id = ? AND data_source_id = ? AND active = TRUE",
                result -> result.next()
                        ? new StoredCredential(result.getInt("credential_version"), result.getString("key_id"),
                                result.getBytes("nonce"), result.getBytes("ciphertext"))
                        : null,
                credentialId.toString(), tenantId.toString(), dataSourceId.toString());
        if (stored == null) {
            throw new IllegalArgumentException("CREDENTIAL_NOT_FOUND");
        }
        CredentialAad aad = new CredentialAad(
                tenantId, dataSourceId, credentialId, PURPOSE, stored.version());
        return cipher.decrypt(new EncryptedCredential(
                stored.keyId(), stored.nonce(), stored.ciphertext()), aad).reveal();
    }

    static UUID parseReference(String credentialRef) {
        if (credentialRef == null || !credentialRef.startsWith(REFERENCE_PREFIX)) {
            throw new IllegalArgumentException("CREDENTIAL_REFERENCE_INVALID");
        }
        try {
            return UUID.fromString(credentialRef.substring(REFERENCE_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("CREDENTIAL_REFERENCE_INVALID");
        }
    }

    private record StoredCredential(int version, String keyId, byte[] nonce, byte[] ciphertext) {
    }
}
