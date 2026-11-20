# 博学（Boxue）开发流程

> 文档状态：v1.0 / 可执行草案
> 适用阶段：第一阶段（题库、刷题、错题复习、多端同步、管理后台）
> 适用范围：`apps/*`、`services/api`、`packages/*`、`infra/*`、`docs/*`
> 最后更新：2026-11-20

---

## 1. 目的

博学不是单一 Web 应用，而是“一个后端 + 四个客户端 + 一套契约 + 一套数据模型”的多端系统。
开发流程的目标不是增加审批，而是保证下面四件事始终成立：

1. **契约一致**：Web、Electron、Android 使用同一份 OpenAPI 契约，不靠口头约定。
2. **数据可演进**：数据库变更可追溯、可回放、向前兼容，不出现“线上库和代码对不上”。
3. **三端不漂移**：每个功能都明确 Web / 桌面 / Android 的完成状态，不允许默认只做一端。
4. **同步可验证**：离线、重试、冲突、游标过期等场景有固定测试，不靠手工点页面。

流程遵循“轻量但硬门禁”：日常开发保持主干、小步提交；合并前由 CI 和清单把住关键质量。

---

## 2. 十条工作原则

1. **契约优先**：先改 `packages/contracts/openapi.yaml`，再写后端和客户端。
2. **垂直切片**：一次交付一个端到端能力，而不是“先把所有 Controller 写完”。
3. **迁移先行**：数据库变更先落 Flyway，再改实体和接口。
4. **本地优先**：客户端先写本地库/本地状态，再接入同步；离线不是异常路径。
5. **用户隔离默认开启**：任何查询都必须带 `owner_id` 约束，不接受客户端传入的归属人。
6. **追加而非覆盖**：作答记录只追加；可编辑资源使用乐观锁；同步变更使用服务端序列号。
7. **小 PR**：单个 PR 尽量不超过 400 行有效改动；同步、认证、迁移类改动必须单独评审。
8. **自动化门禁**：`main` 只接受 CI 全绿的 PR；不靠“我本地跑过了”。
9. **文档随代码**：接口、表结构、ADR、运行命令和代码在同一个 PR 内更新。
10. **AI 不进入关键路径**：AI 只保留接口、任务表和开关，默认关闭。

---

## 3. 环境与事实源

### 3.1 环境分层

| 环境 | 用途 | 数据 | 部署方式 |
| --- | --- | --- | --- |
| Local | 日常开发、调试 | 本地假数据/测试数据 | `devenv` 原生进程；Docker 可选 |
| CI | 自动测试、构建 | 每次全新容器 | GitHub Actions |
| Staging | 联调、验收、演示 | 可重置的测试数据 | Docker Compose / 单机 |
| Production | 正式用户 | 真实数据 | 反向代理 + API + PostgreSQL + Redis + S3 |

第一阶段可以只有 Local + CI；在第一次对外演示前补 Staging。

### 3.2 本机事实源（当前机器）

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| PostgreSQL 17 | `127.0.0.1:5433` | 库 `boxue`，用户 `boxue` |
| Redis 8 | `127.0.0.1:6379` | 本地缓存/会话 |
| RustFS S3 | `http://127.0.0.1:9000` | S3 兼容；控制台 `9001` |
| API | `http://127.0.0.1:8080` | Spring Boot |
| Web | `http://127.0.0.1:5173` | Vite |
| Admin | `http://127.0.0.1:5174` | Vite |
| Prometheus | `http://127.0.0.1:9090` | 指标 |
| Loki | `http://127.0.0.1:3100` | 日志 |
| Grafana | `http://127.0.0.1:3000` | 可视化 |
| Caddy | `http://127.0.0.1:8088` / `8089` | Web/Admin 统一入口 |

> 本机 5432 已被另一个 PostgreSQL 16 实例占用，因此项目统一使用 **5433**。
> Docker Compose 也建议映射 `5433:5432`，保持两种运行方式的宿主机端口一致。

### 3.3 配置优先级

```text
.env.local（个人，不提交）
  > 进程环境变量
  > .env.example（团队默认值，提交）
  > application.yml（代码默认值）
```

规则：

- 新增配置项必须同时更新 `.env.example` 和 `docs/environment-setup.md`。
- 密钥、令牌、签名证书只放 `.env.local` 或 CI Secret，禁止提交。
- `application.yml` 只放安全默认值；本地差异放 `application-local.yml`。
- 端口、库名、S3 bucket 等“团队约定”必须在本文档和 `.env.example` 中一致。

