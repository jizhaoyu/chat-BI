# 接口与数据模型

## 1. 接口约定

- 基础路径：`/api/v1`。
- 响应封装与错误码保持稳定，错误不得泄露数据库凭据、SQL 堆栈或大批数据。
- 数据源密码只允许写入，不提供读取接口。
- 生成与执行分成两个接口，防止用户无感执行高成本候选。
- 每次执行引用一次短时 `approvalId`，审批内容变化后必须重新校验。

## 2. 主要接口草案

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/auth/login` | 登录 |
| POST | `/data-sources` | 注册数据源 |
| POST | `/data-sources/{id}:test` | 测试连接和只读能力 |
| POST | `/data-sources/{id}:sync-metadata` | 创建元数据同步任务 |
| GET | `/data-sources/{id}/catalog` | 查看活动元数据 |
| GET | `/data-sources/{id}/catalog/diff?before=&after=` | 查看快照差异 |
| PUT | `/catalog/columns/{id}/semantic-config` | 配置业务含义与敏感等级 |
| PUT | `/data-sources/{id}/permissions` | 配置对象授权 |
| POST | `/analysis-sessions` | 创建分析会话 |
| POST | `/analysis-sessions/{id}/questions` | 生成候选 SQL 和解释 |
| POST | `/query-candidates:validate` | 校验手工或模型候选 SQL 并返回一次性批准标识；候选持久化后再增加按 ID 校验 |
| POST | `/approved-queries/{approvalId}:execute` | 执行一次已批准查询 |
| POST | `/query-results/{id}:explain` | 生成结果解释和图表规格 |
| POST | `/questions/{id}/feedback` | 提交正确性反馈 |
| POST | `/evaluation-runs` | 运行固定评测集 |

## 3. 核心表草案

### 数据源与目录

- `data_source(id, tenant_id, name, dialect, host, port, database_name, username, credential_ref, status, max_rows, timeout_seconds, version, authorization_version, created_at, updated_at)`
- `data_source_credential(id, tenant_id, data_source_id, credential_version, key_id, nonce, ciphertext, active, created_at)`
- `catalog_snapshot(id, tenant_id, data_source_id, version_no, status, object_count, created_at, activated_at)`
- `catalog_table(id, tenant_id, snapshot_id, schema_name, table_name, table_comment, business_name, sensitivity, enabled)`
- `catalog_column(id, tenant_id, table_id, column_name, data_type, nullable, ordinal_no, column_comment, business_name, sensitivity, enabled)`
- `catalog_column_synonym(tenant_id, column_id, ordinal_no, synonym)`
- `catalog_relation(id, tenant_id, snapshot_id, source_table_id, target_table_id, relation_type)`
- `catalog_relation_column(tenant_id, relation_id, ordinal_no, source_column_name, target_column_name)`
- `metric_definition(id, tenant_id, data_source_id, name, description, expression_template, time_dimension_id, status, version)`
- `data_permission(id, tenant_id, subject_type, subject_id, data_source_id, object_type, object_id, mask_policy, created_at)`

数据源 API 只接受结构化主机、端口和库名，不接受完整 JDBC URL 或驱动参数。密码为只写字段，由服务端生成租户绑定的 `credential_ref` 并使用 AES-256-GCM 认证加密；AAD 绑定租户、数据源、凭据 ID、用途和版本。主机、库名和用户名用于连接策略与审计，不作为秘密字段返回给普通读取接口。

连接测试在 DNS 解析后拒绝回环、私网、链路本地、CGNAT、组播、未指定和 IPv4-mapped 私网地址，并把通过校验的 IP 固定到连接池，避免二次解析。MySQL 账号必须只有 `USAGE` 和目标库范围内的 `SELECT`，仅设置 JDBC `readOnly` 不视为通过。

目录查询默认拒绝：分析用户必须同时具有表和列的显式授权；表授权不会隐式开放全部列。新快照完整写入后才原子切换为唯一 `ACTIVE`，失败快照不会替换旧活动版本。

### 提问、审批与执行

- `analysis_session(id, tenant_id, user_id, data_source_id, title, created_at)`
- `analysis_question(id, session_id, question, status, metadata_snapshot_id, prompt_version, created_at)`
- `query_candidate(id, question_id, raw_sql, normalized_sql, explanation, referenced_objects_json, model, status, created_at)`
- `query_validation(id, candidate_id, rule_version, decision, violations_json, estimated_cost_json, policy_hash, approval_id_hash, approval_expires_at, created_at)`
- `query_approval(id, token_hash, tenant_id, user_id, data_source_id, metadata_snapshot_id, data_source_version, authorization_version, rule_version, policy_hash, sql_hash, parameter_hash, normalized_sql, max_rows, timeout_seconds, status, expires_at, consumed_at, created_at)`
- `query_approval_reference(approval_id, ordinal_no, table_id, column_id, schema_name, table_name, column_name)`
- `query_execution(id, tenant_id, approval_id, executor_user_id, data_source_id, status, started_at, completed_at, duration_ms, row_count, truncated, error_code, result_digest)`
- `chart_spec(id, execution_id, type, spec_json, validation_status, created_at)`
- `question_feedback(id, question_id, user_id, rating, corrected_sql, comment, created_at)`
- `audit_event(id, tenant_id, actor_id, action, resource_type, resource_id, decision, detail_json, created_at)`

完整结果默认仅存在短期缓存或响应流中；如后续需要持久化，必须增加保留期、加密和访问控制设计。首批执行器启用 JDBC 游标抓取，并固定限制返回结果为 2 MiB、单个单元格为 256 KiB；总预算耗尽时只在完整行边界截断，单元格超限则拒绝结果。

首批执行接口只接受路径中的 `approvalId`，不接受 SQL、数据源 ID 或资源限制覆盖值。平台短事务原子消费审批、创建 `RUNNING` 执行记录并写开始审计，提交后才访问外部分析库；成功、截断或失败再由独立平台短事务写入唯一终态。平台库不保存完整结果，只保存行数、截断标记、稳定错误码和不可逆结果摘要。

## 4. 审批对象约束

`approvalId` 对应的服务端记录至少绑定：

- 用户和租户
- 数据源
- 规范化 SQL 哈希
- 参数哈希
- 元数据快照与权限版本
- 规则版本和资源限制
- 过期时间及一次性/可复用策略

批准标识使用 256 位随机数，仅在创建响应时返回明文，平台库只保存 SHA-256 摘要。标识默认两分钟过期且只能原子消费一次；执行器发现租户、用户、数据源版本、活动元数据快照、授权版本、规则版本或生命周期状态任一变化时拒绝执行并要求重新校验。

## 5. 错误码示例

- `DATASOURCE_UNAVAILABLE`
- `DATASOURCE_NOT_READ_ONLY`
- `SQL_PARSE_FAILED`
- `SQL_MULTIPLE_STATEMENTS`
- `SQL_COMMENT_FORBIDDEN`
- `SQL_STATEMENT_FORBIDDEN`
- `SQL_FEATURE_FORBIDDEN`
- `SQL_OBJECT_FORBIDDEN`
- `SQL_COLUMN_FORBIDDEN`
- `SQL_WILDCARD_FORBIDDEN`
- `SQL_IDENTIFIER_AMBIGUOUS`
- `SQL_FUNCTION_FORBIDDEN`
- `SQL_VARIABLE_FORBIDDEN`
- `SQL_LIMIT_INVALID`
- `SQL_PARAMETER_INVALID`
- `QUERY_COST_EXCEEDED`
- `QUERY_TIMEOUT`
- `QUERY_CONCURRENCY_EXCEEDED`
- `EXECUTION_ABANDONED`
- `QUERY_RESULT_TOO_LARGE`
- `RESULT_LIMIT_EXCEEDED`
- `APPROVAL_EXPIRED`
- `APPROVAL_ALREADY_USED`
- `APPROVAL_INVALID`

数据库原始错误先分类和脱敏，再映射为业务错误码。

## 6. 配置项

- `PLATFORM_DB_URL/PLATFORM_DB_USERNAME/PLATFORM_DB_PASSWORD`
- `DATASOURCE_ENCRYPTION_KEY_ID`
- `DATASOURCE_ENCRYPTION_KEY_BASE64`
- `DATASOURCE_ENCRYPTION_OLD_KEYS`
- `AI_BASE_URL/AI_API_KEY/AI_CHAT_MODEL`
- `QUERY_DEFAULT_TIMEOUT_SECONDS`
- `QUERY_DEFAULT_MAX_ROWS`
- `QUERY_GLOBAL_CONCURRENCY`
- `QUERY_TENANT_CONCURRENCY`
- `QUERY_DATASOURCE_CONCURRENCY`
- `QUERY_USER_CONCURRENCY`

真实值仅存在于环境或密钥管理系统，仓库只提交 `.env.example`。
