# 博学（Boxue）项目运维和部署文档

> 文档版本：2.0
> 更新日期：2026-11-20
> 文档定位：本地环境、Docker、数据库、监控、发布与故障处理
> 当前状态：本地原生环境与 Docker 环境均已验证

---

## 1. 环境分层

| 环境 | 用途 | 数据 | 运行方式 |
| --- | --- | --- | --- |
| Local（原生） | 日常开发、调试 | 本地测试数据 | `devenv` 原生进程 |
| Local（Docker） | 环境一致性验证、集成测试 | Docker volumes | Docker Compose |
| CI | 自动测试、构建 | 每次全新容器 | GitHub Actions |
| Staging | 联调、验收、演示 | 可重置测试数据 | Docker Compose / 单机 |
| Production | 正式用户 | 真实数据 | 反向代理 + API + PostgreSQL + Redis + S3 |

当前已落地 Local 原生、Local Docker 和 CI；Staging/Production 尚未部署。

---

## 2. 工具链与路径

所有工具链和中间件位于仓库之外：

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
├─ caches\
├─ downloads\
├─ scripts\
├─ logs\
└─ run\
```

### 2.1 激活环境

```powershell
. C:\Users\ksl-54-it-n04\Project\devenv\scripts\env.ps1
```

会设置：

- `DEVENV_ROOT`
- `JAVA_HOME` / `MAVEN_HOME`
- `FLUTTER_HOME` / `ANDROID_HOME`
- `GRADLE_USER_HOME` / `PUB_CACHE`
- `PGDATA` / `PGPORT=5433` / `PGHOST` / `PGUSER`
- Docker CLI PATH
- 代理自动探测（`127.0.0.1:12450` 可用时设置 `HTTP_PROXY` / `HTTPS_PROXY` / `NO_PROXY`）
- 国内镜像环境变量

### 2.2 版本

| 组件 | 版本 |
| --- | --- |
| JDK | Temurin 21.0.13+9 |
| Maven | 3.9.16 |
| Node.js | 26.13.1 LTS |
| pnpm | 12.21.0 |
| Flutter | 3.50.0 / Dart 3.14 |
| Android SDK | platform 37.0 / build-tools 37.0.0 |
| PostgreSQL | 17.12 |
| Redis | 8.10 |
| RustFS | 1.0.6 |
| Prometheus | 3.16.0 |
| Loki | 3.7.10 |
| Grafana | 13.3.1 |
| Caddy | 2.11.9 |
| Docker Desktop | 4.99.0 / CLI 29.9.3 |
| Docker Compose | v5.7.0 |

---

## 3. 服务与端口

### 3.1 应用端口

| 服务 | 端口 |
| --- | --- |
| API | 8080 |
| Web | 5173 |
| Admin | 5174 |
| Caddy Web 入口 | 8088 |
| Caddy Admin 入口 | 8089 |
| Caddy Admin API | 2019 |

### 3.2 中间件端口

| 服务 | 端口 |
| --- | --- |
| PostgreSQL | 5433 |
| Redis | 6379 |
| RustFS S3 | 9000 |
| RustFS Console | 9001 |
| Prometheus | 9090 |
| Loki | 3100 |
| Grafana | 3000 |
| Mailpit SMTP / Web | 1025 / 8025 |

> 本机 5432 已被另一个 PostgreSQL 16 实例占用，项目统一使用 5433。

### 3.3 原生与 Docker 的关系

原生中间件和 Docker 使用相同的宿主机端口，只能同时运行一套。

从原生切换到 Docker：

```powershell
C:\Users\ksl-54-it-n04\Project\devenv\scripts\stop.ps1 -Services postgres,redis,rustfs
C:\Users\ksl-54-it-n04\Project\devenv\scripts\docker-up.ps1
```

从 Docker 切换到原生：

```powershell
docker compose -f C:\Users\ksl-54-it-n04\Project\workspace\infra\docker\compose.yml down
C:\Users\ksl-54-it-n04\Project\devenv\scripts\start.ps1 -Services postgres,redis,rustfs
```

---

## 4. 快速启动

### 4.1 原生中间件

```powershell
C:\Users\ksl-54-it-n04\Project\devenv\scripts\start.ps1
C:\Users\ksl-54-it-n04\Project\devenv\scripts\status.ps1
C:\Users\ksl-54-it-n04\Project\devenv\scripts\stop.ps1
```

### 4.2 Docker 基础设施

```powershell
C:\Users\ksl-54-it-n04\Project\devenv\scripts\docker-up.ps1
docker compose -f C:\Users\ksl-54-it-n04\Project\workspace\infra\docker\compose.yml ps
```

### 4.3 应用

```powershell
pnpm install
pnpm dev:web
pnpm dev:admin
pnpm dev:desktop

