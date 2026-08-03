package com.jizhaoyu.chatbi;

import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.application.datasource.DataSourceApplicationService;
import com.jizhaoyu.chatbi.application.datasource.DataSourceRepository;
import com.jizhaoyu.chatbi.application.datasource.CredentialVaultPort;
import com.jizhaoyu.chatbi.application.datasource.DataSourceLifecycleService;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourceConnectionProbe;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourcePool;
import com.jizhaoyu.chatbi.application.catalog.CatalogApplicationService;
import com.jizhaoyu.chatbi.application.catalog.CatalogMetadataReader;
import com.jizhaoyu.chatbi.application.catalog.CatalogPermissionRepository;
import com.jizhaoyu.chatbi.application.catalog.CatalogSnapshotRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
public class ApplicationConfiguration {
    @Bean
    DataSourceApplicationService dataSourceApplicationService(DataSourceRepository repository, AuditPort auditPort,
                                                               CredentialVaultPort credentialVault,
                                                               ExternalDataSourcePool externalPools) {
        return new DataSourceApplicationService(repository, auditPort, credentialVault, externalPools);
    }

    @Bean
    DataSourceLifecycleService dataSourceLifecycleService(
            DataSourceRepository repository,
            CredentialVaultPort credentialVault,
            ExternalDataSourceConnectionProbe probe,
            AuditPort auditPort) {
        return new DataSourceLifecycleService(repository, credentialVault, probe, auditPort);
    }

    @Bean
    CatalogApplicationService catalogApplicationService(
            CatalogSnapshotRepository snapshots,
            CatalogMetadataReader metadataReader,
            CatalogPermissionRepository permissions,
            AuditPort auditPort) {
        return new CatalogApplicationService(
                snapshots, metadataReader, permissions, auditPort, Clock.systemUTC());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