### 3.4 工具链版本固定

| 工具 | 固定版本 | 位置 |
| --- | --- | --- |
| JDK | Temurin 21.0.13+9 | `devenv/toolchains/jdk-21.0.13+9` |
| Maven | 3.9.16 | `devenv/toolchains/maven-3.9.16` |
| Node.js | 26.13.1 LTS | `devenv/toolchains/node-v26.13.1` |
| pnpm | 12.21.0 | `devenv/toolchains/pnpm-12.21.0` |
| Flutter | 3.50.0 stable | `devenv/toolchains/flutter-3.50.0` |
| Android SDK | platform 37.0 / build-tools 37.0.0 | `devenv/toolchains/android-sdk` |
| Gradle | 9.9.1（独立版） | `devenv/toolchains/gradle-9.9.1` |

CI 必须固定同样的主版本；禁止使用 `channel: stable` 这类漂移引用。
当前 `ci.yml` 仍需在 Sprint 0 中把 Node 22 对齐到 26、Flutter 固定到 3.50.0。

---

## 4. Git 与分支模型

### 4.1 主干开发

- `main` 永远保持可构建、可演示。
- 功能分支短生命周期，原则上不超过 3 天；大功能拆成多个可独立合并的切片。
- 使用 **Squash Merge** 合并 PR，保持 `main` 每个提交对应一个可回溯的变更。
- 禁止直接 push `main`；即使单人开发也走 PR，让 CI 留下证据。

### 4.2 分支命名

```text
feat/<area>-<short-desc>      例：feat/api-bank-crud
fix/<area>-<short-desc>       例：fix/sync-cursor-overflow
refactor/<area>-<desc>        例：refactor/web-question-store
docs/<desc>                   例：docs/development-workflow
chore/<desc>                  例：chore/ci-pin-flutter
release/v0.2.0                发布准备（仅 cherry-pick / 版本号）
hotfix/v0.2.1                 线上紧急修复
```

`<area>` 取值：`api`、`web`、`admin`、`desktop`、`android`、`sync`、`contract`、`db`、`infra`、`docs`。

### 4.3 提交信息

采用 Conventional Commits：

```text
<type>(<scope>): <subject>

[optional body]
[optional footer]
```

示例：

```text
feat(api): add bank CRUD endpoints
feat(sync): push changes with idempotency key
fix(android): keep local cursor after failed pull
chore(ci): pin Flutter 3.50.0
docs(contract): document sync conflict policy
```

允许的 type：`feat`、`fix`、`refactor`、`test`、`docs`、`chore`、`build`、`ci`、`perf`、`revert`。

### 4.4 标签与版本

- 标签：`v0.1.0`、`v0.2.0`，使用 annotated tag。
- 第一阶段版本保持 `0.x`；API 路径固定在 `/api/v1`。
- 标签只在发布流水线通过后创建，不在日常合并时随手打。

### 4.5 基线提交

截至本文档创建时，仓库 `main` 还没有任何提交，骨架文件均为未跟踪状态。
Sprint 0 的第一件事是完成基线提交，确保后续每个变更都有可对比的起点。

---

## 5. 任务准入（Definition of Ready）

Issue 进入开发前必须补齐以下信息，缺一项就退回补充：

| 项目 | 要求 |
| --- | --- |
| 目标 | 一句话说明用户价值，不写技术实现 |
| 验收标准 | 3–7 条可验证的 Given/When/Then 或清单 |
| 影响面 | 勾选 contract / api / db / web / admin / desktop / android / sync |
| 契约变更 | 是否有 OpenAPI 变更；是否破坏兼容 |
| 数据变更 | 是否新增表/列/索引；是否需要回填；回滚策略 |
| 三端范围 | 本期做哪几端；未做的端必须建后续 Issue |
| 同步影响 | 是否产生新实体、变更类型、冲突策略 |
| 安全影响 | 涉及哪些权限码；是否涉及 owner 隔离/审计 |
| 监控影响 | 需要新增哪些日志、指标、告警 |
| 测试场景 | 单元/集成/同步/E2E 各需要哪些用例 |
| 文档影响 | 需要更新哪些 docs 或 ADR |
| 开关 | 是否需要 feature flag；默认值是什么 |

推荐使用 `.github/ISSUE_TEMPLATE/feature.yml` 创建功能 Issue。

