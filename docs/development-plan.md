# 博学（Boxue）开发计划书

> 文档版本：2.0
> 更新日期：2026-11-20
> 文档定位：开发流程、迭代计划、质量门禁与交付标准
> 当前阶段：Sprint 0 已完成，准备进入 Sprint 1

---

## 1. 文档目标

本计划书回答五个问题：

1. 现在做到哪一步了？
2. 下一个功能按什么顺序做？
3. 一个功能从 Issue 到上线要经过哪些步骤？
4. 什么算“完成”，什么情况不允许合并？
5. 如何保证三端一致、契约一致、数据可演进？

适用范围：`apps/*`、`services/api`、`packages/*`、`infra/*`、`docs/*`。

---

## 2. 当前状态（2026-11-20）

### 2.1 已完成

| 项目 | 状态 |
| --- | --- |
| 仓库骨架 | Web / Admin / Desktop / Android / API / Contracts / Infra 已建立 |
| 工具链 | JDK 21、Maven 3.9.16、Node 26、pnpm 12.21、Flutter 3.50、Android SDK 37 |
| 契约工具 | Redocly + openapi-typescript，`contract:lint` / `sdk:gen` 可用 |
| 数据库 | Flyway V1 全量表结构、V2 角色/权限/开关 |
| 后端 | Spring Boot 3.5.16，健康检查、Flyway 自动迁移 |
| 前端 | Web / Admin / Desktop 类型检查与构建通过 |
| Android | `flutter analyze`、`flutter test`、debug APK 构建通过 |
| CI | contracts / web / api / android / security 五个任务 |
| Docker | PostgreSQL / Redis / RustFS / Mailpit 全部 healthy |
| 文档 | 已整理为三份主文档 |

### 2.2 尚未开始

- 注册、登录、刷新 Token、RBAC 业务实现
- 管理员引导与审计
- 题库 CRUD
- 四种题型业务逻辑
- 图片上传与 LaTeX 渲染
- 练习、作答、错题、复习
- 同步推送/拉取业务实现
- 管理后台业务页面
- Staging / Production 部署

### 2.3 下一步

进入 Sprint 1：认证、RBAC 与管理员引导。

---

## 3. 团队与角色

当前按小团队/单人开发设计，角色可以一人多帽：

| 角色 | 职责 |
| --- | --- |
| 产品/需求 | 维护范围、验收标准、优先级 |
| 架构/后端 | 契约、数据库、API、同步、安全 |
| 前端 | Web / Admin / Desktop |
| 移动端 | Flutter Android |
| 测试/质量 | 测试策略、CI、回归 |
| 运维 | 环境、Docker、监控、发布 |

单人开发时，用“PR 描述 + CI + DoD 清单 + 次日复看”代替第二人批准。

---

## 4. 开发原则

1. **契约优先**：先改 OpenAPI，再写后端和客户端。
2. **垂直切片**：一次交付一个端到端能力，不按层横向铺开。
3. **迁移先行**：数据库变更先落 Flyway，再改实体和接口。
4. **本地优先**：客户端先写本地库，再接入同步。
5. **用户隔离默认开启**：所有查询必须带 `owner_id`。
6. **追加而非覆盖**：作答记录只追加；可编辑资源用乐观锁。
7. **小 PR**：单 PR 有效改动尽量 ≤ 400 行。
8. **自动化门禁**：`main` 只接受 CI 全绿的 PR。
9. **文档随代码**：契约、表结构、ADR、命令同一 PR 更新。
10. **AI 不进入关键路径**：只预留接口，默认关闭。

---

## 5. 环境与工具链

开发环境详见 `docs/operations-deployment.md`。核心版本：

| 工具 | 版本 |
| --- | --- |
| JDK | Temurin 21.0.13+9 |
| Maven | 3.9.16 |
| Node.js | 26.13.1 LTS |
| pnpm | 12.21.0 |
| Flutter | 3.50.0 |
| Android SDK | platform 37.0 / build-tools 37.0.0 |
| PostgreSQL | 17.12（端口 5433） |
| Redis | 8.10 |
| RustFS | 1.0.6（S3 9000/9001） |

