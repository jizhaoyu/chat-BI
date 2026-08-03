ALTER TABLE query_approval
    ADD CONSTRAINT uk_query_approval_scope UNIQUE (tenant_id, id);

CREATE TABLE query_execution (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    approval_id CHAR(36) NOT NULL,
    executor_user_id CHAR(36) NOT NULL,
    data_source_id CHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    duration_ms BIGINT NULL,
    row_count INT NOT NULL DEFAULT 0,
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(80) NULL,
    result_digest CHAR(64) NULL,
    CONSTRAINT fk_query_execution_approval FOREIGN KEY (tenant_id, approval_id)
        REFERENCES query_approval(tenant_id, id),
    CONSTRAINT fk_query_execution_user FOREIGN KEY (tenant_id, executor_user_id)
        REFERENCES app_user(tenant_id, id),
    CONSTRAINT fk_query_execution_source FOREIGN KEY (tenant_id, data_source_id)
        REFERENCES data_source(tenant_id, id),
    CONSTRAINT uk_query_execution_approval UNIQUE (approval_id),
    INDEX idx_query_execution_scope (tenant_id, executor_user_id, started_at)
);