---

## 6. 标准开发流程

```text
Issue(DoR)
  -> 分支
  -> 契约设计
  -> Flyway 迁移
  -> 后端实现
  -> 契约校验 + SDK 生成
  -> Web / Admin
  -> Desktop
  -> Android
  -> 同步与三端验收
  -> 自动化测试
  -> 文档/ADR
  -> PR + CI + Review
  -> Squash Merge
  -> 发布/打标签
```

### 6.1 第 1 步：创建分支

```powershell
git switch main
git pull --ff-only
git switch -c feat/api-bank-crud
```

### 6.2 第 2 步：契约设计

1. 更新 `packages/contracts/openapi.yaml`：
   - path、operationId、请求/响应 schema、错误码、示例。
2. 如涉及行为约定，同步更新 `docs/api-contract.md` 对应章节。
3. 运行契约校验（Sprint 0 增加脚本）：

```powershell
pnpm contract:lint
```

4. 生成客户端类型（生成物不手工修改）：

```powershell
pnpm sdk:gen
```

> 生成的 `packages/sdk/generated/**` 已被 `.gitignore` 忽略。
> 如果需要提交生成结果，必须改为“生成后校验无 diff”，而不是人工编辑。

### 6.3 第 3 步：数据库迁移

1. 在 `services/api/src/main/resources/db/migration/` 新增迁移：

```text
V1__init_schema.sql
V2__seed_roles_and_permissions.sql
V3__seed_admin_bootstrap.sql
V4__add_bank_share_flag.sql
```

2. 迁移只向前，不修改已应用文件。
3. 大表变更使用 expand/contract：
   - 先加可空列/新表；
   - 双写或回填；
   - 再加约束/索引；
   - 代码切换完成后再删旧结构。
4. 在空库和“上一版本快照库”上各跑一次迁移测试。
5. 迁移完成后才把 `spring.flyway.enabled` 改为 `true`。

### 6.4 第 4 步：后端实现

按模块化单体组织，包名与 `docs/project-charter.md` 的模块划分一致：

```text
com.boxue.api.auth
com.boxue.api.user
com.boxue.api.role
com.boxue.api.content
com.boxue.api.practice
com.boxue.api.review
com.boxue.api.sync
com.boxue.api.media
com.boxue.api.imports
com.boxue.api.admin
com.boxue.api.audit
com.boxue.api.ai
```

每个功能至少包含：

- Controller：只做参数校验、权限注解、调用 Service。
- Service：事务边界、业务规则、owner 校验。
- Repository：所有用户数据查询必须带 `owner_id`。
- DTO：请求/响应与实体分离。
- 错误码：使用 `docs/api-contract.md` 中定义的业务错误码。
- 审计：管理员操作写 `app_audit_log`。
- 日志：结构化字段，禁止打印密码、Token、完整题干正文。

### 6.5 第 5 步：Web / Admin

- Web 与 Admin 共用 `packages/ui`，但业务状态各自独立。
- 所有接口调用必须来自生成 SDK，不手写 URL。
- 表单校验与服务端校验保持一致。
- 错误提示使用后端错误码映射，不直接展示原始异常。
- 涉及题库/题目的页面必须覆盖空态、加载态、失败态、无权限态。

### 6.6 第 6 步：Desktop

- 渲染层尽量复用 Web 组件与页面逻辑。
- 主进程只保留窗口、更新、文件、SQLite 等系统能力。
- 保持 `contextIsolation: true`、`nodeIntegration: false`、`sandbox: true`。
- preload 只暴露白名单 API，禁止把 Node 能力整体暴露给渲染进程。
- 本地数据写入必须先落 SQLite，再进入同步队列。

### 6.7 第 7 步：Android

- 状态管理：Riverpod；路由：go_router；网络：Dio；本地库：Drift。
- 页面先读本地库，再后台同步；离线时不得阻塞用户操作。
- Token 放 `flutter_secure_storage`，不写普通 SharedPreferences。
- 图片选择、通知、后台任务遵循最小权限。
- `compileSdk` 与插件要求保持一致；新增插件前先确认其对 Android SDK 的要求。

### 6.8 第 8 步：同步与三端验收

任何涉及同步的改动，必须覆盖 `docs/api-contract.md` §17.10 的场景：