cd services\api
mvn spring-boot:run

cd apps\android
flutter pub get
flutter run
```

---

## 5. 配置管理

### 5.1 配置优先级

```text
.env.local（个人，不提交）
  > 进程环境变量
  > .env.example（团队默认值）
  > application.yml（代码默认值）
```

### 5.2 关键配置

| 配置 | 默认值 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5433/boxue` |
| `SPRING_DATASOURCE_USERNAME` | `boxue` |
| `SPRING_DATASOURCE_PASSWORD` | `boxue_dev_pwd` |
| `S3_ENDPOINT` | `http://127.0.0.1:9000` |
| `S3_ACCESS_KEY` | `boxue_dev` |
| `S3_SECRET_KEY` | `boxue_dev_secret_change_me` |
| `S3_BUCKET` | `boxue-media` |
| `S3_PATH_STYLE` | `true` |
| `S3_MAX_SIZE_MB` | `10` |
| `S3_MAX_REQUEST_MB` | `12` |
| `BOXUE_AI_ENABLED` | `false` |
| `JWT_ISSUER` | `boxue` |
| `JWT_CLIENT_SECRET` | 32 字节以上开发密钥 |
| `JWT_ADMIN_SECRET` | 32 字节以上开发密钥 |
| `JWT_ACCESS_TTL_SECONDS` | `900` |
| `JWT_REFRESH_TTL_DAYS` | `30` |
| `BOXUE_AUTH_REQUIRE_EMAIL_VERIFICATION` | `false` |
| `BOXUE_AUTH_LOG_DEV_LINKS` | `true`（仅本地） |
| `AUTH_RATE_LIMIT_WINDOW_SECONDS` | `60` |
| `AUTH_LOGIN_PER_IP` | `30` |
| `AUTH_LOGIN_PER_ACCOUNT` | `10` |
| `AUTH_ADMIN_LOGIN_PER_IP` | `10` |
| `AUTH_ADMIN_LOGIN_PER_ACCOUNT` | `5` |
| `BOXUE_CSRF_SECRET` | CSRF Token 签名密钥；开发留空时从 Client JWT Secret 派生 |
| `BOXUE_TOTP_SECRET_KEY` | 生产必须配置的 AES 密钥材料；开发留空时回退到 JWT Admin Secret 派生 |
| `BOXUE_WEB_BASE_URL` | `http://localhost:5173` |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025`（Mailpit） |
| `MAIL_FROM` | `no-reply@boxue.local` |

生产环境必须替换所有默认密码与密钥，并启用 HTTPS。

### 5.3 本地管理员

```text
admin / admin
```

仅用于骨架和本地验证，生产环境必须修改。

---

## 6. 数据库

### 6.1 本机实例

```text
host=127.0.0.1  port=5433
superuser: postgres / boxue_pg_admin
app user : boxue / boxue_dev_pwd
database : boxue
extensions: citext, pg_trgm, pgcrypto, uuid-ossp
```

### 6.2 Flyway

迁移目录：

```text
services/api/src/main/resources/db/migration/
├─ V1__init_schema.sql
├─ V2__seed_roles_and_permissions.sql
├─ V3__question_media.sql
├─ V4__session_audience.sql
└─ V5__admin_totp.sql
```

规则：

- 只向前迁移，不修改历史迁移。
- `spring.flyway.enabled=true`。
- JPA `ddl-auto: none`。
- 每次结构调整新增 `V<序号>__<snake_case>.sql`。
- 生产禁止 `flyway clean`。

### 6.3 初始化管理员

初始管理员不通过开放注册创建，也不写死进 Flyway 迁移。使用一次性 CLI：

```powershell
.\scripts\bootstrap-admin.ps1 -Email admin@boxue.local -Username admin
```

脚本会安全提示输入密码（至少 12 位），然后以 `bootstrap-admin` profile 启动：

1. 自动执行 Flyway；
2. 如果已存在 ACTIVE 的 `ADMIN`，直接跳过；
3. 检查邮箱/用户名是否冲突；
4. 使用 BCrypt 写入密码；
5. 创建 `app_user`、`app_user_profile`、`app_user_role`。

首次登录后必须立即修改初始密码。

### 6.3.1 邮箱验证与密码重置

默认关闭邮箱验证，注册后直接 `ACTIVE`。启用方式：

```powershell
$env:BOXUE_AUTH_REQUIRE_EMAIL_VERIFICATION = "true"
```

本地邮件使用 Mailpit：

- SMTP：`localhost:1025`
- Web：`http://localhost:8025`
- 验证链接与重置链接指向 `BOXUE_WEB_BASE_URL`

