package com.jizhaoyu.chatbi;

import com.jizhaoyu.chatbi.infrastructure.datasource.SampleAnalysisReadProbe;
import com.jizhaoyu.chatbi.application.datasource.DataSourceCommand;
import com.jizhaoyu.chatbi.application.datasource.DataSourceRepository;
import com.jizhaoyu.chatbi.application.datasource.CredentialVaultPort;
import com.jizhaoyu.chatbi.application.catalog.CatalogPermissionRepository;
import com.jizhaoyu.chatbi.application.catalog.CatalogSnapshotRepository;
import com.jizhaoyu.chatbi.domain.catalog.CatalogColumn;
import com.jizhaoyu.chatbi.domain.catalog.CatalogObjectType;
import com.jizhaoyu.chatbi.domain.catalog.CatalogPermission;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotStatus;
import com.jizhaoyu.chatbi.domain.catalog.CatalogTable;
import com.jizhaoyu.chatbi.domain.catalog.SemanticMetadata;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = false)
@SpringBootTest
class DatabaseIsolationIT {
    private static final DockerImageName MYSQL = DockerImageName.parse("mysql:8.4.7");

    @Container
    static final MySQLContainer<?> PLATFORM = new MySQLContainer<>(MYSQL)
            .withDatabaseName("chatbi_platform")
            .withUsername("chatbi_app")
            .withPassword("platform-test-password");