1. 新设备首次全量同步。
2. 离线创建/编辑，恢复网络后推送。
3. 两端同时编辑同一题，冲突按策略处理。
4. 推送成功但响应丢失，客户端重试不重复写入。
5. 游标过期后触发全量同步。
6. 媒体上传中断后继续。
7. 客户端时钟异常不影响服务端序列号。
8. 同步过程中退出登录/切换账号，不串数据。

同步验收至少使用两个客户端实例（Web + Android 或 Web + Desktop）实际走一遍。

### 6.9 第 9 步：测试

见第 10 节。先跑最小闭环：

```powershell
pnpm typecheck
pnpm build
mvn -f services/api/pom.xml test
cd apps/android; flutter analyze; flutter test
```

### 6.10 第 10 步：文档与 ADR

- 用户可见行为变化：更新 `docs/api-contract.md`、`README.md` 或相关模块 README。
- 架构/技术选型变化：新增 ADR（见第 16 节）。
- 数据库变化：更新 `docs/database-design.md`。
- 环境/命令变化：更新 `docs/environment-setup.md`。

### 6.11 第 11 步：PR 与合并

- PR 标题使用 Conventional Commits 风格。
- PR 描述按 `.github/pull_request_template.md` 填写。
- CI 全绿 + 至少 1 次评审（单人项目用自查清单 + CI 代替人工批准）。
- 合并方式：Squash Merge。
- 合并后删除远端分支。

---

## 7. 契约优先规则

### 7.1 唯一事实源

| 内容 | 事实源 |
| --- | --- |
| 机器可读接口 | `packages/contracts/openapi.yaml` |
| 接口行为说明 | `docs/api-contract.md` |
| 数据库结构 | `docs/database-design.md` + Flyway SQL |
| 客户端类型 | 由 OpenAPI 生成，禁止手写 |

### 7.2 变更流程

1. 先改 YAML（schema、path、错误码、示例）。
2. 运行 lint / 兼容性检查。
3. 生成 TypeScript 与 Dart 客户端。
4. 后端实现与契约对齐。
5. 客户端升级 SDK。
6. 在同一个 PR 内完成契约、后端、客户端和测试。

### 7.3 兼容性规则

- `/api/v1` 内只做**向后兼容**变更：
  - 新增可选字段；
  - 新增端点；
  - 新增枚举值（客户端必须能忽略未知值）。
- 破坏性变更必须进入 `/api/v2`，并保留旧版本的弃用窗口。
- 字段重命名采用“新增新字段 + 双写 + 弃用旧字段”，不直接改名。
- `operationId` 一旦发布不得随意修改，否则生成客户端会大面积断裂。

### 7.4 契约门禁

Sprint 0 需补充：

```json
{
  "scripts": {
    "contract:lint": "redocly lint packages/contracts/openapi.yaml",
    "contract:bundle": "redocly bundle packages/contracts/openapi.yaml -o packages/contracts/dist/openapi.json",
    "sdk:gen:ts": "openapi-typescript packages/contracts/openapi.yaml -o packages/sdk/generated/types.ts",
    "sdk:gen": "pnpm sdk:gen:ts"
  }
}
```

Dart 客户端生成可在 CI 或发布前执行，生成物放 `packages/sdk/generated/dart/`。

---

## 8. 数据库迁移规则

1. Flyway 是唯一迁移工具；JPA 只做映射，`ddl-auto: none` 永远不改为 `update`。
2. 迁移文件命名：`V<序号>__<snake_case>.sql`，序号全局递增，不复用。
3. 已应用的迁移禁止修改；修复用新迁移。
4. 每个迁移必须能在空库上从零执行成功。
5. 生产变更遵循 expand/contract，保证旧版本 API 在滚动发布期间仍可工作。
6. 索引、约束、触发器按 `docs/database-design.md` 执行；新增索引需说明查询场景。
7. 种子数据（角色、权限、功能开关）用独立迁移，不用手工 SQL。
8. 管理员引导密码从环境变量读取，禁止在迁移中写死。
9. 迁移 PR 必须包含：
   - 变更目的；
   - 对旧数据的影响；
   - 回填/清理策略；
   - 验证方式（空库 + 快照库）；
   - 回滚预案（向前修复，不做 down 迁移）。

---

## 9. 三端对等与同步专项

### 9.1 对等清单

每个功能 Issue 必须勾选：

```text
[ ] API
[ ] Web
[ ] Admin（如涉及管理能力）
[ ] Desktop
[ ] Android
[ ] 同步（如涉及用户数据）
```

