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
import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalRepository;
import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalService;
import com.jizhaoyu.chatbi.application.sqlguard.SqlGuardPort;
import com.jizhaoyu.chatbi.application.sqlguard.SqlValidationService;
import com.jizhaoyu.chatbi.application.execution.ApprovedQueryExecutor;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionRepository;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionService;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionPreparationService;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionCompletionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;

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
    QueryApprovalService queryApprovalService(QueryApprovalRepository repository) {
        return new QueryApprovalService(
                repository, Clock.systemUTC(), Duration.ofMinutes(2), SqlValidationService.RULE_VERSION);
    }

    @Bean
    SqlValidationService sqlValidationService(
            DataSourceRepository dataSources,
            CatalogSnapshotRepository snapshots,
            CatalogPermissionRepository permissions,
            SqlGuardPort sqlGuard,
            QueryApprovalService approvals,
            AuditPort auditPort) {
        return new SqlValidationService(
                dataSources, snapshots, permissions, sqlGuard, approvals, auditPort, Clock.systemUTC());
    }

    @Bean
    QueryExecutionPreparationService queryExecutionPreparationService(
            QueryApprovalService approvals,
            AuditPort auditPort) {
        return new QueryExecutionPreparationService(approvals, auditPort, Clock.systemUTC());
    }

    @Bean
    QueryExecutionCompletionService queryExecutionCompletionService(
            QueryExecutionRepository executions, AuditPort auditPort) {
        return new QueryExecutionCompletionService(executions, auditPort, Clock.systemUTC());
    }

    @Bean
    QueryExecutionService queryExecutionService(
            QueryExecutionPreparationService preparation,
            ApprovedQueryExecutor executor,
            QueryExecutionCompletionService completion) {
        return new QueryExecutionService(preparation, executor, completion);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
