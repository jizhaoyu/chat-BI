package com.jizhaoyu.chatbi.infrastructure.identity;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Configuration
@EnableConfigurationProperties(BootstrapIdentityProperties.class)
public class BootstrapIdentityConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.bootstrap-identity.enabled", havingValue = "true")
    ApplicationRunner bootstrapIdentity(
            BootstrapIdentityProperties properties,
            @Qualifier("platformJdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("platformTransactionManager") PlatformTransactionManager transactionManager,
            PasswordEncoder encoder) {
        return arguments -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (properties.password() == null || properties.password().length() < 12) {
                throw new IllegalStateException("BOOTSTRAP_IDENTITY_PASSWORD_TOO_SHORT");
            }
            UUID tenantId = jdbc.query("SELECT id FROM tenant WHERE name = ?", (rs, row) -> UUID.fromString(rs.getString(1)), properties.tenantName())
                    .stream().findFirst().orElseGet(() -> {
                        UUID id = UUID.randomUUID();
                        jdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?)", id.toString(), properties.tenantName());
                        return id;
                    });
            UUID userId = jdbc.query("SELECT id FROM app_user WHERE tenant_id = ? AND username = ?",
                    (rs, row) -> UUID.fromString(rs.getString(1)), tenantId.toString(), properties.username())
                    .stream().findFirst().orElseGet(() -> {
                        UUID id = UUID.randomUUID();
                        jdbc.update("INSERT INTO app_user(id, tenant_id, username, password_hash) VALUES (?, ?, ?, ?)",
                                id.toString(), tenantId.toString(), properties.username(), encoder.encode(properties.password()));
                        return id;
                    });
            jdbc.update("INSERT IGNORE INTO app_user_role(user_id, role_name) VALUES (?, 'DATA_ADMIN')", userId.toString());
        });
    }
}