未完成的端必须：

- 在 Issue 中说明原因；
- 建立后续 Issue 并关联；
- 不在 PR 描述中写“后续再补”而没有链接。

### 9.2 客户端本地优先要求

- 本地库 schema 带版本号，升级必须有迁移。
- 写操作先落本地 + outbox，再异步推送。
- 每个推送带 `idempotencyKey`，服务端按 `sync_idempotency` 去重。
- 拉取使用服务端序列号游标，不依赖客户端时间。
- 媒体文件先落本地缓存，再后台上传，失败可续传。
- 冲突处理遵循 `docs/api-contract.md` §17.6，不在客户端静默覆盖。

### 9.3 同步代码评审重点

- 是否可能重复写入/丢失写入；
- 游标是否持久化；
- 失败重试是否有上限与退避；
- 账号切换是否清理本地数据；
- 冲突解决后是否产生新的服务端变更；
- 指标是否覆盖成功/失败/冲突/耗时。

---

## 10. 测试策略

### 10.1 分层

| 层级 | 工具 | 范围 | 运行时机 |
| --- | --- | --- | --- |
| 静态检查 | TypeScript、ESLint（待补）、Flutter analyze | 类型、lint、格式 | 每次提交/PR |
| 单元测试 | JUnit 5 + AssertJ/Mockito、Vitest（待补）、flutter test | Service、工具、状态 | 每次 PR |
| 集成测试 | Spring Boot Test + Testcontainers PostgreSQL | Repository、Flyway、Security、事务 | 每次 PR（CI 有 Docker） |
| 契约测试 | Redocly + 生成器 | OpenAPI 合法性、SDK 可生成 | 每次 PR |
| 同步场景 | 后端集成 + 双客户端模拟 | 离线/重试/冲突/游标 | 每次同步相关 PR |
| E2E | Playwright（待补） | 注册→登录→建题库→刷题 | main / nightly |
| 手工验收 | 清单 | 视觉、交互、通知、安装包 | 发布前 |

### 10.2 本地无 Docker 的策略

本机未安装 Docker，Testcontainers 集成测试会失败。约定：

- 单元测试必须在本机可跑。
- 集成测试类使用 `@Testcontainers(disabledWithoutDocker = true)`，无 Docker 时自动跳过。
- CI 中 Docker 可用，集成测试必须真实执行，不允许跳过。
- 需要本地连真实库时，使用 `application-test-local.yml` 指向 `127.0.0.1:5433`。

### 10.3 最低覆盖要求

- 认证、权限、同步、判分、迁移：关键分支覆盖率 ≥ 80%。
- 其他后端模块：行覆盖率 ≥ 60%。
- 前端/Android：核心 store、路由守卫、离线队列必须有测试。
- 覆盖率不是目的；**关键路径没有测试视为未完成**。

### 10.4 测试数据

- 使用固定 ID、固定时间、固定随机种子，保证可重复。
- 不使用生产数据；日志中不输出题干全文和用户隐私。
- 图片等媒体使用小体积 fixture，不提交大文件。

---

## 11. CI 门禁

### 11.1 目标流水线

```text
contracts  ->  web  ->  api  ->  android  ->  desktop
     \________________ security / audit ________________/
                         |
                    e2e (main/nightly)
```

### 11.2 各阶段要求

| 阶段 | 命令 | 失败即阻断 |
| --- | --- | --- |
| contracts | `pnpm contract:lint`、SDK 生成 | 是 |
| web | `pnpm --filter @boxue/web typecheck`、`build`、单测 | 是 |
| admin | `pnpm --filter @boxue/admin typecheck`、`build` | 是 |
| desktop | `pnpm --filter @boxue/desktop typecheck`、`build` | 是 |
| api | `mvn -B -f services/api/pom.xml verify` | 是 |
| android | `flutter analyze`、`flutter test`、`flutter build apk --debug` | 是 |
| security | 依赖审计、密钥扫描 | 是（高危） |
| e2e | Playwright + Compose | main/nightly |

### 11.3 CI 约束

- 固定版本：JDK 21、Node 26、pnpm 12.21.0、Flutter 3.50.0。
- 缓存：pnpm store、Maven `~/.m2`、Gradle、Pub。
- `concurrency` 取消同一分支的旧运行。
- PR 流水线目标 ≤ 10 分钟；重活放 nightly。
- 构建产物：Android debug APK、API jar、Web/Admin dist，保留 7 天。
- `main` 分支保护：必须 PR、必须 CI 绿、禁止 force push。