CI 必须固定与本地一致的主版本，禁止使用漂移的 `stable` 引用。

---

## 6. Git 与分支模型

### 6.1 主干开发

- `main` 永远可构建、可演示。
- 功能分支短生命周期，原则上不超过 3 天。
- 大功能拆成多个可独立合并的切片。
- 使用 Squash Merge，保持 `main` 每个提交对应一个可回溯变更。
- 禁止直接 push `main`，即使单人开发也走 PR。

### 6.2 分支命名

```text
feat/<area>-<short-desc>
fix/<area>-<short-desc>
refactor/<area>-<desc>
docs/<desc>
chore/<desc>
release/v0.x.y
hotfix/v0.x.y
```

`<area>`：`api`、`web`、`admin`、`desktop`、`android`、`sync`、`contract`、`db`、`infra`、`docs`。

### 6.3 提交信息

Conventional Commits：

```text
feat(api): add bank CRUD endpoints
feat(sync): push changes with idempotency key
fix(android): keep local cursor after failed pull
chore(ci): pin Flutter 3.50.0
docs(contract): document sync conflict policy
```

允许 type：`feat`、`fix`、`refactor`、`test`、`docs`、`chore`、`build`、`ci`、`perf`、`revert`。

### 6.4 标签与版本

- 标签：`v0.1.0`、`v0.2.0`，使用 annotated tag。
- 第一阶段版本保持 `0.x`。
- API 路径固定在 `/api/v1`。
- 标签只在发布流水线通过后创建。

---

## 7. 任务准入（Definition of Ready）

Issue 进入开发前必须补齐：

| 项目 | 要求 |
| --- | --- |
| 目标 | 一句话说明用户价值 |
| 验收标准 | 3–7 条可验证清单 |
| 影响面 | contract / api / db / web / admin / desktop / android / sync |
| 契约变更 | 是否变更；是否破坏兼容 |
| 数据变更 | 新增表/列/索引；是否需要回填；回滚策略 |
| 三端范围 | 本期做哪几端；未做的端必须建后续 Issue |
| 同步影响 | 新实体、变更类型、冲突策略 |
| 安全影响 | 权限码、owner 隔离、审计 |
| 监控影响 | 日志、指标、告警 |
| 测试场景 | 单元/集成/同步/E2E |
| 文档影响 | 需要更新哪些文档或 ADR |
| 开关 | 是否需要 feature flag；默认值 |

---

## 8. 标准开发流程

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

### 8.1 创建分支

```powershell
git switch main
git pull --ff-only
git switch -c feat/api-bank-crud
```

### 8.2 契约设计

1. 更新 `packages/contracts/openapi.yaml`。
2. 同步更新 `packages/contracts/API-CONTRACT.md`。
3. 运行：

```powershell
pnpm contract:lint
pnpm sdk:gen
```

4. 生成物不手工修改。

### 8.3 数据库迁移

1. 在 `services/api/src/main/resources/db/migration/` 新增 `V<序号>__<snake_case>.sql`。
2. 只向前迁移，不修改已应用文件。
3. 大表变更使用 expand/contract。
4. 空库和上一版本快照库都要验证。
5. 迁移完成后才开启对应功能。

### 8.4 后端实现

按模块化单体组织：

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

每个功能至少包含：Controller、Service、Repository、DTO、错误码、权限注解、审计、结构化日志。

### 8.5 Web / Admin

- 共用 `packages/ui`，业务状态各自独立。
- 接口调用必须来自生成 SDK。
- 表单校验与服务端一致。
- 错误提示按错误码映射。
- 覆盖空态、加载态、失败态、无权限态。

### 8.6 Desktop

- 渲染层尽量复用 Web。
- 主进程只保留窗口、更新、文件、SQLite 等系统能力。
- 保持 `contextIsolation`、`sandbox`、最小 preload。
- 本地写入先落 SQLite，再进入同步队列。

### 8.7 Android

- Riverpod + go_router + Dio + Drift。
- 页面先读本地库，再后台同步。
- Token 放 `flutter_secure_storage`。
- 图片、通知、后台任务遵循最小权限。
- 新增插件前确认 Android SDK 要求。

### 8.8 同步与三端验收