本地开发默认 `BOXUE_AUTH_LOG_DEV_LINKS=true`，邮件链接会同时写入后端日志，便于调试。
生产环境必须关闭该开关并配置真实 SMTP。

### 6.4 备份与恢复

生产建议：

- PostgreSQL 每日全量 + WAL 归档。
- RustFS/S3 开启版本化。
- 每月至少一次恢复演练。
- 用户侧导出作为补充备份。

本地快速备份：

```powershell
pg_dump -h 127.0.0.1 -p 5433 -U boxue -Fc boxue > boxue.dump
pg_restore -h 127.0.0.1 -p 5433 -U boxue -d boxue boxue.dump
```

### 6.5 迁移验证

```powershell
mvn -B -f services/api/pom.xml verify
```

Testcontainers 用例在无 Docker 时跳过；CI 中必须真实执行。

---

## 7. Docker 部署

### 7.1 当前 Compose 栈

| 容器 | 镜像 | 端口 | 状态 |
| --- | --- | --- | --- |
| `boxue-postgres` | `postgres:17-alpine` | 5433 → 5432 | healthy |
| `boxue-redis` | `redis:8-alpine` | 6379 | healthy |
| `boxue-object-storage` | `rustfs/rustfs:latest` | 9000 / 9001 | healthy |
| `boxue-mailpit` | `axllent/mailpit:latest` | 1025 / 8025 | healthy |

数据卷：

```text
boxue_boxue-postgres-data
boxue_boxue-redis-data
boxue_boxue-s3-data
```

### 7.2 从零安装 Docker Desktop

1. 下载安装包：

```text
https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe
```

2. 管理员安装 WSL2：

```powershell
wsl --install --no-distribution
```

3. **重启 Windows**，让 VirtualMachinePlatform 生效。

4. 管理员静默安装 Docker Desktop：

```powershell
& "C:\Users\ksl-54-it-n04\Project\devenv\downloads\tools\DockerDesktopInstaller.exe" `
  install --quiet --accept-license --backend=wsl-2
