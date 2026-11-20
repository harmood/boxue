# 本地开发环境

## 工具链位置

```text
C:\Users\ksl-54-it-n04\Project\devenv\
├─ toolchains\
│  ├─ jdk-21.0.13+9
│  ├─ maven-3.9.16
│  ├─ flutter-3.50.0
│  ├─ android-sdk
│  ├─ gradle-9.9.1
│  ├─ node-v26.13.1
│  ├─ pnpm-12.21.0
│  └─ git-2.56.1
├─ middleware\
│  ├─ postgresql-17.12
│  ├─ redis-8.10.3
│  ├─ storage-rustfs-1.0.6
│  ├─ prometheus-3.16.0
│  ├─ loki-3.7.10
│  ├─ grafana-13.3.1
│  └─ caddy-2.11.9
└─ caches\
   ├─ maven-repo
   ├─ gradle-user-home
   ├─ pub-cache
   ├─ npm-cache
   └─ pnpm-store
```

## 激活环境

在项目根目录：

```powershell
. ..\devenv\scripts\env.ps1
```

或在任意目录：

```powershell
. C:\Users\ksl-54-it-n04\Project\devenv\scripts\env.ps1
```

激活后会设置：

- `DEVENV_ROOT`
- `JAVA_HOME`
- `MAVEN_HOME`
- `FLUTTER_HOME`
- `ANDROID_HOME` / `ANDROID_SDK_ROOT`
- `GRADLE_USER_HOME`
- `PUB_CACHE`
- `NPM_CONFIG_CACHE`
- `MAVEN_OPTS`
- `PGDATA` / `PGPORT=5433` / `PGHOST` / `PGUSER`
- `PUB_HOSTED_URL` / `FLUTTER_STORAGE_BASE_URL`

## 本机服务

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| PostgreSQL | `127.0.0.1:5433` | 库 `boxue`，应用用户 `boxue` |
| Redis | `127.0.0.1:6379` | 无密码，仅监听本机 |
| RustFS S3 | `http://127.0.0.1:9000` | 控制台 `http://127.0.0.1:9001` |
| Prometheus | `http://127.0.0.1:9090` | 指标 |
| Loki | `http://127.0.0.1:3100` | 日志 |
| Grafana | `http://127.0.0.1:3000` | 可视化 |

启动/停止/状态：

```powershell
C:\Users\ksl-54-it-n04\Project\devenv\scripts\start.ps1
C:\Users\ksl-54-it-n04\Project\devenv\scripts\stop.ps1
C:\Users\ksl-54-it-n04\Project\devenv\scripts\status.ps1
```

> 本机 5432 已被另一个 PostgreSQL 16 实例占用，项目统一使用 **5433**。
> 对象存储统一走 S3 兼容 API：本机是 RustFS，Docker Compose 中可用 MinIO。

## 数据库初始化

本机实例已初始化：

```text
数据库：boxue
应用用户：boxue / boxue_dev_pwd
超级用户：postgres / boxue_pg_admin
扩展：citext、pg_trgm、pgcrypto、uuid-ossp
```

迁移由 Flyway 管理：

```text
services/api/src/main/resources/db/migration/
├─ V1__init_schema.sql
└─ V2__seed_roles_and_permissions.sql
```

启动后端的 `local` profile 会自动执行迁移。

## 验证

```powershell
java -version
mvn -version
flutter --version
node --version
pnpm --version
```

项目检查：

```powershell
pnpm check
pnpm build
mvn -f services/api/pom.xml verify
cd apps\android; flutter analyze; flutter test
```

## 已知问题

### Docker 未安装

当前环境未检测到 Docker。`infra/docker/compose.yml` 已准备好，安装 Docker Desktop 后可以启动 PostgreSQL、Redis、S3 兼容存储和 Mailpit。

### SQLite 原生库下载

`sqlite3 3.9.1` 的 build hook 会从 GitHub Releases 下载 Android 原生库。若直连失败：

1. 配置代理后重试；
2. 使用镜像下载对应 `.so`，校验 SHA256 后放入
   `.dart_tool/hooks_runner/shared/sqlite3/build/download-<hash>/`；
3. 在本地 `apps/android/pubspec_overrides.yaml` 配置 `url_pattern`（该文件已加入 .gitignore）。

### Workmanager Kotlin 警告

`workmanager_android 0.10.x` 构建时会提示仍在使用旧方式应用 Kotlin Gradle Plugin。
当前为非致命警告，不影响 APK 构建。

### Flutter material 弃用提示

Flutter 3.50 对 `package:flutter/material.dart` 给出弃用提示，当前不影响编译和测试。后续可根据 Flutter 版本迁移到新的 material 包。

### Android compileSdk

`flutter_local_notifications` 要求较高的 compileSdk，项目固定为 **37**，并启用 core library desugaring。

## 环境与 Git

- 工具链和缓存都在 `devenv` 下
- `devenv` 在 Git 仓库之外
- 只有 `workspace` 内容会提交
- 开发流程见 `docs/development-workflow.md`