同步改动必须覆盖：

1. 新设备首次全量同步
2. 离线创建/编辑后推送
3. 双端冲突
4. 推送成功但响应丢失，重试不重复写入
5. 游标过期触发全量
6. 媒体续传
7. 时钟异常
8. 账号切换隔离

至少用两个客户端实例实际走一遍。

### 8.9 PR 与合并

- PR 标题使用 Conventional Commits 风格。
- 按 `.github/pull_request_template.md` 填写。
- CI 全绿 + 至少 1 次评审（单人项目用自查 + CI）。
- Squash Merge，合并后删除分支。
---

## 9. 契约优先规则

### 9.1 唯一事实源

| 内容 | 事实源 |
| --- | --- |
| 机器可读接口 | `packages/contracts/openapi.yaml` |
| 接口行为说明 | `packages/contracts/API-CONTRACT.md` |
| 数据库结构 | `services/api/docs/DATABASE-DESIGN.md` + Flyway SQL |
| 客户端类型 | 由 OpenAPI 生成，禁止手写 |

### 9.2 变更流程

1. 先改 YAML（schema、path、错误码、示例）。
2. 运行 lint / 兼容性检查。
3. 生成 TypeScript 与 Dart 客户端。
4. 后端实现与契约对齐。
5. 客户端升级 SDK。
6. 同一 PR 内完成契约、后端、客户端和测试。

### 9.3 兼容性规则

`/api/v1` 内只做向后兼容变更：

- 新增可选字段
- 新增端点
- 新增枚举值（客户端必须忽略未知值）

破坏性变更必须进入 `/api/v2`，并保留弃用窗口。字段重命名采用“新增 + 双写 + 弃用旧字段”。

`operationId` 一旦发布不得随意修改。

### 9.4 契约门禁

```powershell
pnpm contract:lint
pnpm sdk:gen
```

当前 `openapi.yaml` 只包含 `/health`，Sprint 1 起按模块补齐。

---

## 10. 数据库迁移规则

1. Flyway 是唯一迁移工具；JPA `ddl-auto: none`。
2. 迁移命名：`V<序号>__<snake_case>.sql`，序号全局递增。
3. 已应用迁移禁止修改；修复用新迁移。
4. 每个迁移必须能在空库从零执行。
5. 生产变更遵循 expand/contract。
6. 索引、约束、触发器按数据库设计执行。
7. 种子数据用独立迁移。
8. 管理员引导密码从环境变量读取，禁止写死。
9. 迁移 PR 必须包含：目的、旧数据影响、回填策略、验证方式、回滚预案。

当前迁移：

```text
V1__init_schema.sql
V2__seed_roles_and_permissions.sql
```

---

## 11. 三端对等与同步专项

### 11.1 对等清单

每个功能 Issue 必须勾选：

```text
[ ] API
[ ] Web
[ ] Admin（如涉及）
[ ] Desktop
[ ] Android
[ ] 同步（如涉及用户数据）
```

未完成的端必须说明原因并建立后续 Issue。

### 11.2 客户端本地优先要求

- 本地库 schema 带版本号。
- 写操作先落本地 + outbox。
- 每个推送带 `idempotencyKey`。
- 拉取使用服务端序列号游标。
- 媒体先本地缓存再后台上传。
- 冲突按契约策略处理，不静默覆盖。

### 11.3 同步评审重点

- 是否可能重复写入/丢失写入
- 游标是否持久化
- 重试是否有上限与退避
- 账号切换是否清理本地数据
- 冲突解决后是否产生新变更
- 指标是否覆盖成功/失败/冲突/耗时

---

## 12. 测试策略

### 12.1 分层

| 层级 | 工具 | 范围 | 时机 |
| --- | --- | --- | --- |
| 静态检查 | TypeScript、Flutter analyze、OpenAPI lint | 类型、lint、契约 | 每次 PR |
| 单元测试 | JUnit 5、Vitest（待补）、flutter test | Service、状态、工具 | 每次 PR |
| 集成测试 | Spring Boot Test + Testcontainers PostgreSQL | Repository、Flyway、Security、事务 | 每次 PR（CI） |
| 契约测试 | Redocly + SDK 生成 | 契约合法性 | 每次 PR |
| 同步场景 | 后端集成 + 双客户端 | 离线/重试/冲突/游标 | 同步相关 PR |
| E2E | Playwright（待补） | 注册→建题库→刷题 | main / nightly |
| 手工验收 | 清单 | 视觉、交互、通知、安装包 | 发布前 |