### 11.4 当前 CI 的 Sprint 0 修正项

1. Node 22 → 26，Flutter `stable` → `3.50.0`。
2. 增加 `contracts` job 和 `pnpm contract:lint`。
3. API 从 `test` 改为 `verify`，并确保 Testcontainers 可用。
4. Android 增加 `flutter build apk --debug`。
5. 增加 `pnpm build`（web/admin/desktop）而不是只做 typecheck。
6. 增加依赖审计与密钥扫描。

---

## 12. 代码评审

### 12.1 评审重点

按优先级检查：

1. **数据隔离**：是否所有用户数据查询都带 `owner_id`。
2. **权限**：接口是否声明权限码；管理员接口是否独立认证。
3. **契约**：实现是否与 OpenAPI 一致；错误码是否复用。
4. **迁移**：是否向前兼容；是否有回填/索引风险。
5. **同步**：幂等、游标、冲突、重试是否正确。
6. **测试**：关键路径是否有测试；测试是否真的断言行为。
7. **安全**：输入校验、文件类型/大小、SQL 注入、XSS、日志脱敏。
8. **性能**：N+1、缺索引、全表扫描、无分页。
9. **可维护性**：命名、模块边界、重复逻辑。
10. **文档**：契约、表结构、运行命令是否同步。

### 12.2 评审约定

- 评论前缀：`[blocking]`、`[non-blocking]`、`[nit]`。
- `[blocking]` 必须解决后才能合并。
- 认证、同步、迁移、支付类改动要求第二人评审；单人阶段用“自查清单 + CI + 次日复看”。
- PR 超过 400 行有效改动时，评审者有权要求拆分。

---

## 13. 完成定义（Definition of Done）

一个功能只有同时满足以下条件才算完成：

```text
[ ] 验收标准全部通过
[ ] OpenAPI 已更新并通过 lint
[ ] SDK 已重新生成，客户端无手写 URL
[ ] Flyway 迁移已提交并在空库/快照库验证
[ ] API 单元测试 + 集成测试通过
[ ] Web / Admin 类型检查与构建通过
[ ] Desktop 类型检查与构建通过
[ ] Android analyze + test 通过
[ ] 三端对等清单已勾选，未完成端有后续 Issue
[ ] 同步场景（如涉及）已覆盖并实际演练
[ ] 错误码、空态、加载态、无权限态已处理
[ ] 日志/指标/审计已补充
[ ] 文档/ADR 已更新
[ ] CI 全绿
[ ] 无密钥、无调试代码、无大文件
[ ] 用户可见变化已写入 CHANGELOG（发布时）
```

---

## 14. 发布与回滚

### 14.1 版本策略

- 产品版本：`0.x.y`，第一阶段不追求 1.0。
- API 版本：`/api/v1`，破坏性变更进入 `/api/v2`。
- 数据库：迁移序号单调递增，不做 down 迁移。
- 客户端：Android `versionCode` 每次发布递增；Desktop 使用语义化版本 + 自动更新清单。

### 14.2 发布流程

```text
main 全绿
  -> 创建 release/vX.Y.Z
  -> 更新版本号/CHANGELOG
  -> 构建 API / Web / Admin / Desktop / Android
  -> 部署 Staging
  -> 冒烟测试（注册、登录、建题库、刷题、同步）
  -> 部署 Production（先迁移，再 API，再客户端）
  -> 观察 30 分钟指标
  -> 打 annotated tag vX.Y.Z
  -> 公告/归档
```

### 14.3 部署顺序

1. 数据库迁移（向前兼容）。
2. 后端 API（同时兼容旧/新客户端）。
3. Web / Admin。
4. Desktop / Android（灰度发布）。

### 14.4 回滚

- 应用：回滚到上一个镜像/安装包。
- 数据库：不做 down 迁移；用新迁移修复，或恢复备份（最后手段）。
- 功能：优先用 feature flag 关闭，而不是回滚整个版本。
- 事故：记录时间线、影响、根因、改进项，24 小时内产出复盘。

---

## 15. 可观测性与运维

### 15.1 日志

