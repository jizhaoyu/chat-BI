# 用户配置清单

以下配置由开发者在本机或部署环境提供，仓库不保存真实密钥。

## 必需环境

- JDK 21。当前机器已安装在 `C:\Program Files\Java\jdk-21`，但全局 `JAVA_HOME` 仍指向 JDK 17。
- Maven 3.9 或更高版本。
- Docker Desktop（含 Compose v2）。当前机器尚未安装或未加入 `PATH`。

## 本地环境变量

参考 `.env.example` 设置：

- `PLATFORM_DB_PASSWORD`：平台应用账号密码。
- `PLATFORM_DB_ROOT_PASSWORD`：仅供 Compose 初始化平台样例库。
- `SAMPLE_DB_PASSWORD`：样例分析只读账号密码，须为 12-128 位且只使用字母、数字和 `._@%+=:-`。
- `SAMPLE_DB_ROOT_PASSWORD`：仅供 Compose 初始化销售样例库。
- `SESSION_COOKIE_SECURE=false`：仅限本地 HTTP 开发；部署环境必须为 `true`。
- `SAMPLE_ANALYSIS_ENABLED=true`：仅在需要连接固定样例库时开启。
- `BOOTSTRAP_IDENTITY_ENABLED=true`：首次本地启动时创建数据管理员；共享或生产环境应关闭。
- `BOOTSTRAP_DATA_ADMIN_USERNAME` / `BOOTSTRAP_DATA_ADMIN_PASSWORD`：首次本地数据管理员，密码至少 12 位且仅从环境读取。

Compose 中的默认密码只用于本地一次性样例环境，不得用于共享或生产环境。修改初始化密码后，如已有旧数据卷，需要执行 `docker compose down --volumes` 重建本地样例数据；该命令会删除本项目 Compose 创建的两个本地数据库卷。

## 验证命令

PowerShell 中临时切换到 Java 21：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
mvn -DskipITs verify
docker compose up -d --wait
mvn verify
```

容器验收测试不得在 CI 中跳过。CI 使用 Java 21 和 Docker 运行完整 `mvn verify`。