    @Container
    static final MySQLContainer<?> SALES = new MySQLContainer<>(MYSQL)
            .withDatabaseName("sample_sales")
            .withUsername("sample_admin")
            .withPassword("sample-admin-test-password")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("sample-sales-test.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    private static final String READER = "chatbi_reader";
    private static final String READER_PASSWORD = "reader-test-password";

    @Autowired @Qualifier("platformDataSource") DataSource platformDataSource;
    @Autowired @Qualifier("analysisDataSource") DataSource analysisDataSource;
    @Autowired @Qualifier("platformTransactionManager") PlatformTransactionManager platformTransactionManager;
    @Autowired @Qualifier("analysisTransactionManager") PlatformTransactionManager analysisTransactionManager;
    @Autowired SampleAnalysisReadProbe analysisReadProbe;
    @Autowired DataSourceRepository dataSourceRepository;
    @Autowired CredentialVaultPort credentialVault;
    @Autowired CatalogSnapshotRepository catalogSnapshots;
    @Autowired CatalogPermissionRepository catalogPermissions;
    @Autowired @Qualifier("platformJdbcTemplate") JdbcTemplate platformJdbc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("app.platform.datasource.url", PLATFORM::getJdbcUrl);
        registry.add("app.platform.datasource.username", PLATFORM::getUsername);
        registry.add("app.platform.datasource.password", PLATFORM::getPassword);
        registry.add("app.platform.datasource.maximum-pool-size", () -> 1);
        registry.add("app.sample-analysis.enabled", () -> true);
        registry.add("app.sample-analysis.datasource.url", SALES::getJdbcUrl);
        registry.add("app.sample-analysis.datasource.username", () -> READER);
        registry.add("app.sample-analysis.datasource.password", () -> READER_PASSWORD);
        registry.add("app.sample-analysis.datasource.maximum-pool-size", () -> 2);
        registry.add("server.servlet.session.cookie.secure", () -> false);
        registry.add("app.datasource-credentials.active-key-id", () -> "integration-v1");
        registry.add("app.datasource-credentials.active-key-base64",
                () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.datasource-credentials.old-keys", () -> "");
    }

    @Test
    void platformFlywayDoesNotMigrateSalesDatabase() throws SQLException {
        assertThat(tableExists(PLATFORM, "flyway_schema_history")).isTrue();
        assertThat(tableExists(SALES, "flyway_schema_history")).isFalse();
        assertThat(tableExists(SALES, "fact_order")).isTrue();
        assertThat(tableExists(PLATFORM, "fact_order")).isFalse();
    }

    @Test
    void readerCanQuerySalesData() throws SQLException {
        try (Connection connection = readerConnection(); java.sql.Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM fact_order")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void springContextWiresIndependentPoolsAndTransactionManagers() {
        assertThat(platformDataSource).isNotSameAs(analysisDataSource);
        assertThat(platformTransactionManager).isNotSameAs(analysisTransactionManager);
        assertThat(((HikariDataSource) platformDataSource).getJdbcUrl())
                .isNotEqualTo(((HikariDataSource) analysisDataSource).getJdbcUrl());
        assertThat(((HikariDataSource) analysisDataSource).isReadOnly()).isTrue();
    }

    @Test
    void rejectsAnalysisReadInsidePlatformTransaction() {
        TransactionTemplate platformTransaction = new TransactionTemplate(platformTransactionManager);
        assertThatThrownBy(() -> platformTransaction.execute(status -> analysisReadProbe.countOrders()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PLATFORM_TRANSACTION_MUST_NOT_WRAP_ANALYSIS_QUERY");
        assertThat(analysisReadProbe.countOrders()).isEqualTo(2);
    }

    @Test
    void platformRepositoryDoesNotExposeAnotherTenantsDatasource() {
        java.util.UUID tenantA = java.util.UUID.randomUUID();
        java.util.UUID tenantB = java.util.UUID.randomUUID();
        platformJdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?)", tenantA.toString(), "tenant-" + tenantA);
        platformJdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?)", tenantB.toString(), "tenant-" + tenantB);
        var command = new DataSourceCommand("sales", "sample-sales.example.com", 3306, "sample_sales", "reader",
                "reader-secret-password", DataSourceDialect.MYSQL, 1000, 30);
        var created = dataSourceRepository.save(tenantA, java.util.UUID.randomUUID(), command, "credential/test");

        assertThat(dataSourceRepository.findByTenantAndId(tenantA, created.id())).isPresent();
        assertThat(dataSourceRepository.findByTenantAndId(tenantB, created.id())).isEmpty();
        assertThat(dataSourceRepository.findAllByTenant(tenantB)).isEmpty();
        assertThatThrownBy(() -> dataSourceRepository.update(tenantB, created.id(), command, "credential/test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dataSourceRepository.transitionStatus(tenantB, created.id(),
                com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus.DRAFT,
                com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus.TESTING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DATASOURCE_STATE_CONFLICT");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO dim_region VALUES (99, 'X', 'X', 'X')",
            "UPDATE dim_region SET city = 'X' WHERE id = 1",
            "DELETE FROM dim_region WHERE id = 1",
            "CREATE TABLE forbidden_table(id INT)",
            "ALTER TABLE dim_region ADD COLUMN forbidden INT",
            "DROP TABLE fact_order_item"
    })
    void databaseRejectsWritesForReader(String sql) {
        assertThatThrownBy(() -> {
            try (Connection connection = readerConnection(); java.sql.Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).startsWith("42"));
    }

    @Test
    void platformCredentialsCannotConnectToSalesDatabase() {
        assertThatThrownBy(() -> DriverManager.getConnection(SALES.getJdbcUrl(), PLATFORM.getUsername(), PLATFORM.getPassword()))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void analysisCredentialsCannotConnectToPlatformDatabase() {
        assertThatThrownBy(() -> DriverManager.getConnection(PLATFORM.getJdbcUrl(), READER, READER_PASSWORD))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void credentialVaultStoresOnlyCiphertextAndEnforcesTenantScope() {
        UUID tenant = insertTenant();
        UUID source = UUID.randomUUID();
        String password = "reader-credential-integration-secret";

        String reference = credentialVault.store(tenant, source, password);

        byte[] ciphertext = platformJdbc.queryForObject(
                "SELECT ciphertext FROM data_source_credential WHERE tenant_id = ? AND data_source_id = ? AND active = TRUE",
                byte[].class, tenant.toString(), source.toString());
        String plaintextHex = HexFormat.of().formatHex(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(ciphertext)).doesNotContain(plaintextHex);
        assertThat(credentialVault.resolve(tenant, source, reference)).isEqualTo(password);
        assertThatThrownBy(() -> credentialVault.resolve(UUID.randomUUID(), source, reference))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CREDENTIAL_NOT_FOUND");
    }

    @Test
    void failedCatalogSyncKeepsPreviousActiveSnapshot() {
        UUID tenant = insertTenant();
        var source = insertReadyDataSource(tenant);
        CatalogSnapshot firstSync = syncingSnapshot(tenant, source.id(), UUID.randomUUID(), 1, "fact_order");
        catalogSnapshots.beginSync(tenant, source.id(), firstSync.id(), Instant.now());
        CatalogSnapshot firstActive = catalogSnapshots.completeAndActivate(firstSync);

        UUID failedId = UUID.randomUUID();
        catalogSnapshots.beginSync(tenant, source.id(), failedId, Instant.now());
        catalogSnapshots.markFailed(tenant, source.id(), failedId);

        assertThat(catalogSnapshots.findActive(tenant, source.id()))
                .get().extracting(CatalogSnapshot::id).isEqualTo(firstActive.id());
        assertThat(catalogSnapshots.findById(tenant, source.id(), failedId))
                .get().extracting(CatalogSnapshot::status).isEqualTo(CatalogSnapshotStatus.FAILED);
    }

    @Test
    void permissionRepositoryRejectsObjectsOutsideActiveTenantSnapshot() {
        UUID tenant = insertTenant();
        UUID subject = UUID.randomUUID();
        platformJdbc.update("INSERT INTO app_user(id, tenant_id, username, password_hash, enabled) "
                        + "VALUES (?, ?, ?, '{noop}test', TRUE)",
                subject.toString(), tenant.toString(), "analyst-" + subject);
        var source = insertReadyDataSource(tenant);
        CatalogSnapshot syncing = syncingSnapshot(tenant, source.id(), UUID.randomUUID(), 1, "fact_order");
        catalogSnapshots.beginSync(tenant, source.id(), syncing.id(), Instant.now());
        CatalogSnapshot active = catalogSnapshots.completeAndActivate(syncing);
        UUID tableId = active.tables().getFirst().id();
        CatalogPermission valid = new CatalogPermission(UUID.randomUUID(), tenant, "USER", subject, source.id(),
                CatalogObjectType.TABLE, tableId, "");

        catalogPermissions.replace(tenant, subject, source.id(), List.of(valid));

        assertThat(catalogPermissions.findGranted(tenant, subject, source.id()))
                .extracting(CatalogPermission::objectId).containsExactly(tableId);
        CatalogPermission forged = new CatalogPermission(UUID.randomUUID(), tenant, "USER", subject, source.id(),
                CatalogObjectType.TABLE, UUID.randomUUID(), "");
        assertThatThrownBy(() -> catalogPermissions.replace(
                tenant, subject, source.id(), List.of(forged)))
                .isInstanceOf(SecurityException.class)
                .hasMessage("CATALOG_PERMISSION_OBJECT_FORBIDDEN");
    }

    private UUID insertTenant() {
        UUID tenant = UUID.randomUUID();
        platformJdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?)",
                tenant.toString(), "tenant-" + tenant);
        return tenant;
    }

    private com.jizhaoyu.chatbi.application.datasource.DataSourceView insertReadyDataSource(UUID tenant) {
        var command = new DataSourceCommand("sales-" + UUID.randomUUID(), "analytics.example.com", 3306,
                "sample_sales", "reader", "reader-secret-password", DataSourceDialect.MYSQL, 1000, 30);
        var source = dataSourceRepository.save(tenant, UUID.randomUUID(), command, "credential/test");
        dataSourceRepository.transitionStatus(tenant, source.id(),
                com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus.DRAFT,
                com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus.TESTING);
        return dataSourceRepository.transitionStatus(tenant, source.id(),
                com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus.TESTING,
                com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus.READY);
    }

    private CatalogSnapshot syncingSnapshot(
            UUID tenant, UUID source, UUID snapshot, long version, String tableName) {
        UUID tableId = UUID.randomUUID();
        CatalogColumn column = new CatalogColumn(UUID.randomUUID(), tenant, tableId, "id", "BIGINT",
                false, 1, "", SemanticMetadata.physicalOnly(), true);
        CatalogTable table = new CatalogTable(tableId, tenant, snapshot, "sample_sales", tableName,
                "", SemanticMetadata.physicalOnly(), true, List.of(column));
        return new CatalogSnapshot(snapshot, tenant, source, version, CatalogSnapshotStatus.SYNCING,
                List.of(table), List.of(), Instant.now(), null);
    }

    private static boolean tableExists(MySQLContainer<?> container, String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
             var result = connection.getMetaData().getTables(container.getDatabaseName(), null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private static Connection readerConnection() throws SQLException {
        return DriverManager.getConnection(SALES.getJdbcUrl(), READER, READER_PASSWORD);
    }

}
