package com.jizhaoyu.chatbi.infrastructure.credential;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record CredentialAad(
        UUID tenantId,
        UUID dataSourceId,
        UUID credentialId,
        String purpose,
        int version) {

    private static final byte[] DOMAIN = "chatbi/credential/aes-256-gcm/v1".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_PURPOSE_BYTES = 128;

    public CredentialAad {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(credentialId, "credentialId");
        if (purpose == null || purpose.isBlank()
                || purpose.getBytes(StandardCharsets.UTF_8).length > MAX_PURPOSE_BYTES
                || version < 1) {
            throw new IllegalArgumentException("CREDENTIAL_AAD_INVALID");
        }
    }

    byte[] encoded() {
        byte[] purposeBytes = purpose.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(DOMAIN.length + (Long.BYTES * 6)
                + Integer.BYTES + purposeBytes.length + Integer.BYTES);
        buffer.put(DOMAIN);
        putUuid(buffer, tenantId);
        putUuid(buffer, dataSourceId);
        putUuid(buffer, credentialId);
        buffer.putInt(purposeBytes.length);
        buffer.put(purposeBytes);
        buffer.putInt(version);
        return buffer.array();
    }

    private static void putUuid(ByteBuffer buffer, UUID value) {
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
    }
}
