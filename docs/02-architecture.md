# 架构设计

## 1. 架构选择

采用模块化单体，将“生成”和“执行”分成不同信任域。模型生成的内容始终是不可信候选，只有确定性安全网关能够向执行模块发放批准令牌。

```text
Question
  -> Metadata Retrieval
  -> LLM SQL Candidate (untrusted)
  -> Parser / AST Validation
  -> Authorization / Resource Policy
  -> Approved Query
  -> Read-only Executor
  -> Result Sanitizer
  -> Explanation / Chart Spec
```

## 2. 业务模块

- `identity`：用户、角色、租户上下文。
- `datasource`：外部数据源配置、密钥引用、连接池和健康状态。
- `catalog`：Schema、表、列、关系、注释和版本化同步。
- `semantic`：业务术语、指标定义、同义词和敏感等级。
- `question`：分析会话、自然语言问题和候选生成编排。
- `sqlguard`：解析、语句类型、对象权限、函数白名单、行数和成本规则。
- `execution`：只读连接、超时、并发、结果限制和错误分类。
- `visualization`：数据解释与受限图表规格。
- `evaluation`：黄金问题、期望结果和指标。
- `audit`：查询决策链和管理操作。

## 3. 双数据库边界

### 平台数据库

保存平台用户、加密后的数据源配置、元数据快照、问题、SQL 决策和审计，不保存外部业务表。

### 外部分析数据源

- 为每个数据源创建独立、受限的小连接池。
- 数据库账号必须只授予允许对象的 `SELECT`，不依赖应用层模拟只读。
- 设置 JDBC `readOnly`、数据库会话超时和查询取消，但不把这些视为权限替代品。
- 平台事务不得包裹外部查询，避免跨库长事务。

## 4. SQL 审批管线

```text
原始候选
 -> 长度/字符快速检查
 -> 方言解析为 AST
 -> 单语句与 SELECT/CTE 类型检查
 -> 表/列解析和授权
 -> 函数与子查询规则
 -> SELECT * 展开或拒绝
 -> LIMIT 收紧
 -> 可选 EXPLAIN 成本判断
 -> 规范化 SQL + policy_hash + approval_id
```

执行器只接受内部 `ApprovedQuery` 对象，不接受 Controller 或模型传入的裸 SQL。`ApprovedQuery` 包含规范化 SQL、参数、数据源、授权主体、限制、元数据版本、规则版本和短时批准标识。

## 5. 元数据同步

- 使用 JDBC Metadata 和方言适配器读取对象定义。
- 每次同步生成不可变快照，成功后原子切换活动版本。
- 管理员配置的业务术语与物理对象 ID 关联，表重建或字段删除产生待修复告警。
- 提问阶段先进行词法/语义筛选，只把相关对象发给模型，降低 Token 和越权泄露风险。

## 6. 图表规格

模型只能返回受限 JSON，例如：

```json
{
  "type": "bar",
  "title": "各地区销售额",
  "dimension": "region",
  "metrics": [{ "field": "sales_amount", "aggregation": "none" }]
}
```

服务端根据实际结果列验证字段存在、类型适配、数据量合理，再返回客户端。禁止 `eval`、脚本字符串和任意 HTML。

## 7. 凭据与日志

- 开发环境使用环境变量提供主密钥；数据源密码采用认证加密后存储，并支持密钥轮换设计。
- API 响应永不返回密码密文、完整 JDBC 参数或内部错误堆栈。
- 审计保存规范化 SQL 和结果摘要；敏感字段值、完整结果集和模型密钥不得进入日志。

## 8. 可观测性

- 关联字段：`request_id`、`tenant_id`、`user_id`、`datasource_id`、`question_id`、`approval_id`。
- 指标：元数据同步耗时、模型生成耗时、校验拒绝原因、EXPLAIN 拒绝数、查询耗时/行数、取消数、连接池占用和评测正确率。
- 数据源健康检查与平台健康分离，单个数据源异常不得拖垮其他数据源。
