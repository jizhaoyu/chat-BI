# SQL 安全网关策略

本文档定义 MVP 阶段 SQL 安全网关的可执行策略。实现时以本文档为准；未明确允许的语法默认拒绝。

## 1. 基本原则

- 输入 SQL 视为不可信内容，无论来源是模型、用户修正还是评测集。
- 网关只接受单条语句，解析失败、解析不完整或方言不确定时拒绝。
- 执行器不得接收裸 SQL，只接收网关签发的 `ApprovedQuery`。
- 应用层校验不是数据库权限替代品，外部数据源账号仍必须是最小只读权限。
- 所有拒绝必须返回稳定错误码，并写入审计事件。

## 2. MVP 允许语法

仅支持 MySQL 子集：

- 单条 `SELECT`。
- 只读 CTE：`WITH name AS (SELECT ...) SELECT ...`。
- 显式列选择、列别名、表别名。
- `WHERE` 中的比较、范围、空值判断、`IN`、`LIKE`、布尔组合。
- `INNER JOIN`、`LEFT JOIN`，必须带可解析的 `ON` 条件。
- `GROUP BY`、`HAVING`、`ORDER BY`。
- 聚合函数：`COUNT`、`SUM`、`AVG`、`MIN`、`MAX`。
- 标量函数白名单：`DATE`、`YEAR`、`MONTH`、`DAY`、`COALESCE`、`IFNULL`、`ROUND`。
- 子查询：仅允许 `SELECT` 子查询，且子查询内对象同样经过权限校验。
- `LIMIT`：允许已有 `LIMIT`，但会被收紧到数据源资源策略上限。

## 3. MVP 禁止语法

- DML：`INSERT`、`UPDATE`、`DELETE`、`REPLACE`、`MERGE`。
- DDL：`CREATE`、`ALTER`、`DROP`、`TRUNCATE`、`RENAME`。
- 权限和会话操作：`GRANT`、`REVOKE`、`SET`、`USE`、`LOCK`、`UNLOCK`。
- 存储过程和动态执行：`CALL`、`PREPARE`、`EXECUTE`、`DEALLOCATE`。
- 文件、系统、网络相关函数或语法，例如 `LOAD_FILE`、`INTO OUTFILE`。
- 多语句、堆叠语句、客户端命令、无法被 AST 完整覆盖的注释绕过。
- `SELECT *`。MVP 直接拒绝；后续如改为展开，必须先实现列级授权展开测试。
- 未加限制的高风险函数、用户变量、临时表、窗口函数和递归 CTE。
- 跨数据源查询、跨库联邦查询和未授权 Schema 引用。

## 4. 对象与列解析

- SQL 中的表必须解析到当前数据源活动元数据快照。
- 表、列、Schema 名称统一按方言规则规范化后匹配。
- 别名不得绕过权限校验，所有投影列、条件列、排序列、分组列和连接列都必须可追踪到授权列。
- 未限定列名如果存在歧义，拒绝执行。
- 表达式列必须由授权列和允许函数组成。
- 敏感列允许参与受控聚合，但默认不得原样返回；具体由列敏感等级和脱敏策略决定。

## 5. LIMIT 与资源策略

- 默认最大返回行数：`QUERY_DEFAULT_MAX_ROWS`。
- 数据源可设置更严格的最大行数、超时时间、并发数和响应大小。
- 无 `LIMIT` 时自动注入默认上限。
- 有 `LIMIT` 时取用户值和策略上限的较小值。
- `OFFSET` 允许但必须受最大扫描/响应策略约束；无法估算时可拒绝。
- 可选 `EXPLAIN` 仅作为成本辅助，不作为唯一安全判断。

## 6. ApprovedQuery 约束

`ApprovedQuery` 至少包含：

- `approvalId`
- `tenantId`
- `userId`
- `dataSourceId`
- `metadataSnapshotId`
- `permissionVersion`
- `ruleVersion`
- `normalizedSql`
- `sqlHash`
- `resourceLimits`
- `expiresAt`

执行前必须重新比对租户、用户、数据源、SQL 哈希、权限版本、元数据版本和过期时间。任一不匹配均拒绝。

## 7. 首批回归用例

- 合法：简单筛选、聚合、分组、排序、两表 JOIN、非递归 CTE、授权子查询。
- 非法：写操作、DDL、多语句、`SELECT *`、危险函数、越权表、越权列、注释混淆。
- 边界：大小写混合、反引号标识符、别名引用、同名列歧义、超大 `LIMIT`、无 `LIMIT`。
- 重放：批准后替换 SQL、用户、数据源、权限版本、元数据版本或过期时间。
