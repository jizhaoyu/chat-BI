package com.jizhaoyu.chatbi.infrastructure.credential;

import com.jizhaoyu.chatbi.application.datasource.CredentialVaultPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(CredentialVaultProperties.class)
public class CredentialVaultConfiguration {
    @Bean
    CredentialVaultPort credentialVaultPort(
            @Qualifier("platformJdbcTemplate") JdbcTemplate jdbcTemplate,
            CredentialVaultProperties properties) {
        CredentialEncryptionKey activeKey = new CredentialEncryptionKey(
                properties.activeKeyId(), properties.activeKeyBase64());
        List<CredentialEncryptionKey> oldKeys = parseOldKeys(properties.oldKeys());
        Aes256GcmCredentialAdapter cipher = new Aes256GcmCredentialAdapter(
                activeKey, oldKeys.toArray(CredentialEncryptionKey[]::new));
        return new JdbcCredentialVault(jdbcTemplate, cipher);
    }

    static List<CredentialEncryptionKey> parseOldKeys(String configuredKeys) {
        if (configuredKeys == null || configuredKeys.isBlank()) {
            return List.of();
        }
        List<CredentialEncryptionKey> keys = new ArrayList<>();
        for (String entry : configuredKeys.split(",")) {
            int separator = entry.indexOf(':');
            if (separator < 1 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("CREDENTIAL_ENCRYPTION_OLD_KEYS_INVALID");
            }
            keys.add(new CredentialEncryptionKey(
                    entry.substring(0, separator).trim(), entry.substring(separator + 1).trim()));
        }
        return List.copyOf(keys);
    }
}