### 12.2 本地无 Docker 策略

- 单元测试本地必须可跑。
- Testcontainers 类使用 `@Testcontainers(disabledWithoutDocker = true)`。
- CI 中 Docker 可用，集成测试必须真实执行。
- 需要本地真实库时使用 `application-test-local.yml` 指向 5433。

### 12.3 最低覆盖要求

- 认证、权限、同步、判分、迁移：关键分支 ≥ 80%。
- 其他后端模块：行覆盖 ≥ 60%。
- 前端/Android：核心 store、路由守卫、离线队列必须有测试。
- 关键路径没有测试视为未完成。

---

## 13. CI 门禁

### 13.1 流水线

```text
contracts -> web -> api -> android -> security
                         |
                    e2e (main/nightly)
```

### 13.2 各阶段

| 阶段 | 命令 | 阻断 |
| --- | --- | --- |
| contracts | `pnpm contract:lint`、`pnpm sdk:gen` | 是 |
| web | `pnpm typecheck`、`pnpm build` | 是 |
| api | `mvn -B -f services/api/pom.xml verify` | 是 |
| android | `flutter analyze`、`flutter test`、`flutter build apk --debug` | 是 |
| security | `pnpm audit --prod --audit-level critical`、gitleaks | 是（高危） |
| e2e | Playwright + Compose | main/nightly |

### 13.3 约束

- 固定 JDK 21、Node 26、pnpm 12.21.0、Flutter 3.50.0。
- 缓存 pnpm store、Maven、Gradle、Pub。
- `concurrency` 取消同分支旧运行。
- PR 流水线目标 ≤ 10 分钟；重活放 nightly。
- 产物保留 7 天。
- `main` 分支保护：必须 PR、CI 绿、禁止 force push。

---

## 14. 代码评审

### 14.1 评审重点

1. 数据隔离：是否所有查询带 `owner_id`
2. 权限：是否声明权限码；管理员接口是否独立认证
3. 契约：实现是否与 OpenAPI 一致；错误码是否复用
4. 迁移：是否向前兼容；是否有回填/索引风险
5. 同步：幂等、游标、冲突、重试
6. 测试：关键路径是否真的断言行为
7. 安全：输入校验、文件类型/大小、SQL 注入、XSS、日志脱敏
8. 性能：N+1、缺索引、全表扫描、无分页
9. 可维护性：命名、模块边界、重复逻辑
10. 文档：契约、表结构、命令是否同步

### 14.2 约定

- 评论前缀：`[blocking]`、`[non-blocking]`、`[nit]`。
- `[blocking]` 必须解决。
- 认证、同步、迁移要求第二人评审；单人阶段用“自查 + CI + 次日复看”。
- PR 超过 400 行有效改动时，评审者有权要求拆分。

---

## 15. 完成定义（Definition of Done）

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
[ ] 同步场景已覆盖并实际演练
[ ] 错误码、空态、加载态、无权限态已处理
[ ] 日志/指标/审计已补充
[ ] 文档/ADR 已更新
[ ] CI 全绿
[ ] 无密钥、无调试代码、无大文件
[ ] 用户可见变化已写入 CHANGELOG（发布时）
```

---

## 16. 发布与回滚

### 16.1 版本策略

- 产品版本：`0.x.y`
- API：`/api/v1`
- 数据库：迁移序号递增，不做 down 迁移
- Android `versionCode` 每次发布递增
- Desktop 语义化版本 + 自动更新清单

### 16.2 发布流程

```text
main 全绿
  -> release/vX.Y.Z
  -> 更新版本号/CHANGELOG
  -> 构建 API / Web / Admin / Desktop / Android
  -> Staging 部署与冒烟
  -> Production：先迁移，再 API，再客户端
  -> 观察 30 分钟
  -> 打 annotated tag
