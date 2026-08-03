package com.jizhaoyu.chatbi.application.datasource;

import java.util.UUID;

public interface CredentialVaultPort {
    String store(UUID tenantId, UUID dataSourceId, String secret);

    default String resolve(UUID tenantId, UUID dataSourceId, String credentialRef) {
        throw new UnsupportedOperationException("CREDENTIAL_RESOLUTION_NOT_IMPLEMENTED");
    }
}
