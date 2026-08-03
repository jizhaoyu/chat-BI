package com.jizhaoyu.chatbi.infrastructure.credential;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.datasource-credentials")
public record CredentialVaultProperties(String activeKeyId, String activeKeyBase64, String oldKeys) {
}