```

### 16.3 回滚

- 应用回滚到上一个镜像/安装包。
- 数据库不做 down 迁移；用新迁移修复或恢复备份。
- 优先用 feature flag 关闭功能。
- 事故 24 小时内产出复盘。

---

## 17. 文档与 ADR

### 17.1 文档职责

| 文档 | 维护时机 |
| --- | --- |
| `docs/project-overview.md` | 产品范围、架构、数据/API 总览变化 |
| `docs/development-plan.md` | 流程、迭代、质量门禁变化 |
| `docs/operations-deployment.md` | 环境、部署、运维、故障处理变化 |
| `packages/contracts/API-CONTRACT.md` | 接口行为变化 |
| `packages/contracts/openapi.yaml` | 每次接口变更 |
| `services/api/docs/DATABASE-DESIGN.md` | 表/索引/约束变化 |
| `docs/adr/NNNN-*.md` | 架构决策 |

### 17.2 ADR 模板

```markdown
# NNNN. 决策标题

- 状态：提议 / 接受 / 废弃 / 被 NNNN 取代
- 日期：YYYY-MM-DD
- 决策人：

## 背景
## 决策
## 备选方案
## 影响
## 参考
```

触发条件：技术选型、模块边界、同步策略、认证方案、部署方式、数据保留策略变化。
---

## 18. 迭代计划

### Sprint 0：工程基线（已完成）

**目标**：让项目具备可持续开发、可构建、可验证的基础。

交付：

- 基线提交与仓库整理
- 工具链与环境契约统一
- Spring Boot 3.5.16、Node 26、Flutter 3.50、compileSdk 37
- OpenAPI lint 与 SDK 生成
- Flyway V1/V2
- Testcontainers 迁移测试
- CI 五个任务
- Web/Admin/Desktop/Android 构建通过
- Docker 基础设施部署

验收结果：

- `pnpm check` 通过
- `pnpm build` 通过
- `mvn verify` 通过
- `flutter analyze` / `flutter test` 通过
- `flutter build apk --debug` 通过
- Docker 四个容器 healthy
- 后端对 Docker PostgreSQL 完成 Flyway 迁移，健康检查 200

---

### Sprint 1：认证、RBAC 与管理员引导

**目标**：完成账号体系的最小闭环。

范围：

- 注册、邮箱验证（可先记录待发邮件）
- 登录、刷新 Token、退出、退出所有设备
- 当前用户、修改资料、修改密码
- 忘记密码、重置密码
- 设备列表与移除设备
- RBAC：用户、角色、权限、角色权限、用户角色
- 管理员引导：CLI/环境变量创建初始管理员
- 审计日志：登录、角色变更、管理员操作
- 限流与登录失败锁定
- Web 登录/注册页
- Android 安全存储与自动登录
- Desktop 登录与本地会话

验收：

- `USER` / `ADMIN` 权限矩阵测试通过
- 初始管理员不能通过开放注册获得
- 最后一个管理员不能被删除或降级
- Token 刷新、撤销、强制下线可用
- 审计日志可查询

风险：

- JWT 设计不一致 → 先定 Token 结构与权限摘要
- 密码策略与限流参数 → 先用保守默认值
- 邮箱验证依赖 → 第一阶段可落库并记录待发送，不阻塞登录

---

### Sprint 2：题库 CRUD

**目标**：完成题库的端到端管理。

范围：

- `qb_bank` 表与接口
- 创建、列表、详情、更新、归档、删除
- 乐观锁 `version`
- 名称唯一（同用户、忽略大小写）
- Web 题库列表/编辑
- Admin 题库统计
- Android 本地库与题库列表
- 同步实体 `BANK`

验收：

- Web 创建题库 → Android 拉取 → Desktop 编辑 → 三端一致
- 并发编辑返回冲突而不是静默覆盖
- 删除/归档行为符合契约

---

### Sprint 3：题目、四种题型、图片与公式

**目标**：完成题目生产与渲染闭环。

范围：

- `qb_question` / `qb_question_version`
- 单选、多选、判断、填空
- 题目草稿、发布、归档、复制
- 标签、知识点
- 图片上传到 S3，`media://` 引用
- KaTeX / flutter_math_fork 渲染
- 服务端 SVG 兜底（可选）
- 题目搜索文本与 pg_trgm 索引

