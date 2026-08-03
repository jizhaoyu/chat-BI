ALTER TABLE data_source
    ADD COLUMN authorization_version BIGINT NOT NULL DEFAULT 0 AFTER version;

ALTER TABLE app_user
    ADD CONSTRAINT uk_app_user_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE query_approval (
    id CHAR(36) PRIMARY KEY,
    token_hash BINARY(32) NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    data_source_id CHAR(36) NOT NULL,
    metadata_snapshot_id CHAR(36) NOT NULL,
    data_source_version BIGINT NOT NULL,
    authorization_version BIGINT NOT NULL,
    rule_version VARCHAR(80) NOT NULL,
    policy_hash CHAR(64) NOT NULL,
    normalized_sql TEXT NOT NULL,
    sql_hash CHAR(64) NOT NULL,
    parameter_hash CHAR(64) NOT NULL,
    maximum_rows INT NOT NULL,
    timeout_seconds INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_query_approval_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_query_approval_user FOREIGN KEY (tenant_id, user_id) REFERENCES app_user(tenant_id, id),
    CONSTRAINT fk_query_approval_source FOREIGN KEY (tenant_id, data_source_id)
        REFERENCES data_source(tenant_id, id),
    CONSTRAINT fk_query_approval_snapshot FOREIGN KEY (tenant_id, metadata_snapshot_id)
        REFERENCES catalog_snapshot(tenant_id, id),
    CONSTRAINT uk_query_approval_token UNIQUE (token_hash),
    INDEX idx_query_approval_scope (tenant_id, user_id, data_source_id, status, expires_at)
);

CREATE TABLE query_approval_reference (
    approval_id CHAR(36) NOT NULL,
    ordinal_no INT NOT NULL,
    table_id CHAR(36) NOT NULL,
    column_id CHAR(36) NOT NULL,
    schema_name VARCHAR(64) NOT NULL,
    table_name VARCHAR(64) NOT NULL,
    column_name VARCHAR(64) NOT NULL,
    PRIMARY KEY (approval_id, ordinal_no),
    CONSTRAINT fk_query_approval_reference FOREIGN KEY (approval_id) REFERENCES query_approval(id)
);
