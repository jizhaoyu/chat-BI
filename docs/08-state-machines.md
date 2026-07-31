# 状态机

本文档定义核心流程状态，避免 Controller、Application Service 和异步任务各自维护隐式状态。

## 1. 数据源状态

| 状态 | 含义 |
|---|---|
| `DRAFT` | 已创建，尚未通过连接和只读验证 |
| `TESTING` | 正在测试连接 |
| `READY` | 可用于同步和分析 |
| `DISABLED` | 管理员停用 |
| `FAILED` | 最近一次测试或健康检查失败 |

允许流转：

```text
DRAFT -> TESTING -> READY
DRAFT -> TESTING -> FAILED
READY -> DISABLED
DISABLED -> TESTING
FAILED -> TESTING
READY -> FAILED
```

## 2. 元数据快照状态

| 状态 | 含义 |
|---|---|
| `SYNCING` | 正在同步 |
| `ACTIVE` | 当前生效版本 |
| `SUPERSEDED` | 已被新版本替代 |
| `FAILED` | 同步失败 |

新快照只有完整同步成功后才能切换为 `ACTIVE`。同一数据源同一时间只能有一个活动快照。

## 3. 问题状态

| 状态 | 含义 |
|---|---|
| `CREATED` | 问题已保存 |
| `GENERATING` | 正在生成候选 |
| `CANDIDATE_READY` | 候选 SQL 已生成 |
| `GENERATION_FAILED` | 生成失败 |
| `VALIDATED` | 至少一个候选通过校验 |
| `EXECUTED` | 已执行查询 |
| `FAILED` | 流程失败 |

模型生成失败不得触发执行状态。

## 4. 候选 SQL 状态

| 状态 | 含义 |
|---|---|
| `DRAFT` | 原始候选已保存 |
| `VALIDATING` | 正在安全校验 |
| `APPROVED` | 已签发短时批准 |
| `REJECTED` | 被规则拒绝 |
| `EXPIRED` | 批准已过期 |

候选内容变化后必须创建新候选或重新校验，不得复用旧 `approvalId`。

## 5. 执行状态

| 状态 | 含义 |
|---|---|
| `PENDING` | 等待执行 |
| `RUNNING` | 正在查询外部数据源 |
| `SUCCEEDED` | 成功返回受限结果 |
| `TRUNCATED` | 成功但结果被截断 |
| `TIMEOUT` | 超时取消 |
| `CANCELLED` | 用户或系统取消 |
| `FAILED` | 执行失败并已分类脱敏 |

任何执行终态都必须写入耗时、行数、截断标记、错误码或结果摘要。

## 6. 审计要求

以下状态变化必须写审计：

- 数据源创建、测试、启用、禁用。
- 元数据同步开始、成功、失败和活动版本切换。
- SQL 候选生成、校验批准、校验拒绝。
- 查询执行开始和终态。
- 权限、敏感等级和业务术语变更。