验收：

- 同一道含图片/公式的题目在三端渲染一致
- 题目版本快照可用于历史作答追溯
- 图片去重、类型/大小校验有效

---

### Sprint 4：练习与作答

**目标**：完成刷题主流程。

范围：

- 练习会话：顺序、随机、错题、收藏、复习、自定义、模拟考试
- 题目项、提交答案、跳过、暂停/恢复、结束
- 作答记录追加写入
- 客户端幂等 ID
- 错题自动收集
- 练习结果与历史
- Web / Android 练习页
- Desktop 离线练习

验收：

- 断网作答后恢复网络，记录不重复、不丢失
- 暂停/恢复/结束状态正确
- 结果统计正确

---

### Sprint 5：同步第一条完整链路

**目标**：让题库、题目、作答在三端可靠同步。

范围：

- `sync_change` / `sync_idempotency`
- push / pull / full / status
- 服务端序列号游标
- 冲突检测与返回
- 媒体同步续传
- 客户端 outbox 与重试退避
- Web + Android 双端冲突演练
- 同步指标与日志

验收：

- 通过 `API-CONTRACT.md` §17.10 的 8 个同步场景
- 账号切换不串数据
- 游标过期可全量恢复
- 推送重试不重复写入

---

### Sprint 6：复习、统计、管理后台与稳定性

**目标**：补齐第一阶段运营与学习闭环。

范围：

- 今日复习队列、复习评分、掌握度
- 学习统计：总览、趋势、题库/标签/知识点
- 管理后台：用户、角色、内容统计、同步/导入监控、公告、开关、审计
- Grafana 仪表盘与告警
- Staging 部署
- 第一版验收清单

验收：

- charter 第一版验收标准通过
- Staging 可完整演示注册→建题库→刷题→同步→复习
- 告警可触发

---

### 第二阶段（预留）

- 主观题
- 编程题与独立 Judge 服务
- 课程/章节
- 学习计划、闪卡、考试模拟
- 题库分享、班级/教师/学生
- AI 解析、AI 出题、AI OCR、AI 评分
- 公开题库、审核、订阅与支付

---

## 19. 里程碑

| 里程碑 | 内容 | 状态 |
| --- | --- | --- |
| M0 | 工程基线 + Docker | 已完成 |
| M1 | 认证与 RBAC | 待开始 |
| M2 | 题库 CRUD | 待开始 |
| M3 | 题目与题型 | 待开始 |
| M4 | 练习与作答 | 待开始 |
| M5 | 同步闭环 | 待开始 |
| M6 | 复习/统计/管理后台 | 待开始 |
| M7 | 第一版发布 | 待开始 |

---

## 20. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 同步复杂度超预期 | 数据冲突/丢失 | 先做题库/题目/作答三类实体，固定测试场景 |
| 三端功能漂移 | 体验不一致 | 对等清单 + 生成 SDK + 共享 UI |
| 契约与实现不一致 | 客户端返工 | 契约先行 + CI lint + SDK 生成 |
| 迁移不可回滚 | 发布受阻 | expand/contract + 空库/快照库验证 |
| 范围膨胀 | 延期 | 第一阶段范围锁定，AI/主观题/编程题后置 |
| 网络与依赖不可用 | 构建失败 | 国内镜像 + 代理自动探测 + 缓存 |
| Docker 镜像源失效 | 环境不可用 | 多镜像源 + 原生中间件兜底 |
| 单人开发评审不足 | 质量风险 | CI + DoD + 次日复看 + 关键改动拆分 |

---

## 21. 度量

| 指标 | 目标 |
| --- | --- |
| PR 平均有效改动行数 | ≤ 400 |
| PR 打开到合并 | ≤ 2 个工作日 |
| CI 平均时长 | ≤ 10 分钟 |
| CI 偶发失败率 | < 5% |
| 迁移导致回滚 | 0 |
| 关键路径测试覆盖率 | ≥ 80% |
| 同步相关线上缺陷 | 逐月下降 |

流程本身每两个迭代复盘一次，连续无效的环节删除或简化。