- JSON 结构化日志，字段至少包含：`timestamp`、`level`、`service`、`traceId`、`route`、`status`、`latencyMs`。
- 用户标识使用内部 ID，不打印邮箱/手机号/题干全文。
- 管理员操作单独审计日志，保留操作人、目标、前后值摘要、IP、时间。

### 15.2 指标

必须覆盖：

- API 请求量、错误率、P95/P99；
- 登录成功/失败、限流触发；
- 同步推送/拉取成功失败、冲突数、平均耗时、游标过期次数；
- 数据库连接池、慢查询；
- 导入任务队列长度、失败率；
- 对象存储用量；
- JVM 内存/GC/线程；
- 磁盘空间。

### 15.3 告警

- 5xx 错误率持续 5 分钟超阈值；
- 同步失败率突增；
- 数据库连接池耗尽；
- 磁盘使用率 > 80%；
- 证书过期 < 14 天；
- 备份失败。

### 15.4 备份

- PostgreSQL 每日全量 + WAL 归档（生产）。
- 对象存储开启版本化。
- 每月至少一次恢复演练，记录恢复时间。

---

## 16. 文档与 ADR

### 16.1 文档职责

| 文档 | 维护时机 |
| --- | --- |
| `docs/project-charter.md` | 范围/架构/路线变化 |
| `docs/api-contract.md` | 接口行为变化 |
| `packages/contracts/openapi.yaml` | 每次接口变更 |
| `docs/database-design.md` | 表/索引/约束变化 |
| `docs/environment-setup.md` | 工具链/端口/命令变化 |
| `docs/development-workflow.md` | 流程变化 |
| `docs/adr/NNNN-*.md` | 架构决策 |

### 16.2 ADR 规则

- 目录：`docs/adr/`
- 命名：`0001-use-postgresql-jsonb.md`
- 结构：背景 → 决策 → 备选方案 → 影响 → 状态（提议/接受/废弃/取代）
- 触发条件：技术选型、模块边界、同步策略、认证方案、部署方式、数据保留策略变化。
- 已有决策（charter 第 19 节）可逐步迁移为 ADR，不要求一次性重写。

### 16.3 文档评审

- 文档 PR 与代码 PR 同等对待，必须有人 review。
- 过期文档比没有文档更危险；发现不一致时，优先修文档或在 Issue 中登记。

---

## 17. 迭代节奏与协作

### 17.1 节奏

- 迭代周期：1 周（小步）或 2 周（含联调）。
- 周一：计划会，确认本周切片与验收标准。
- 每日：异步站会（昨天/今天/阻塞）。
- 周四：联调/演示准备。
- 周五：演示 + 回顾 + 下周候选。
- 每迭代结束打一个 `v0.x.y` 标签（可选，但建议）。

### 17.2 WIP 限制

- 同时进行的功能不超过 2 个。
- 每人最多 1 个开发任务 + 1 个评审任务。
- 预留 20% 容量处理缺陷、技术债和线上问题。

### 17.3 看板列

```text
Backlog -> Ready -> In Progress -> In Review -> Verify -> Done
```

- `Ready`：满足 DoR。
- `In Review`：PR 已开，CI 运行中。
- `Verify`：合并后在 Staging/本地完成验收。
- `Done`：满足 DoD。

### 17.4 单人开发变体

- 用 PR 描述代替“口头交接”。
- 用 CI + DoD 清单代替第二人批准。
- 每周固定一次“自审”：回看本周 PR，检查契约/迁移/同步遗漏。
- 关键变更（认证、同步、迁移）隔天复查一遍再发布。

---

## 18. 第一阶段落地路线

### Sprint 0：工程基线（1 周）

1. 完成 `main` 基线提交。
2. 统一环境契约：PostgreSQL 5433、S3 9000/9001、端口表、`.env.example`。
3. 固定 CI 版本：Node 26、Flutter 3.50.0、pnpm 12.21.0。
4. 增加 `contract:lint`、`sdk:gen` 脚本。
5. 编写 `V1__init_schema.sql` + `V2__seed_roles_and_permissions.sql`。
6. 开启 Flyway，补一个 Testcontainers 迁移测试。
7. 补 PR / Issue 模板。

**验收**：`pnpm check`、`mvn verify`、`flutter test`、`flutter build apk --debug` 全部通过。

### Sprint 1：认证与 RBAC 垂直切片

- 注册、登录、刷新、登出、当前用户；
- 管理员引导与权限码；
- Web 登录页 + Token 刷新；
- Android secure storage + 自动登录；
- Desktop 登录与本地会话；
- 审计日志与限流。