```

5. 启动并验证：

```powershell
docker version
docker compose version
docker info
```

### 7.3 镜像加速

配置文件：

```text
C:\Users\ksl-54-it-n04\.docker\daemon.json
```

```json
{
  "builder": {
    "gc": {
      "defaultKeepStorage": "20GB",
      "enabled": true
    }
  },
  "experimental": false,
  "registry-mirrors": [
    "https://docker.1panel.live",
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run"
  ]
}
```

修改后重启 Docker Desktop。

### 7.4 Docker Desktop 配置

```text
C:\Users\ksl-54-it-n04\AppData\Roaming\Docker\settings-store.json
```

关键项：

```json
{
  "AutoStart": true,
  "ProxyHTTPMode": "none"
}
```

- `AutoStart=true`：登录 Windows 后自动启动。
- `ProxyHTTPMode=none`：镜像拉取走 registry mirrors，不依赖系统代理。

### 7.5 已知 Docker 问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `docker info` 返回 500 | WSL2 未重启生效 | 重启 Windows |
| Docker 拉取直连超时 | Docker Hub 不可稳定直连 | 配置 registry mirrors |
| `127.0.0.1:12450` 连接被拒 | Linux VM 内 127.0.0.1 不是宿主机 | 不使用 Docker 代理，改镜像加速 |
| `minio/minio` denied | MinIO 镜像不可用/已归档 | 切换 RustFS |
| Redis 镜像名带镜像前缀 | 显式镜像前缀拉取 | `docker tag` 为 `redis:8-alpine` |

### 7.6 常用命令

```powershell
$compose = "C:\Users\ksl-54-it-n04\Project\workspace\infra\docker\compose.yml"

docker compose -f $compose ps
docker compose -f $compose logs -f
docker compose -f $compose up -d
docker compose -f $compose down
docker compose -f $compose restart
```

重置数据卷（破坏性）：

```powershell
docker compose -f $compose down -v
```
---

## 8. 监控与日志

### 8.1 本地监控栈

| 组件 | 地址 | 用途 |
| --- | --- | --- |
| Prometheus | `http://127.0.0.1:9090` | 指标采集 |
| Loki | `http://127.0.0.1:3100` | 日志聚合 |
| Grafana | `http://127.0.0.1:3000` | 可视化 |
| Caddy | `http://127.0.0.1:8088` / `8089` | 本地反向代理 |

Grafana 已配置 Prometheus / Loki 数据源。默认 `admin / admin`，生产必须修改。

### 8.2 日志规范

- JSON 结构化日志
- 字段：`timestamp`、`level`、`service`、`traceId`、`route`、`status`、`latencyMs`
- 用户标识使用内部 ID
- 不打印密码、Token、邮箱、题干全文
- 管理员操作写审计日志：操作人、目标、前后值摘要、IP、时间

### 8.3 指标

- API 请求量、错误率、P95/P99
- 登录成功/失败、限流触发
- 同步 push/pull 成功失败、冲突数、耗时、游标过期
- 数据库连接池、慢查询
- 导入队列长度、失败率
- 对象存储用量
- JVM 内存/GC/线程
- 磁盘空间

### 8.4 告警建议

- 5xx 错误率持续 5 分钟超阈值
- 同步失败率突增
- 数据库连接池耗尽
- 磁盘使用率 > 80%
- 证书过期 < 14 天
- 备份失败

### 8.5 Actuator

```text
/actuator/health
/actuator/info
/actuator/metrics
/actuator/prometheus
```

生产环境限制暴露范围，并配置认证或内网访问。

---

## 9. CI/CD 与发布

### 9.1 CI

GitHub Actions：

```text
.github/workflows/ci.yml
```

任务：

- contracts：OpenAPI lint + SDK 生成
- web：typecheck + build
- api：Maven verify
- android：analyze + test + debug APK
- security：依赖审计 + gitleaks

版本固定：JDK 21、Node 26、pnpm 12.21.0、Flutter 3.50.0。

### 9.2 部署顺序

```text
数据库迁移（向前兼容）
  -> 后端 API
  -> Web / Admin
  -> Desktop / Android（灰度）
```

