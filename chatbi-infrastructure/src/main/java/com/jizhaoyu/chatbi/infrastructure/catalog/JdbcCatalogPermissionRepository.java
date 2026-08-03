package com.jizhaoyu.chatbi.infrastructure.catalog;

import com.jizhaoyu.chatbi.application.catalog.CatalogPermissionRepository;
import com.jizhaoyu.chatbi.domain.catalog.CatalogObjectType;
import com.jizhaoyu.chatbi.domain.catalog.CatalogPermission;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcCatalogPermissionRepository implements CatalogPermissionRepository {
    private static final String SUBJECT_TYPE = "USER";

    private final JdbcTemplate jdbc;

    public JdbcCatalogPermissionRepository(@Qualifier("platformJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void replace(
            UUID tenantId, UUID subjectId, UUID dataSourceId, List<CatalogPermission> permissions) {
        validateSubject(tenantId, subjectId);
        validateDataSource(tenantId, dataSourceId);
        for (CatalogPermission permission : permissions) {
            validateScope(tenantId, subjectId, dataSourceId, permission);
            validateActiveObject(tenantId, dataSourceId, permission.objectType(), permission.objectId());
        }
        jdbc.update("DELETE FROM data_permission "
                        + "WHERE tenant_id = ? AND subject_type = ? AND subject_id = ? AND data_source_id = ?",
                tenantId.toString(), SUBJECT_TYPE, subjectId.toString(), dataSourceId.toString());
        for (CatalogPermission permission : permissions) {
            jdbc.update("INSERT INTO data_permission "
                            + "(id, tenant_id, subject_type, subject_id, data_source_id, object_type, object_id, mask_policy) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    permission.id().toString(), tenantId.toString(), SUBJECT_TYPE, subjectId.toString(),
                    dataSourceId.toString(), permission.objectType().name(), permission.objectId().toString(),
                    permission.maskPolicy());
        }
        jdbc.update("UPDATE data_source SET authorization_version = authorization_version + 1 "
                        + "WHERE tenant_id = ? AND id = ?",
                tenantId.toString(), dataSourceId.toString());
    }

    @Override
    public List<CatalogPermission> findGranted(UUID tenantId, UUID subjectId, UUID dataSourceId) {
        return jdbc.query("SELECT id, subject_type, object_type, object_id, mask_policy FROM data_permission "
                        + "WHERE tenant_id = ? AND subject_id = ? AND data_source_id = ?",
                (row, number) -> new CatalogPermission(UUID.fromString(row.getString("id")), tenantId,
                        row.getString("subject_type"), subjectId, dataSourceId,
                        CatalogObjectType.valueOf(row.getString("object_type")),
                        UUID.fromString(row.getString("object_id")), row.getString("mask_policy")),
                tenantId.toString(), subjectId.toString(), dataSourceId.toString());
    }

    private void validateSubject(UUID tenantId, UUID subjectId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE tenant_id = ? AND id = ? AND enabled = TRUE",
                Integer.class, tenantId.toString(), subjectId.toString());
        if (count == null || count != 1) {
            throw new IllegalArgumentException("CATALOG_PERMISSION_SUBJECT_NOT_FOUND");
        }
    }

    private void validateDataSource(UUID tenantId, UUID dataSourceId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_source WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId.toString(), dataSourceId.toString());
        if (count == null || count != 1) {
            throw new IllegalArgumentException("DATASOURCE_NOT_FOUND");
        }
    }

    private static void validateScope(
            UUID tenantId, UUID subjectId, UUID dataSourceId, CatalogPermission permission) {
        if (!tenantId.equals(permission.tenantId())
                || !subjectId.equals(permission.subjectId())
                || !dataSourceId.equals(permission.dataSourceId())
                || !SUBJECT_TYPE.equals(permission.subjectType())) {
            throw new SecurityException("CATALOG_PERMISSION_SCOPE_MISMATCH");
        }
    }

    private void validateActiveObject(
            UUID tenantId, UUID dataSourceId, CatalogObjectType type, UUID objectId) {
        String sql = type == CatalogObjectType.TABLE
                ? "SELECT COUNT(*) FROM catalog_table o "
                        + "JOIN catalog_snapshot s ON s.tenant_id = o.tenant_id AND s.id = o.snapshot_id "
                        + "WHERE o.tenant_id = ? AND o.id = ? AND s.data_source_id = ? AND s.status = 'ACTIVE'"
                : "SELECT COUNT(*) FROM catalog_column o "
                        + "JOIN catalog_table t ON t.tenant_id = o.tenant_id AND t.id = o.table_id "
                        + "JOIN catalog_snapshot s ON s.tenant_id = t.tenant_id AND s.id = t.snapshot_id "
                        + "WHERE o.tenant_id = ? AND o.id = ? AND s.data_source_id = ? AND s.status = 'ACTIVE'";
        Integer count = jdbc.queryForObject(
                sql, Integer.class, tenantId.toString(), objectId.toString(), dataSourceId.toString());
        if (count == null || count != 1) {
            throw new SecurityException("CATALOG_PERMISSION_OBJECT_FORBIDDEN");
        }
    }
}