**验收**：三种角色（USER/ADMIN/SUPER_ADMIN）权限矩阵测试通过。

### Sprint 2：题库 CRUD

- `qb_bank` 迁移、接口、Web/Admin 页面、Android 本地库；
- 乐观锁与 owner 隔离；
- 空态/失败态/无权限态。

**验收**：Web 创建题库 → Android 拉取 → Desktop 编辑 → 三端一致。

### Sprint 3：题目与四种题型

- 单选、多选、判断、填空；
- 题目版本、发布/归档；
- 图片上传到 S3；
- KaTeX + flutter_math_fork 渲染快照测试。

**验收**：同一道含图片/公式的题目在三端渲染一致。

### Sprint 4：练习与作答

- 练习会话、提交答案、跳过、暂停/恢复、结果；
- 作答幂等；
- 错题本自动生成。

**验收**：断网作答后恢复网络，作答记录不重复、不丢失。

### Sprint 5：同步第一条完整链路

- 题库/题目/作答的 push/pull；
- 游标、幂等、冲突策略；
- Web + Android 双端冲突演练。

**验收**：通过 §17.10 的 8 个同步场景。

### Sprint 6：复习、统计与管理后台

- 复习队列、掌握度、统计；
- 管理后台用户/角色/监控/公告/开关；
- Grafana 仪表盘与告警；
- 发布 Staging。

**验收**：完成第一版验收标准（charter 第 17 节）。

---

## 19. 流程度量

每月复盘以下指标，不用于考核个人：

| 指标 | 目标 |
| --- | --- |
| PR 平均有效改动行数 | ≤ 400 |
| PR 从打开到合并 | ≤ 2 个工作日 |
| CI 平均时长 | ≤ 10 分钟 |
| CI 偶发失败率 | < 5% |
| 迁移导致的回滚 | 0 |
| 同步相关线上缺陷 | 逐月下降 |
| 关键路径测试覆盖率 | ≥ 80% |

流程本身也要迭代：连续两个迭代无效的环节，删除或简化。

---

## 20. 命令速查

### 20.1 激活环境

```powershell
. C:\Users\ksl-54-it-n04\Project\devenv\scripts\env.ps1
```

### 20.2 中间件

```powershell
C:\Users\ksl-54-it-n04\Project\devenv\scripts\start.ps1
C:\Users\ksl-54-it-n04\Project\devenv\scripts\status.ps1
C:\Users\ksl-54-it-n04\Project\devenv\scripts\stop.ps1
```

### 20.3 前端

```powershell
pnpm install
pnpm dev:web
pnpm dev:admin
pnpm dev:desktop
pnpm typecheck
pnpm build
```

### 20.4 后端

```powershell
cd services\api
mvn test
mvn spring-boot:run
```

### 20.5 Android

```powershell
cd apps\android
flutter pub get
flutter analyze
flutter test
flutter run
flutter build apk --debug
```

### 20.6 一键检查

```powershell
.\scripts\check.ps1
```

### 20.7 Docker（安装 Docker 后）

```powershell
.\scripts\db-up.ps1
.\scripts\db-down.ps1
.\scripts\db-logs.ps1
```

---

## 21. Sprint 0 执行结果（2026-11-20）

Sprint 0 已完成，环境与工程基线已统一：

| 项目 | 处理结果 |
| --- | --- |
| PostgreSQL 端口 | 统一为 **5433**（README、`.env.example`、`application.yml`、`application-local.yml`、Compose） |
| 对象存储 | 统一称“S3 兼容存储”；本机与 Compose 均使用 RustFS，端口均为 9000/9001 |
| Spring Boot | 统一升级并固定为 **3.5.16** |
| Node / pnpm | 统一为 Node **26**、pnpm **12.21.0** |
| Flutter | 固定 **3.50.0**，CI 不再使用漂移的 `stable` |
| Android compileSdk | 固定 **37**，启用 core library desugaring |
| 契约校验 | 新增 `pnpm contract:lint`、`pnpm sdk:gen` 与 `redocly.yaml` |
| Flyway | 新增 V1/V2，开启 Flyway，增加 Testcontainers 迁移测试 |
| 基线提交 | 已完成 `chore: import project skeleton` |
| CI | 固定版本，增加 contracts / web / api / android / security 任务 |

详细验证记录见 `docs/sprint-0-report.md`。