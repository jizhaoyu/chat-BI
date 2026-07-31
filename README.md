# ChatBI 智能分析平台

## 项目定位

这是一个面向 Java 后端求职作品集的安全型 ChatBI 系统。业务人员使用自然语言提问，系统基于授权数据源生成只读 SQL，经过结构化校验和执行治理后返回数据、解释与可视化建议。

项目重点不是让大模型直接连接生产数据库，而是展示数据库接入、元数据建模、权限控制、SQL AST 校验、资源保护、审计追踪和结果评测等后端工程能力。

## 核心场景

1. 管理员注册一个只读 MySQL/PostgreSQL 数据源并测试连接。
2. 系统同步授权的库表字段和注释，管理员配置业务术语与字段含义。
3. 分析用户提出问题，系统生成 SQL 草案和解释。
4. SQL 通过语法树、权限和成本规则后，在受限连接上执行。
5. 系统返回有限结果集、数据解释和经过白名单验证的图表规格。
6. 管理员查看问题、SQL、执行指标和用户反馈，维护评测集。

## 计划技术基线

- Java 21、Spring Boot 3.x、Maven
- Spring MVC、Bean Validation、Spring Security
- Spring JDBC：平台元数据；独立受控连接池：外部分析数据源
- JSqlParser 或等价 SQL AST 解析器，具体版本在验证兼容方言后锁定
- MySQL：平台用户、数据源配置、语义元数据、会话和审计
- Redis：元数据缓存、限流和异步任务状态，MVP 可按需要引入
- Spring AI 作为首选模型适配层，模型只生成结构化候选，不拥有数据库凭据
- Docker Compose：平台数据库和样例分析数据源

## 文档导航

- [需求与范围](docs/01-requirements.md)
- [架构设计](docs/02-architecture.md)
- [接口与数据模型](docs/03-api-and-data.md)
- [开发路线](docs/04-development-plan.md)
- [测试与验收](docs/05-testing-and-acceptance.md)
- [SQL 安全网关策略](docs/06-sql-guard-policy.md)
- [权限矩阵](docs/07-permission-matrix.md)
- [状态机](docs/08-state-machines.md)
- [样例数据与评测集](docs/09-sample-data-and-evaluation.md)
- [威胁模型](docs/10-threat-model.md)
- [演示脚本](docs/11-demo-script.md)

## 开发原则

- 数据库最小权限是第一道防线，SQL 校验不能替代只读账号。
- 模型永远不直接执行 SQL，候选 SQL 必须经过确定性策略检查。
- 禁止 DDL、DML、多语句、注释绕过和未授权对象访问。
- 结果集、执行时间和数据库资源都必须有上限。
- 平台库和业务分析库使用不同连接池与凭据，不得混用事务。
- 图表输出必须是受限结构化规格，不能执行模型生成的脚本。

## 当前状态

当前仅完成开发文档，尚未创建代码脚手架。第一阶段从 `docs/04-development-plan.md` 的 CBI-P1 开始。
"# chat-BI"  
