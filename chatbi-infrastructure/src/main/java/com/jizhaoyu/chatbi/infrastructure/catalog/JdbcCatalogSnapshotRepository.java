package com.jizhaoyu.chatbi.infrastructure.catalog;

import com.jizhaoyu.chatbi.application.catalog.CatalogSnapshotRepository;
import com.jizhaoyu.chatbi.application.catalog.CatalogSyncAttempt;
import com.jizhaoyu.chatbi.domain.catalog.CatalogColumn;
import com.jizhaoyu.chatbi.domain.catalog.CatalogRelation;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotStatus;
import com.jizhaoyu.chatbi.domain.catalog.CatalogTable;
import com.jizhaoyu.chatbi.domain.catalog.SemanticMetadata;
import com.jizhaoyu.chatbi.domain.catalog.SensitivityLevel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCatalogSnapshotRepository implements CatalogSnapshotRepository {
    private final JdbcTemplate jdbc;

    public JdbcCatalogSnapshotRepository(@Qualifier("platformJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public CatalogSyncAttempt beginSync(
            UUID tenantId, UUID dataSourceId, UUID snapshotId, Instant createdAt) {
        String status = jdbc.query(
                "SELECT status FROM data_source WHERE tenant_id = ? AND id = ? FOR UPDATE",
                result -> result.next() ? result.getString(1) : null,
                tenantId.toString(), dataSourceId.toString());
        if (status == null) {
            throw new IllegalArgumentException("DATASOURCE_NOT_FOUND");
        }
        if (!"READY".equals(status)) {
            throw new IllegalStateException("DATASOURCE_NOT_READY");
        }
        Long current = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) FROM catalog_snapshot "
                        + "WHERE tenant_id = ? AND data_source_id = ?",
                Long.class, tenantId.toString(), dataSourceId.toString());
        long version = (current == null ? 0 : current) + 1;
        jdbc.update("INSERT INTO catalog_snapshot "
                        + "(id, tenant_id, data_source_id, version_no, status, object_count, created_at) "
                        + "VALUES (?, ?, ?, ?, 'SYNCING', 0, ?)",
                snapshotId.toString(), tenantId.toString(), dataSourceId.toString(), version,
                Timestamp.from(createdAt));
        return new CatalogSyncAttempt(snapshotId, version);
    }

    @Override
    @Transactional
    public CatalogSnapshot completeAndActivate(CatalogSnapshot snapshot) {
        if (snapshot.status() != CatalogSnapshotStatus.SYNCING) {
            throw new IllegalArgumentException("CATALOG_SNAPSHOT_NOT_SYNCING");
        }
        String status = jdbc.query(
                "SELECT status FROM catalog_snapshot "
                        + "WHERE tenant_id = ? AND data_source_id = ? AND id = ? FOR UPDATE",
                result -> result.next() ? result.getString(1) : null,
                snapshot.tenantId().toString(), snapshot.dataSourceId().toString(), snapshot.id().toString());
        if (!"SYNCING".equals(status)) {
            throw new IllegalStateException("CATALOG_SNAPSHOT_STATE_CONFLICT");
        }
        insertObjects(snapshot);
        jdbc.update("UPDATE catalog_snapshot SET status = 'SUPERSEDED' "
                        + "WHERE tenant_id = ? AND data_source_id = ? AND status = 'ACTIVE'",
                snapshot.tenantId().toString(), snapshot.dataSourceId().toString());
        Instant activatedAt = Instant.now();
        int changed = jdbc.update("UPDATE catalog_snapshot SET status = 'ACTIVE', object_count = ?, activated_at = ? "
                        + "WHERE tenant_id = ? AND data_source_id = ? AND id = ? AND status = 'SYNCING'",
                snapshot.objectCount(), Timestamp.from(activatedAt), snapshot.tenantId().toString(),
                snapshot.dataSourceId().toString(), snapshot.id().toString());
        if (changed != 1) {
            throw new IllegalStateException("CATALOG_SNAPSHOT_STATE_CONFLICT");
        }
        return new CatalogSnapshot(snapshot.id(), snapshot.tenantId(), snapshot.dataSourceId(), snapshot.version(),
                CatalogSnapshotStatus.ACTIVE, snapshot.tables(), snapshot.relations(), snapshot.createdAt(), activatedAt);
    }

    @Override
    public void markFailed(UUID tenantId, UUID dataSourceId, UUID snapshotId) {
        jdbc.update("UPDATE catalog_snapshot SET status = 'FAILED' "
                        + "WHERE tenant_id = ? AND data_source_id = ? AND id = ? AND status = 'SYNCING'",
                tenantId.toString(), dataSourceId.toString(), snapshotId.toString());
    }

    @Override
    public Optional<CatalogSnapshot> findActive(UUID tenantId, UUID dataSourceId) {
        return findOne("SELECT id FROM catalog_snapshot "
                        + "WHERE tenant_id = ? AND data_source_id = ? AND status = 'ACTIVE'",
                tenantId, dataSourceId, null);
    }

    @Override
    public Optional<CatalogSnapshot> findById(UUID tenantId, UUID dataSourceId, UUID snapshotId) {
        return findOne("SELECT id FROM catalog_snapshot "
                        + "WHERE tenant_id = ? AND data_source_id = ? AND id = ?",
                tenantId, dataSourceId, snapshotId);
    }

    @Override
    @Transactional
    public void updateColumnSemantic(
            UUID tenantId, UUID dataSourceId, UUID columnId, SemanticMetadata semantic, boolean enabled) {
        int changed = jdbc.update("UPDATE catalog_column c "
                        + "JOIN catalog_table t ON t.tenant_id = c.tenant_id AND t.id = c.table_id "
                        + "JOIN catalog_snapshot s ON s.tenant_id = t.tenant_id AND s.id = t.snapshot_id "
                        + "SET c.business_name = ?, c.sensitivity = ?, c.enabled = ? "
                        + "WHERE c.tenant_id = ? AND c.id = ? AND s.data_source_id = ? AND s.status = 'ACTIVE'",
                semantic.businessName(), semantic.sensitivity().name(), enabled, tenantId.toString(),
                columnId.toString(), dataSourceId.toString());
        if (changed != 1) {
            throw new IllegalArgumentException("CATALOG_COLUMN_NOT_FOUND");
        }
        jdbc.update("DELETE FROM catalog_column_synonym WHERE tenant_id = ? AND column_id = ?",
                tenantId.toString(), columnId.toString());
        for (int index = 0; index < semantic.synonyms().size(); index++) {
            jdbc.update("INSERT INTO catalog_column_synonym "
                            + "(tenant_id, column_id, ordinal_no, synonym) VALUES (?, ?, ?, ?)",
                    tenantId.toString(), columnId.toString(), index + 1, semantic.synonyms().get(index));
        }
        jdbc.update("UPDATE data_source SET authorization_version = authorization_version + 1 "
                        + "WHERE tenant_id = ? AND id = ?",
                tenantId.toString(), dataSourceId.toString());
    }

    private void insertObjects(CatalogSnapshot snapshot) {
        for (CatalogTable table : snapshot.tables()) {
            jdbc.update("INSERT INTO catalog_table "
                            + "(id, tenant_id, snapshot_id, schema_name, table_name, table_comment, "
                            + "business_name, sensitivity, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    table.id().toString(), table.tenantId().toString(), table.snapshotId().toString(),
                    table.schemaName(), table.name(), table.comment(), table.semantic().businessName(),
                    table.semantic().sensitivity().name(), table.enabled());
            for (CatalogColumn column : table.columns()) {
                jdbc.update("INSERT INTO catalog_column "
                                + "(id, tenant_id, table_id, column_name, data_type, nullable, ordinal_no, "
                                + "column_comment, business_name, sensitivity, enabled) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        column.id().toString(), column.tenantId().toString(), column.tableId().toString(),
                        column.name(), column.dataType(), column.nullable(), column.ordinal(), column.comment(),
                        column.semantic().businessName(), column.semantic().sensitivity().name(), column.enabled());
                for (int index = 0; index < column.semantic().synonyms().size(); index++) {
                    jdbc.update("INSERT INTO catalog_column_synonym "
                                    + "(tenant_id, column_id, ordinal_no, synonym) VALUES (?, ?, ?, ?)",
                            column.tenantId().toString(), column.id().toString(), index + 1,
                            column.semantic().synonyms().get(index));
                }
            }
        }
        for (CatalogRelation relation : snapshot.relations()) {
            jdbc.update("INSERT INTO catalog_relation "
                            + "(id, tenant_id, snapshot_id, source_table_id, target_table_id, relation_type) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    relation.id().toString(), relation.tenantId().toString(), relation.snapshotId().toString(),
                    relation.sourceTableId().toString(), relation.targetTableId().toString(), relation.relationType());
            for (int index = 0; index < relation.sourceColumns().size(); index++) {
                jdbc.update("INSERT INTO catalog_relation_column "
                                + "(tenant_id, relation_id, ordinal_no, source_column_name, target_column_name) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        relation.tenantId().toString(), relation.id().toString(), index + 1,
                        relation.sourceColumns().get(index), relation.targetColumns().get(index));
            }
        }
    }

    private Optional<CatalogSnapshot> findOne(
            String sql, UUID tenantId, UUID dataSourceId, UUID snapshotId) {
        List<String> ids = snapshotId == null
                ? jdbc.query(sql, (row, number) -> row.getString(1), tenantId.toString(), dataSourceId.toString())
                : jdbc.query(sql, (row, number) -> row.getString(1), tenantId.toString(),
                        dataSourceId.toString(), snapshotId.toString());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(load(tenantId, dataSourceId, UUID.fromString(ids.getFirst())));
    }

    private CatalogSnapshot load(UUID tenantId, UUID dataSourceId, UUID snapshotId) {
        SnapshotRow snapshot = jdbc.queryForObject(
                "SELECT version_no, status, created_at, activated_at FROM catalog_snapshot "
                        + "WHERE tenant_id = ? AND data_source_id = ? AND id = ?",
                (row, number) -> new SnapshotRow(row.getLong("version_no"),
                        CatalogSnapshotStatus.valueOf(row.getString("status")),
                        row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("activated_at") == null ? null : row.getTimestamp("activated_at").toInstant()),
                tenantId.toString(), dataSourceId.toString(), snapshotId.toString());
        List<CatalogTable> tables = loadTables(tenantId, snapshotId);
        List<CatalogRelation> relations = loadRelations(tenantId, snapshotId);
        return new CatalogSnapshot(snapshotId, tenantId, dataSourceId, snapshot.version(), snapshot.status(),
                tables, relations, snapshot.createdAt(), snapshot.activatedAt());
    }

    private List<CatalogTable> loadTables(UUID tenantId, UUID snapshotId) {
        List<TableRow> rows = jdbc.query("SELECT id, schema_name, table_name, table_comment, business_name, sensitivity, enabled "
                        + "FROM catalog_table WHERE tenant_id = ? AND snapshot_id = ? "
                        + "ORDER BY schema_name, table_name",
                (row, number) -> new TableRow(
                        UUID.fromString(row.getString("id")), row.getString("schema_name"),
                        row.getString("table_name"), row.getString("table_comment"),
                        row.getString("business_name"),
                        SensitivityLevel.valueOf(row.getString("sensitivity")),
                        row.getBoolean("enabled")),
                tenantId.toString(), snapshotId.toString());
        return rows.stream()
                .map(row -> new CatalogTable(row.id(), tenantId, snapshotId, row.schemaName(), row.name(),
                        row.comment(), new SemanticMetadata(row.businessName(), List.of(), row.sensitivity()),
                        row.enabled(), loadColumns(tenantId, row.id())))
                .toList();
    }

    private List<CatalogColumn> loadColumns(UUID tenantId, UUID tableId) {
        List<ColumnRow> rows = jdbc.query("SELECT id, column_name, data_type, nullable, ordinal_no, column_comment, "
                        + "business_name, sensitivity, enabled FROM catalog_column "
                        + "WHERE tenant_id = ? AND table_id = ? ORDER BY ordinal_no",
                (row, number) -> new ColumnRow(
                        UUID.fromString(row.getString("id")), row.getString("column_name"),
                        row.getString("data_type"), row.getBoolean("nullable"), row.getInt("ordinal_no"),
                        row.getString("column_comment"), row.getString("business_name"),
                        SensitivityLevel.valueOf(row.getString("sensitivity")), row.getBoolean("enabled")),
                tenantId.toString(), tableId.toString());
        return rows.stream().map(row -> {
            List<String> synonyms = jdbc.query("SELECT synonym FROM catalog_column_synonym "
                            + "WHERE tenant_id = ? AND column_id = ? ORDER BY ordinal_no",
                    (synonymRow, ignored) -> synonymRow.getString(1),
                    tenantId.toString(), row.id().toString());
            return new CatalogColumn(row.id(), tenantId, tableId, row.name(), row.dataType(), row.nullable(),
                    row.ordinal(), row.comment(),
                    new SemanticMetadata(row.businessName(), synonyms, row.sensitivity()), row.enabled());
        }).toList();
    }

    private List<CatalogRelation> loadRelations(UUID tenantId, UUID snapshotId) {
        List<RelationRow> rows = jdbc.query(
                "SELECT id, source_table_id, target_table_id, relation_type FROM catalog_relation "
                        + "WHERE tenant_id = ? AND snapshot_id = ? ORDER BY id",
                (row, number) -> new RelationRow(
                        UUID.fromString(row.getString("id")),
                        UUID.fromString(row.getString("source_table_id")),
                        UUID.fromString(row.getString("target_table_id")), row.getString("relation_type")),
                tenantId.toString(), snapshotId.toString());
        return rows.stream().map(row -> {
            List<RelationColumn> columns = jdbc.query("SELECT source_column_name, target_column_name "
                            + "FROM catalog_relation_column WHERE tenant_id = ? AND relation_id = ? "
                            + "ORDER BY ordinal_no",
                    (columnRow, ignored) -> new RelationColumn(columnRow.getString(1), columnRow.getString(2)),
                    tenantId.toString(), row.id().toString());
            return new CatalogRelation(row.id(), tenantId, snapshotId, row.sourceTableId(),
                    columns.stream().map(RelationColumn::source).toList(), row.targetTableId(),
                    columns.stream().map(RelationColumn::target).toList(), row.type());
        }).toList();
    }

    private record SnapshotRow(
            long version, CatalogSnapshotStatus status, Instant createdAt, Instant activatedAt) {
    }

    private record TableRow(
            UUID id, String schemaName, String name, String comment, String businessName,
            SensitivityLevel sensitivity, boolean enabled) {
    }

    private record ColumnRow(
            UUID id, String name, String dataType, boolean nullable, int ordinal, String comment,
            String businessName, SensitivityLevel sensitivity, boolean enabled) {
    }

    private record RelationRow(UUID id, UUID sourceTableId, UUID targetTableId, String type) {
    }

    private record RelationColumn(String source, String target) {
    }
}