### 9.3 发布流程

```text
main 全绿
  -> release/vX.Y.Z
  -> 更新版本号/CHANGELOG
  -> 构建 API / Web / Admin / Desktop / Android
  -> Staging 部署
  -> 冒烟：注册、登录、建题库、刷题、同步
  -> Production
  -> 观察 30 分钟
  -> 打 annotated tag
```

### 9.4 回滚

- 应用回滚到上一个镜像/安装包。
- 数据库不做 down 迁移；用新迁移修复或恢复备份。
- 优先用 feature flag 关闭功能。
- 事故 24 小时内复盘。

### 9.5 桌面端与 Android 发布

Desktop：

- 代码签名
- 自动更新清单
- 灰度发布
- 校验更新包签名

Android：

- `versionCode` 递增
- 签名 AAB/APK
- 内部测试 → 灰度 → 全量
- 保留上一版本回滚包

---

## 10. 安全运维

### 10.1 账号与权限

- 生产禁用默认 `admin/admin`
- 管理员使用强密码 + 2FA（后续）
- RBAC 权限码校验
- 最后一个管理员保护
- 审计日志不可被普通管理员删除

### 10.2 网络与端口

- 生产只暴露 443 和必要端口
- PostgreSQL / Redis / S3 不直接暴露公网
- Caddy/Nginx 终止 TLS
- 管理后台限制 IP 或单独域名

### 10.3 密钥

- `.env.local` / CI Secret / 密钥管理服务
- 不提交密钥、证书、Token
- 定期轮换 JWT Secret、数据库密码、S3 密钥
- 备份加密

### 10.4 数据

- 用户数据按 `owner_id` 隔离
- 生产数据不进入开发环境
- 导出/删除用户数据流程
- 备份与恢复演练

---

## 11. 故障排查手册

### 11.1 环境与工具链

| 问题 | 排查 |
| --- | --- |
| `java` / `mvn` 找不到 | 是否执行了 `env.ps1` |
| Maven 依赖慢 | 检查 `config\maven\settings.xml` 阿里云镜像 |
| pnpm 安装慢/失败 | 检查 `pnpm config get registry` 是否为 npmmirror |
| GitHub 下载失败 | 检查代理端口 12450 是否监听；`env.ps1` 会自动探测 |
| Gradle 下载失败 | 检查 wrapper 是否使用腾讯镜像；Gradle 缓存是否存在 |

### 11.2 数据库

| 问题 | 排查 |
| --- | --- |
| 5433 连接拒绝 | 原生 PG 是否启动；Docker 容器是否 healthy |
| 5432 被占用 | 本机已有 PostgreSQL 16，项目统一用 5433 |
| Flyway 校验失败 | 是否修改了历史迁移；恢复原文件或新增迁移 |
| 找不到 `citext` | 以超级用户执行 `CREATE EXTENSION citext` |
| 迁移在非空库失败 | `baseline-on-migrate=false`，需使用空库或先 baseline |

### 11.3 Docker

| 问题 | 排查 |
| --- | --- |
| Docker 引擎未启动 | 启动 Docker Desktop；确认 WSL2 已重启生效 |
| 镜像拉取超时 | 检查 `daemon.json` registry mirrors；重启 Docker Desktop |
| 端口冲突 | 停止原生中间件或修改 `POSTGRES_PORT` 等变量 |
| 容器 unhealthy | 查看 `docker compose logs <service>` |
| 数据卷异常 | 备份后 `down -v` 重建（破坏性） |

### 11.4 前端

| 问题 | 排查 |
| --- | --- |
| 类型检查失败 | `pnpm typecheck` 查看具体包 |
| Vite 端口冲突 | 检查 5173 / 5174 是否被占用 |
| Electron 二进制缺失 | 设置 `ELECTRON_MIRROR` 后重新安装，或 `ELECTRON_SKIP_BINARY_DOWNLOAD=1` 仅构建 |
| 登录 401/403 | 检查 Token、权限码、CORS |

