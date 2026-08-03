package com.jizhaoyu.chatbi.infrastructure.datasource;

import com.jizhaoyu.chatbi.application.datasource.DataSourceCommand;
import com.jizhaoyu.chatbi.application.datasource.DataSourceRepository;
import com.jizhaoyu.chatbi.application.datasource.DataSourceView;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDataSourceRepository implements DataSourceRepository {
    private static final String BASE_SELECT = """
            SELECT id, name, host, port, database_name, username, credential_ref,
                   dialect, status, max_rows, timeout_seconds
            FROM data_source
            """;
    private static final RowMapper<DataSourceView> MAPPER = JdbcDataSourceRepository::map;
    private final JdbcTemplate jdbc;

    public JdbcDataSourceRepository(@Qualifier("platformJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DataSourceView save(UUID tenantId, UUID id, DataSourceCommand command, String credentialRef) {
        jdbc.update("""
                INSERT INTO data_source(id, tenant_id, name, dialect, host, port, database_name, username,
                                        credential_ref, status, max_rows, timeout_seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                """, id.toString(), tenantId.toString(), command.name(), command.dialect().name(), command.host(),
                command.port(), command.database(), command.username(), credentialRef, command.maxRows(), command.timeoutSeconds());
        return findByTenantAndId(tenantId, id).orElseThrow();
    }

    @Override
    public List<DataSourceView> findAllByTenant(UUID tenantId) {
        return jdbc.query(BASE_SELECT + " WHERE tenant_id = ? ORDER BY created_at DESC", MAPPER, tenantId.toString());
    }

    @Override
    public Optional<DataSourceView> findByTenantAndId(UUID tenantId, UUID id) {
        return jdbc.query(BASE_SELECT + " WHERE tenant_id = ? AND id = ?", MAPPER, tenantId.toString(), id.toString()).stream().findFirst();
    }

    @Override
    public DataSourceView update(UUID tenantId, UUID id, DataSourceCommand command, String credentialRef) {
        int changed = jdbc.update("""
                UPDATE data_source
                SET name = ?, dialect = ?, host = ?, port = ?, database_name = ?, username = ?,
                    credential_ref = ?, max_rows = ?, timeout_seconds = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE tenant_id = ? AND id = ? AND status = 'DRAFT'
                """, command.name(), command.dialect().name(), command.host(), command.port(), command.database(),
                command.username(), credentialRef, command.maxRows(), command.timeoutSeconds(),
                tenantId.toString(), id.toString());
        if (changed != 1) throw new IllegalArgumentException("DATASOURCE_NOT_FOUND");
        return findByTenantAndId(tenantId, id).orElseThrow();
    }

    @Override
    public DataSourceView transitionStatus(
            UUID tenantId, UUID id, DataSourceStatus expectedStatus, DataSourceStatus targetStatus) {
        int changed = jdbc.update("UPDATE data_source SET status = ?, updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE tenant_id = ? AND id = ? AND status = ?",
                targetStatus.name(), tenantId.toString(), id.toString(), expectedStatus.name());
        if (changed != 1) {
            throw new IllegalStateException("DATASOURCE_STATE_CONFLICT");
        }
        return findByTenantAndId(tenantId, id).orElseThrow();
    }

    @Override
    public void disable(UUID tenantId, UUID id) {
        transitionStatus(tenantId, id, DataSourceStatus.READY, DataSourceStatus.DISABLED);
    }

    private static DataSourceView map(ResultSet rs, int row) throws SQLException {
        return new DataSourceView(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getString("host"),
                rs.getInt("port"), rs.getString("database_name"), rs.getString("username"), rs.getString("credential_ref"),
                DataSourceDialect.valueOf(rs.getString("dialect")), DataSourceStatus.valueOf(rs.getString("status")),
                rs.getInt("max_rows"), rs.getInt("timeout_seconds"));
    }
}
