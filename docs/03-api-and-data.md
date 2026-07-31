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
| PUT | `/catalog/columns/{id}/semantic-config` | 配置业务含义与敏感等级 |
| PUT | `/data-sources/{id}/permissions` | 配置对象授权 |
| POST | `/analysis-sessions` | 创建分析会话 |
| POST | `/analysis-sessions/{id}/questions` | 生成候选 SQL 和解释 |
| POST | `/query-candidates/{id}:validate` | 运行安全校验并返回批准标识 |
| POST | `/approved-queries/{approvalId}:execute` | 执行一次已批准查询 |
| POST | `/query-results/{id}:explain` | 生成结果解释和图表规格 |
| POST | `/questions/{id}/feedback` | 提交正确性反馈 |
| POST | `/evaluation-runs` | 运行固定评测集 |

## 3. 核心表草案

### 数据源与目录

- `data_source(id, tenant_id, name, dialect, jdbc_url_ciphertext, username_ciphertext, password_ciphertext, key_version, status, resource_policy_json, created_at, updated_at)`
- `catalog_snapshot(id, data_source_id, version_no, status, object_count, created_at, activated_at)`
- `catalog_table(id, snapshot_id, schema_name, table_name, comment, business_name, sensitivity, enabled)`
- `catalog_column(id, table_id, column_name, data_type, nullable, ordinal_no, comment, business_name, synonyms_json, sensitivity, enabled)`
- `catalog_relation(id, snapshot_id, source_table_id, source_columns_json, target_table_id, target_columns_json, relation_type)`
- `metric_definition(id, tenant_id, data_source_id, name, description, expression_template, time_dimension_id, status, version)`
- `data_permission(id, tenant_id, subject_type, subject_id, data_source_id, object_type, object_id, permission, mask_policy)`

真实实现时应尽量将 JDBC URL 拆为主机、端口、库名等结构化配置再加密，避免直接接受任意连接参数。是否允许完整 URL 由安全 Spike 决定。

### 提问、审批与执行

- `analysis_session(id, tenant_id, user_id, data_source_id, title, created_at)`
- `analysis_question(id, session_id, question, status, metadata_snapshot_id, prompt_version, created_at)`
- `query_candidate(id, question_id, raw_sql, normalized_sql, explanation, referenced_objects_json, model, status, created_at)`
- `query_validation(id, candidate_id, rule_version, decision, violations_json, estimated_cost_json, policy_hash, approval_id_hash, approval_expires_at, created_at)`
- `query_execution(id, validation_id, executor_user_id, status, started_at, completed_at, duration_ms, row_count, truncated, error_code, result_schema_json, result_digest)`
- `chart_spec(id, execution_id, type, spec_json, validation_status, created_at)`
- `question_feedback(id, question_id, user_id, rating, corrected_sql, comment, created_at)`
- `audit_event(id, tenant_id, actor_id, action, resource_type, resource_id, decision, detail_json, created_at)`

完整结果默认仅存在短期缓存或响应流中；如后续需要持久化，必须增加保留期、加密和访问控制设计。

## 4. 审批对象约束

`approvalId` 对应的服务端记录至少绑定：

- 用户和租户
- 数据源
- 规范化 SQL 哈希
- 参数哈希
- 元数据快照与权限版本
- 规则版本和资源限制
- 过期时间及一次性/可复用策略

执行器发现任一上下文变化时拒绝执行，要求重新校验。

## 5. 错误码示例

- `DATASOURCE_UNAVAILABLE`
- `DATASOURCE_NOT_READ_ONLY`
- `SQL_PARSE_FAILED`
- `SQL_STATEMENT_NOT_ALLOWED`
- `SQL_OBJECT_FORBIDDEN`
- `SQL_FUNCTION_FORBIDDEN`
- `QUERY_COST_EXCEEDED`
- `QUERY_TIMEOUT`
- `RESULT_LIMIT_EXCEEDED`
- `APPROVAL_EXPIRED`

数据库原始错误先分类和脱敏，再映射为业务错误码。

## 6. 配置项

- `PLATFORM_DB_URL/PLATFORM_DB_USERNAME/PLATFORM_DB_PASSWORD`
- `DATASOURCE_ENCRYPTION_KEY`
- `AI_BASE_URL/AI_API_KEY/AI_CHAT_MODEL`
- `QUERY_DEFAULT_TIMEOUT_SECONDS`
- `QUERY_DEFAULT_MAX_ROWS`
- `QUERY_GLOBAL_CONCURRENCY`

真实值仅存在于环境或密钥管理系统，仓库只提交 `.env.example`。
