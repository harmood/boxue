# Sprint 0 执行报告

> 日期：2026-11-20
> 状态：已完成
> 范围：工程基线、环境契约、契约校验、Flyway 初始化、CI、一键检查

---

## 1. 交付清单

| 项目 | 结果 |
| --- | --- |
| Git 基线 | 已完成首个提交 `chore: import project skeleton` |
| 环境契约 | PostgreSQL 统一 5433；S3 统一 9000/9001；README / `.env.example` / application / Compose 已对齐 |
| Spring Boot | 从 3.4.5 升级并固定到 3.5.16；springdoc 2.10.0 |
| Node / pnpm | 引擎要求 Node ≥26、pnpm ≥12；CI 固定 Node 26、pnpm 12.21.0 |
| Flutter | CI 固定 3.50.0；Android compileSdk 37；启用 core library desugaring |
| Android 依赖 | Riverpod 3.5、go_router 18、Drift 2.35、Workmanager 0.10、flutter_local_notifications 23 等已对齐 Flutter 3.50 |
| 契约工具 | 新增 `@redocly/cli`、`openapi-typescript`；`contract:lint`、`sdk:gen` 可用 |
| OpenAPI | 修复 3.1 `nullable`、security、license、tag 描述等问题，lint 零告警 |
| Flyway | `V1__init_schema.sql`、`V2__seed_roles_and_permissions.sql`；启动时自动迁移 |
| 迁移测试 | 新增 `FlywayMigrationTest`（Testcontainers，无 Docker 时自动跳过） |
| CI | 新增 contracts / web / api / android / security 五个任务，固定版本并启用缓存 |
| 本地检查 | `scripts/check.ps1` 覆盖 typecheck、构建、后端 verify、Flutter analyze/test |
| 文档 | README、environment-setup、开发流程、迁移说明、PR/Issue 模板、ADR 规范 |

---

## 2. 验证结果

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 依赖锁定 | `pnpm install --frozen-lockfile` | 通过 |
| 契约校验 | `pnpm contract:lint` | 通过，0 error / 0 warning |
| SDK 生成 | `pnpm sdk:gen` | 通过，生成 `packages/sdk/generated/types.ts` |
| 前端类型检查 | `pnpm typecheck` | 通过（web / admin / desktop） |
| 前端构建 | `pnpm build` | 通过（web / admin / desktop） |
| 后端构建与测试 | `mvn -f services/api/pom.xml verify` | 通过；2 个测试，1 个 Testcontainers 用例在无 Docker 时跳过 |
| Flutter 静态检查 | `flutter analyze` | 通过，无问题 |
| Flutter 测试 | `flutter test` | 通过 |
| Android 构建 | `flutter build apk --debug` | 通过，产出约 177 MB debug APK |
| Flyway 迁移（本机 PG17） | psql 执行 V1+V2 | 通过；2 个角色、11 个权限、12 条角色权限、4 个 AI 开关 |
| Flyway + Spring Boot | 在空库启动 `local` profile | 通过；自动执行 2 个迁移，`/actuator/health` 返回 200 |

Flyway 空库验证结果：

```text
app_role              2
app_permission        11
app_role_permission   12
app_feature_flag      4（全部 disabled）
```

---

## 3. 已知问题与处理

### 3.1 本机无 Docker

- Testcontainers 用例在本地自动跳过（`disabledWithoutDocker = true`）。
- CI 的 `api` 任务必须真实执行迁移测试。
- Docker 安装后可运行 `scripts/db-up.ps1` 验证 Compose 路径。

### 3.2 SQLite 原生库下载

`sqlite3 3.9.1` 的 build hook 默认从 GitHub Releases 下载 Android 原生库。
当前网络直连 GitHub 可能失败。可选方案：

1. 配置可用代理后重新构建；
2. 通过镜像下载对应 `.so`，放入
   `.dart_tool/hooks_runner/shared/sqlite3/build/download-<hash>/`；
3. 在本地 `pubspec_overrides.yaml` 中配置 `url_pattern`（不要提交到仓库）。

Sprint 0 已通过镜像下载并校验 SHA256 后完成构建。

### 3.3 Workmanager Kotlin 插件警告

`workmanager_android 0.10.x` 仍以旧方式应用 Kotlin Gradle Plugin，构建时给出非致命警告。
当前不影响 APK 构建；后续插件升级支持 Built-in Kotlin 后消除。

---

## 4. Sprint 1 进入条件

- `main` 保持 CI 全绿。
- 契约、Flyway、SDK 生成流程可用。
- 本机 PostgreSQL 5433、Redis、S3 可用。
- Android debug APK 可构建。
- 下一步实现注册、登录、刷新、RBAC 与管理员引导。

---

## 5. 追加处理（2026-11-20）

### 5.1 系统代理与 SQLite 原生库

- 系统代理 `127.0.0.1:12450` 已开启。
- `devenv\scripts\env.ps1` 已加入代理自动探测，设置 `HTTP_PROXY` / `HTTPS_PROXY` / `NO_PROXY`。
- 已删除手工缓存的 x64 SQLite 原生库并重新构建，Flutter 自动从 GitHub 下载、校验 SHA256 并完成 APK 构建。
- 结论：SQLite 原生库问题已解决。

### 5.2 Docker 环境

- Docker Desktop `4.99.0` / CLI `29.9.3` 已安装并运行。
- Docker Compose `v5.7.0` 已安装。
- WSL `3.0.5` 与 `VirtualMachinePlatform` 已启用。
- 已配置国内镜像加速，PostgreSQL、Redis、RustFS、Mailpit 镜像均已拉取。
- `boxue` Compose 栈已启动，四个容器全部 healthy。
- 后端已对 Docker PostgreSQL 成功执行 Flyway V1/V2，`/actuator/health` 返回 200。
