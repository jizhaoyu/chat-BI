ALTER TABLE data_source
    ADD CONSTRAINT uk_data_source_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE data_source_credential (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    data_source_id CHAR(36) NOT NULL,
    credential_version INT NOT NULL,
    key_id VARCHAR(100) NOT NULL,
    nonce VARBINARY(12) NOT NULL,
    ciphertext VARBINARY(2048) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    active_data_source_id CHAR(36) GENERATED ALWAYS AS (
        CASE WHEN active THEN data_source_id ELSE NULL END
    ) STORED,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_data_source_credential_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uk_data_source_credential_version UNIQUE (tenant_id, data_source_id, credential_version),
    CONSTRAINT uk_data_source_credential_active UNIQUE (tenant_id, active_data_source_id),
    INDEX idx_data_source_credential_lookup (tenant_id, data_source_id, active)
);

CREATE TABLE catalog_snapshot (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    data_source_id CHAR(36) NOT NULL,
    version_no BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    object_count INT NOT NULL DEFAULT 0,
    active_data_source_id CHAR(36) GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN data_source_id ELSE NULL END
    ) STORED,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP NULL,
    CONSTRAINT fk_catalog_snapshot_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_catalog_snapshot_source FOREIGN KEY (tenant_id, data_source_id)
        REFERENCES data_source(tenant_id, id),
    CONSTRAINT uk_catalog_snapshot_scope UNIQUE (tenant_id, id),
    CONSTRAINT uk_catalog_snapshot_version UNIQUE (tenant_id, data_source_id, version_no),
    CONSTRAINT uk_catalog_snapshot_active UNIQUE (tenant_id, active_data_source_id),
    INDEX idx_catalog_snapshot_lookup (tenant_id, data_source_id, status)
);

CREATE TABLE catalog_table (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    snapshot_id CHAR(36) NOT NULL,
    schema_name VARCHAR(64) NOT NULL,
    table_name VARCHAR(64) NOT NULL,
    table_comment VARCHAR(2048) NOT NULL DEFAULT '',
    business_name VARCHAR(200) NOT NULL DEFAULT '',
    sensitivity VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_catalog_table_snapshot FOREIGN KEY (tenant_id, snapshot_id)
        REFERENCES catalog_snapshot(tenant_id, id),
    CONSTRAINT uk_catalog_table_scope UNIQUE (tenant_id, id),
    CONSTRAINT uk_catalog_table_name UNIQUE (tenant_id, snapshot_id, schema_name, table_name),
    INDEX idx_catalog_table_snapshot (tenant_id, snapshot_id)
);

CREATE TABLE catalog_column (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    table_id CHAR(36) NOT NULL,
    column_name VARCHAR(64) NOT NULL,
    data_type VARCHAR(200) NOT NULL,
    nullable BOOLEAN NOT NULL,
    ordinal_no INT NOT NULL,
    column_comment VARCHAR(2048) NOT NULL DEFAULT '',
    business_name VARCHAR(200) NOT NULL DEFAULT '',
    sensitivity VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_catalog_column_table FOREIGN KEY (tenant_id, table_id)
        REFERENCES catalog_table(tenant_id, id),
    CONSTRAINT uk_catalog_column_scope UNIQUE (tenant_id, id),
    CONSTRAINT uk_catalog_column_name UNIQUE (tenant_id, table_id, column_name),
    INDEX idx_catalog_column_table (tenant_id, table_id)
);

CREATE TABLE catalog_column_synonym (
    tenant_id CHAR(36) NOT NULL,
    column_id CHAR(36) NOT NULL,
    ordinal_no INT NOT NULL,
    synonym VARCHAR(200) NOT NULL,
    PRIMARY KEY (tenant_id, column_id, ordinal_no),
    CONSTRAINT fk_catalog_synonym_column FOREIGN KEY (tenant_id, column_id)
        REFERENCES catalog_column(tenant_id, id)
);

CREATE TABLE catalog_relation (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    snapshot_id CHAR(36) NOT NULL,
    source_table_id CHAR(36) NOT NULL,
    target_table_id CHAR(36) NOT NULL,
    relation_type VARCHAR(40) NOT NULL,
    CONSTRAINT fk_catalog_relation_snapshot FOREIGN KEY (tenant_id, snapshot_id)
        REFERENCES catalog_snapshot(tenant_id, id),
    CONSTRAINT fk_catalog_relation_source FOREIGN KEY (tenant_id, source_table_id)
        REFERENCES catalog_table(tenant_id, id),
    CONSTRAINT fk_catalog_relation_target FOREIGN KEY (tenant_id, target_table_id)
        REFERENCES catalog_table(tenant_id, id),
    CONSTRAINT uk_catalog_relation_scope UNIQUE (tenant_id, id),
    INDEX idx_catalog_relation_snapshot (tenant_id, snapshot_id)
);

CREATE TABLE catalog_relation_column (
    tenant_id CHAR(36) NOT NULL,
    relation_id CHAR(36) NOT NULL,
    ordinal_no INT NOT NULL,
    source_column_name VARCHAR(64) NOT NULL,
    target_column_name VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, relation_id, ordinal_no),
    CONSTRAINT fk_catalog_relation_column FOREIGN KEY (tenant_id, relation_id)
        REFERENCES catalog_relation(tenant_id, id)
);

CREATE TABLE data_permission (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    subject_id CHAR(36) NOT NULL,
    data_source_id CHAR(36) NOT NULL,
    object_type VARCHAR(20) NOT NULL,
    object_id CHAR(36) NOT NULL,
    mask_policy VARCHAR(100) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_data_permission_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_data_permission_source FOREIGN KEY (tenant_id, data_source_id)
        REFERENCES data_source(tenant_id, id),
    CONSTRAINT uk_data_permission_grant UNIQUE (
        tenant_id, subject_type, subject_id, data_source_id, object_type, object_id
    ),
    INDEX idx_data_permission_subject (tenant_id, subject_id, data_source_id)
);
