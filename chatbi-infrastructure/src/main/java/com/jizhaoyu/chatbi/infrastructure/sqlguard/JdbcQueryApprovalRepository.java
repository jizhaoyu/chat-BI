package com.jizhaoyu.chatbi.infrastructure.sqlguard;

import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalEnvelope;
import com.jizhaoyu.chatbi.application.sqlguard.QueryApprovalRepository;
import com.jizhaoyu.chatbi.application.sqlguard.SqlObjectReference;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcQueryApprovalRepository implements QueryApprovalRepository {
    private final JdbcTemplate jdbc;

    public JdbcQueryApprovalRepository(@Qualifier("platformJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void save(byte[] tokenHash, QueryApprovalEnvelope envelope) {
        jdbc.update("INSERT INTO query_approval "
                        + "(id, token_hash, tenant_id, user_id, data_source_id, metadata_snapshot_id, "
                        + "data_source_version, authorization_version, rule_version, policy_hash, normalized_sql, "
                        + "sql_hash, parameter_hash, maximum_rows, timeout_seconds, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                envelope.id().toString(), tokenHash, envelope.tenantId().toString(), envelope.userId().toString(),
                envelope.dataSourceId().toString(), envelope.metadataSnapshotId().toString(),
                envelope.dataSourceVersion(), envelope.authorizationVersion(), envelope.ruleVersion(),
                envelope.policyHash(), envelope.normalizedSql(), envelope.sqlHash(), envelope.parameterHash(),
                envelope.maximumRows(), envelope.timeoutSeconds(), Timestamp.from(envelope.expiresAt()));
        for (int index = 0; index < envelope.references().size(); index++) {
            SqlObjectReference reference = envelope.references().get(index);
            jdbc.update("INSERT INTO query_approval_reference "
                            + "(approval_id, ordinal_no, table_id, column_id, schema_name, table_name, column_name) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    envelope.id().toString(), index + 1, reference.tableId().toString(),
                    reference.columnId().toString(), reference.schemaName(), reference.tableName(),
                    reference.columnName());
        }
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public QueryApprovalEnvelope consumeAndStart(
            byte[] tokenHash, UserPrincipal actor, Instant now, String currentRuleVersion, UUID executionId) {
        List<ApprovalRow> rows = jdbc.query("SELECT a.id, a.tenant_id, a.user_id, a.data_source_id, "
                        + "a.metadata_snapshot_id, a.data_source_version, a.authorization_version, "
                        + "a.rule_version, a.policy_hash, a.normalized_sql, a.sql_hash, a.parameter_hash, "
                        + "a.maximum_rows, a.timeout_seconds, a.status, a.expires_at, "
                        + "d.version AS current_source_version, d.authorization_version AS current_auth_version, "
                        + "d.status AS source_status, d.max_rows AS current_max_rows, "
                        + "d.timeout_seconds AS current_timeout_seconds, s.status AS snapshot_status "
                        + "FROM query_approval a "
                        + "JOIN data_source d ON d.tenant_id = a.tenant_id AND d.id = a.data_source_id "
                        + "JOIN catalog_snapshot s ON s.tenant_id = a.tenant_id AND s.id = a.metadata_snapshot_id "
                        + "AND s.data_source_id = a.data_source_id "
                        + "WHERE a.token_hash = ? "
                        + "AND EXISTS (SELECT 1 FROM app_user u WHERE u.tenant_id = a.tenant_id "
                        + "AND u.id = a.user_id AND u.enabled = TRUE) "
                        + "AND EXISTS (SELECT 1 FROM app_user_role r WHERE r.user_id = a.user_id "
                        + "AND r.role_name IN ('ANALYST', 'DATA_ADMIN')) FOR UPDATE",
                (row, number) -> new ApprovalRow(
                        UUID.fromString(row.getString("id")), UUID.fromString(row.getString("tenant_id")),
                        UUID.fromString(row.getString("user_id")), UUID.fromString(row.getString("data_source_id")),
                        UUID.fromString(row.getString("metadata_snapshot_id")),
                        row.getLong("data_source_version"), row.getLong("authorization_version"),
                        row.getString("rule_version"), row.getString("policy_hash"),
                        row.getString("normalized_sql"), row.getString("sql_hash"),
                        row.getString("parameter_hash"), row.getInt("maximum_rows"),
                        row.getInt("timeout_seconds"), row.getString("status"),
                        row.getTimestamp("expires_at").toInstant(), row.getLong("current_source_version"),
                        row.getLong("current_auth_version"), row.getString("source_status"),
                        row.getInt("current_max_rows"), row.getInt("current_timeout_seconds"),
                        row.getString("snapshot_status")),
                tokenHash);
        if (rows.size() != 1) {
            throw new SecurityException("APPROVAL_INVALID");
        }
        ApprovalRow row = rows.getFirst();
        validate(row, actor, now, currentRuleVersion);
        int changed = jdbc.update("UPDATE query_approval SET status = 'CONSUMED', consumed_at = ? "
                        + "WHERE id = ? AND status = 'ISSUED'",
                Timestamp.from(now), row.id().toString());
        if (changed != 1) {
            throw new SecurityException("APPROVAL_ALREADY_USED");
        }
        jdbc.update("INSERT INTO query_execution "
                        + "(id, tenant_id, approval_id, executor_user_id, data_source_id, status, started_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'RUNNING', ?)",
                executionId.toString(), actor.tenantId().toString(), row.id().toString(),
                actor.userId().toString(), row.dataSourceId().toString(), Timestamp.from(now));
        List<SqlObjectReference> references = jdbc.query(
                "SELECT table_id, column_id, schema_name, table_name, column_name "
                        + "FROM query_approval_reference WHERE approval_id = ? ORDER BY ordinal_no",
                (reference, number) -> new SqlObjectReference(
                        UUID.fromString(reference.getString("table_id")),
                        UUID.fromString(reference.getString("column_id")),
                        reference.getString("schema_name"), reference.getString("table_name"),
                        reference.getString("column_name")),
                row.id().toString());
        return row.toEnvelope(references);
    }

    private static void validate(
            ApprovalRow row, UserPrincipal actor, Instant now, String currentRuleVersion) {
        if (!row.tenantId().equals(actor.tenantId()) || !row.userId().equals(actor.userId())) {
            throw new SecurityException("APPROVAL_INVALID");
        }
        if (!"ISSUED".equals(row.status())) {
            throw new SecurityException("APPROVAL_ALREADY_USED");
        }
        if (!now.isBefore(row.expiresAt())) {
            throw new SecurityException("APPROVAL_EXPIRED");
        }
        if (!currentRuleVersion.equals(row.ruleVersion())
                || row.dataSourceVersion() != row.currentDataSourceVersion()
                || row.authorizationVersion() != row.currentAuthorizationVersion()
                || !row.sqlHash().equals(sha256(row.normalizedSql()))
                || !row.parameterHash().equals(sha256("[]"))
                || !row.policyHash().equals(sha256(currentRuleVersion + ":"
                        + row.currentMaximumRows() + ":" + row.currentTimeoutSeconds()))
                || !"READY".equals(row.sourceStatus()) || !"ACTIVE".equals(row.snapshotStatus())) {
            throw new SecurityException("APPROVAL_INVALID");
        }
    }

    private record ApprovalRow(
            UUID id, UUID tenantId, UUID userId, UUID dataSourceId, UUID metadataSnapshotId,
            long dataSourceVersion, long authorizationVersion, String ruleVersion, String policyHash,
            String normalizedSql, String sqlHash, String parameterHash, int maximumRows, int timeoutSeconds,
            String status, Instant expiresAt, long currentDataSourceVersion, long currentAuthorizationVersion,
            String sourceStatus, int currentMaximumRows, int currentTimeoutSeconds, String snapshotStatus) {
        QueryApprovalEnvelope toEnvelope(List<SqlObjectReference> references) {
            return new QueryApprovalEnvelope(id, tenantId, userId, dataSourceId, metadataSnapshotId,
                    dataSourceVersion, authorizationVersion, ruleVersion, policyHash, normalizedSql, sqlHash,
                    parameterHash, maximumRows, timeoutSeconds, references, expiresAt);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", impossible);
        }
    }
}