---

## 22. 下一步立即行动

### 22.1 Sprint 1 启动清单

1. 补齐 OpenAPI Auth / User / Admin 端点。
2. 新增 `V3__seed_admin_bootstrap.sql` 或管理员 CLI。
3. 实现注册、登录、刷新、退出。
4. 实现 RBAC 查询与权限注解。
5. 增加登录限流与失败锁定。
6. 写审计日志。
7. Web 登录/注册页。
8. Android secure storage + 自动登录。
9. Desktop 登录与本地会话。
10. 补认证集成测试与权限矩阵测试。

### 22.2 需要先确认的产品问题

- 邮箱验证是否强制？
- 注册是否需要验证码/邀请码？
- Token 有效期与刷新策略？
- 管理员初始密码如何交付？
- 是否需要管理员 2FA 在第一阶段上线？

---

## 23. 附录：PR / Issue 模板

仓库已提供：

- `.github/pull_request_template.md`
- `.github/ISSUE_TEMPLATE/feature.yml`
- `.github/ISSUE_TEMPLATE/bug.yml`

PR 必须填写：变更说明、关联 Issue、影响面、契约/数据变更、三端对等、测试证据、风险与回滚、自查清单。

Issue 必须满足 DoR；缺陷必须包含复现步骤、期望/实际、日志与严重程度。

---

## 24. 当前缺口清单

### 24.1 P0：阻塞第一阶段闭环

| 缺口 | 说明 | 对应计划 |
| --- | --- | --- |
| OpenAPI 不完整 | `openapi.yaml` 目前只有 `/health`，详细契约仍在 `API-CONTRACT.md` | Sprint 1-5 逐步补齐 |
| 认证与 RBAC 未实现 | 只有骨架，没有注册/登录/Token/权限校验 | Sprint 1 |
| 管理员引导未实现 | 没有 CLI / 环境变量创建初始管理员 | Sprint 1 |
| 核心业务未实现 | 题库、题目、练习、复习、同步仍是占位 | Sprint 2-5 |
| 测试不足 | 后端仅 2 个测试，前端/Android 几乎没有业务测试 | 随功能补齐 |
| Staging 未部署 | 没有可演示、可验收的联调环境 | Sprint 6 前 |
| CI 未真实跑通 | 只有工作流文件，尚无远程仓库运行记录 | Sprint 1 前 |

### 24.2 P1：上线前必须补齐

| 缺口 | 说明 |
| --- | --- |
| 备份自动化 | 备份策略已写，但没有定时任务与恢复演练记录 |
| 监控仪表盘/告警 | Prometheus/Grafana 只是可用，没有业务仪表盘和告警规则 |
| 日志链路 | 没有 traceId 实现、日志采集部署与保留策略 |
| 对象存储初始化 | 没有自动创建 bucket、策略与 CORS 配置 |
| 桌面端签名/更新 | 没有证书、安装包与自动更新服务器 |
| Android 签名/发布 | 没有 keystore、AAB、灰度与商店发布流程 |
| 安全评审 | 没有威胁模型、限流参数、2FA、密钥轮换演练 |
| 端口安全 | Docker 端口当前绑定 0.0.0.0，生产/办公网络需改为 127.0.0.1 或防火墙限制 |
| 依赖源风险 | Docker registry mirrors 为第三方服务，需要备选或自建缓存 |

### 24.3 P2：体验与长期能力

| 缺口 | 说明 |
| --- | --- |
| UI/UX 设计 | 没有页面清单、线框图、设计令牌与组件规范 |
| 国际化 | 目前只有中文，未接入 i18n 框架 |
| 可访问性 | 没有键盘、焦点、对比度、读屏检查 |
| 隐私与合规 | 没有隐私政策、用户协议、数据保留与删除流程 |
| 许可证 | 项目许可证未确定；OpenAPI 标记为 UNLICENSED |
| CHANGELOG | 没有版本变更记录 |
| 性能测试 | 没有负载测试、容量评估与慢查询基线 |
| 示例数据 | 没有演示题库、导入模板与种子数据 |
| ADR 实体 | 关键决策仍以摘要形式存在，尚未逐条落成 ADR 文件 |