### 11.5 Android

| 问题 | 排查 |
| --- | --- |
| `flutter analyze` 失败 | 运行 `flutter pub get`，检查 Flutter 版本 |
| compileSdk 不满足插件 | 项目固定 37，启用 core library desugaring |
| sqlite3 原生库下载失败 | 开启代理；或按 `env.ps1` 自动代理；必要时手动放 `.so` |
| Workmanager KGP 警告 | 非致命，等待插件升级 |
| Gradle 下载失败 | 检查 wrapper 镜像与 `GRADLE_USER_HOME` 缓存 |

---

## 12. 验证清单

### 12.1 环境

```powershell
java -version
mvn -version
node -v
pnpm -v
flutter --version
docker version
docker compose version
```

### 12.2 项目

```powershell
pnpm check
pnpm build
mvn -f services/api/pom.xml verify
cd apps\android
flutter analyze
flutter test
flutter build apk --debug
```

### 12.3 Docker

```powershell
$compose = "C:\Users\ksl-54-it-n04\Project\workspace\infra\docker\compose.yml"
docker compose -f $compose ps
docker compose -f $compose exec -T postgres pg_isready -U boxue -d boxue
docker compose -f $compose exec -T redis redis-cli ping
curl.exe http://127.0.0.1:9000/minio/health/live
curl.exe http://127.0.0.1:8025
```

### 12.4 后端健康

```powershell
cd services\api
mvn spring-boot:run
curl.exe http://127.0.0.1:8080/actuator/health
```

---

## 13. 机器级文件清单

| 文件 | 作用 |
| --- | --- |
| `devenv\scripts\env.ps1` | 工具链、代理、Docker PATH |
| `devenv\scripts\start.ps1` / `stop.ps1` / `status.ps1` | 原生中间件 |
| `devenv\scripts\docker-up.ps1` | 启动 Docker Desktop + Compose |
| `devenv\downloads\tools\DockerDesktopInstaller.exe` | Docker Desktop 安装包 |
| `%USERPROFILE%\.docker\daemon.json` | registry mirrors |
| `%APPDATA%\Docker\settings-store.json` | Docker Desktop 自启动与代理模式 |
| `devenv\caches\maven-repo` | Maven 本地仓库 |
| `devenv\caches\pnpm-store` | pnpm 内容寻址存储 |
| `devenv\caches\pub-cache` | Dart/Flutter 包缓存 |
| `devenv\caches\gradle-user-home` | Gradle 缓存与 init 脚本 |

这些文件在 Git 仓库之外，不随代码提交。

---

## 14. 已知限制

1. 国内 Docker registry mirrors 是第三方服务，长期可用性不受项目控制。
2. Docker 容器端口当前绑定 `0.0.0.0`，局域网可访问；如需仅本机访问，可改为 `127.0.0.1:<port>:<port>`。
3. 原生中间件不会开机自启；Docker Desktop 已设置开机自启。
4. 系统代理客户端不会自动启动；`env.ps1` 仅在 12450 已监听时启用代理。
5. `workmanager_android` 的 Kotlin Gradle Plugin 警告仍存在。
6. Staging / Production 尚未部署，备份、TLS、告警、签名发布仍是待办。
7. 当前管理后台、监控仪表盘和告警规则仍是最小骨架。

---

## 15. 运维待办

- 建立 Staging 环境与一键部署脚本
- 配置生产 TLS、域名、反向代理
- 建立 PostgreSQL 备份任务与恢复演练
- 配置 Grafana 仪表盘与告警通知
- 建立日志保留与轮转策略
- 建立发布/回滚 Runbook 并演练
- 配置 Docker 端口仅本机绑定（如需要）
- 替换第三方镜像源或建立自建镜像缓存
