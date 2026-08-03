package com.jizhaoyu.chatbi.infrastructure.catalog;

import com.jizhaoyu.chatbi.application.catalog.CatalogMetadataReader;
import com.jizhaoyu.chatbi.application.catalog.DiscoveredCatalog;
import com.jizhaoyu.chatbi.application.catalog.DiscoveredColumn;
import com.jizhaoyu.chatbi.application.catalog.DiscoveredRelation;
import com.jizhaoyu.chatbi.application.catalog.DiscoveredTable;
import com.jizhaoyu.chatbi.application.datasource.CredentialVaultPort;
import com.jizhaoyu.chatbi.application.datasource.DataSourceRepository;
import com.jizhaoyu.chatbi.application.datasource.DataSourceView;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourceConnectionSpec;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourcePool;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public final class MySqlCatalogMetadataReader implements CatalogMetadataReader {
    private static final int METADATA_POOL_SIZE = 3;
    private static final int MAX_CONNECTION_TIMEOUT_SECONDS = 30;

    private final DataSourceRepository sources;
    private final CredentialVaultPort credentials;
    private final ExternalDataSourcePool pools;

    public MySqlCatalogMetadataReader(
            DataSourceRepository sources, CredentialVaultPort credentials, ExternalDataSourcePool pools) {
        this.sources = sources;
        this.credentials = credentials;
        this.pools = pools;
    }

    @Override
    public DiscoveredCatalog read(UUID tenantId, UUID dataSourceId) {
        DataSourceView source = sources.findByTenantAndId(tenantId, dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException("DATASOURCE_NOT_FOUND"));
        if (source.status() != DataSourceStatus.READY) {
            throw new IllegalStateException("DATASOURCE_NOT_READY");
        }
        String password = credentials.resolve(tenantId, dataSourceId, source.credentialRef());
        ExternalDataSourceConnectionSpec spec = new ExternalDataSourceConnectionSpec(
                tenantId, dataSourceId, source.host(), source.port(), source.database(), source.username(),
                password, source.dialect(), METADATA_POOL_SIZE,
                Math.min(source.timeoutSeconds(), MAX_CONNECTION_TIMEOUT_SECONDS));
        try (Connection connection = pools.getOrCreate(spec).getConnection()) {
            return discover(connection.getMetaData(), source.database());
        } catch (SQLException exception) {
            throw new IllegalStateException("CATALOG_METADATA_READ_FAILED");
        }
    }

    private static DiscoveredCatalog discover(DatabaseMetaData metadata, String database) throws SQLException {
        List<DiscoveredTable> tables = new ArrayList<>();
        List<DiscoveredRelation> relations = new ArrayList<>();
        try (ResultSet tableRows = metadata.getTables(database, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (tableRows.next()) {
                String schema = schemaName(tableRows, database);
                String table = tableRows.getString("TABLE_NAME");
                tables.add(new DiscoveredTable(schema, table, text(tableRows, "REMARKS"),
                        columns(metadata, database, table)));
                relations.addAll(relations(metadata, database, schema, table));
            }
        }
        tables.sort(Comparator.comparing(DiscoveredTable::qualifiedName));
        relations.sort(Comparator.comparing(DiscoveredRelation::sourceTable)
                .thenComparing(DiscoveredRelation::targetTable)
                .thenComparing(relation -> String.join(",", relation.sourceColumns())));
        return new DiscoveredCatalog(tables, relations);
    }

    private static List<DiscoveredColumn> columns(
            DatabaseMetaData metadata, String database, String table) throws SQLException {
        List<DiscoveredColumn> columns = new ArrayList<>();
        try (ResultSet rows = metadata.getColumns(database, null, table, "%")) {
            while (rows.next()) {
                columns.add(new DiscoveredColumn(rows.getString("COLUMN_NAME"), rows.getString("TYPE_NAME"),
                        rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        rows.getInt("ORDINAL_POSITION"), text(rows, "REMARKS")));
            }
        }
        columns.sort(Comparator.comparingInt(DiscoveredColumn::ordinal));
        return columns;
    }

    private static List<DiscoveredRelation> relations(
            DatabaseMetaData metadata, String database, String sourceSchema, String sourceTable) throws SQLException {
        Map<RelationKey, MutableRelation> grouped = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getImportedKeys(database, null, sourceTable)) {
            while (rows.next()) {
                String targetSchema = firstNonBlank(rows.getString("PKTABLE_SCHEM"),
                        rows.getString("PKTABLE_CAT"), database);
                String targetTable = rows.getString("PKTABLE_NAME");
                String foreignKeyName = firstNonBlank(rows.getString("FK_NAME"),
                        sourceTable + "->" + targetTable);
                RelationKey key = new RelationKey(foreignKeyName, sourceSchema + "." + sourceTable,
                        targetSchema + "." + targetTable);
                grouped.computeIfAbsent(key, ignored -> new MutableRelation())
                        .add(rows.getShort("KEY_SEQ"), rows.getString("FKCOLUMN_NAME"),
                                rows.getString("PKCOLUMN_NAME"));
            }
        }
        return grouped.entrySet().stream()
                .map(entry -> entry.getValue().toDiscovered(entry.getKey()))
                .toList();
    }

    private static String schemaName(ResultSet rows, String fallback) throws SQLException {
        return firstNonBlank(rows.getString("TABLE_SCHEM"), rows.getString("TABLE_CAT"), fallback);
    }

    private static String text(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("CATALOG_SCHEMA_NAME_REQUIRED");
    }

    private record RelationKey(String name, String sourceTable, String targetTable) {
    }

    private static final class MutableRelation {
        private final List<RelationColumn> columns = new ArrayList<>();

        void add(int sequence, String source, String target) {
            columns.add(new RelationColumn(sequence, source, target));
        }

        DiscoveredRelation toDiscovered(RelationKey key) {
            columns.sort(Comparator.comparingInt(RelationColumn::sequence));
            return new DiscoveredRelation(key.sourceTable(), columns.stream().map(RelationColumn::source).toList(),
                    key.targetTable(), columns.stream().map(RelationColumn::target).toList(), "FOREIGN_KEY");
        }
    }

    private record RelationColumn(int sequence, String source, String target) {
    }
}
