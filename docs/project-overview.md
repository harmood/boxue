# 博学（Boxue）项目整体介绍

> 文档版本：2.0
> 更新日期：2026-11-20
> 文档定位：项目唯一的产品与架构总览
> 适用阶段：第一阶段（题库、刷题、错题复习、多端同步、管理后台）

---

## 1. 项目定位

### 1.1 一句话定位

博学是一个面向个人学习者的多端学习平台。当前阶段以“题库 + 刷题 + 错题复习”为核心，后续逐步扩展为包含课程、学习计划、主观题、编程题和 AI 能力的综合性学习平台。

### 1.2 当前阶段目标

第一阶段只完成刷题及其附属能力：

- 题库创建、管理与分享边界控制
- 单选、多选、判断、填空四种客观题
- 题目与选项支持图片
- 题干、选项、解析支持 LaTeX 公式
- 练习会话与作答记录
- 错题本、收藏、笔记
- 复习计划与学习统计
- Web、桌面端、Android 三端功能对等
- 多端数据同步与离线使用
- 开放注册、用户管理、角色与权限管理
- 开发者/管理人员使用的管理后台与监控
- 预留 AI 扩展接口，但第一阶段不启用

### 1.3 长期目标

在刷题能力稳定后，逐步扩展为综合性学习平台：

- 主观题、编程题与在线判题
- 课程、章节、课时
- 学习计划、闪卡、模拟考试
- 题库分享、班级/小组、教师/学生
- AI 解析、AI 出题、AI 学习助手
- 公开题库、内容审核、订阅与支付

### 1.4 当前阶段明确不做

- 主观题和简答题
- 编程题和在线判题
- 视频课程、直播
- 社区、排行榜、评论
- 多机构、多租户
- 支付与订阅
- 公开题库市场
- AI 解析、AI 出题、AI 讲解等实际 AI 功能
- 移动端 iOS（保留 Flutter 扩展空间，但第一阶段只做 Android）

---

### 1.5 产品分析结论

- 第一阶段选择**知识型题库**，不做编程题和在线判题；编程题放到第二阶段独立 Judge 服务。
- 差异化不在社区和排行榜，而在**多端本地优先、同步、导入、公式、错题复习**。
- MVP 顺序：
  1. Web + 后端闭环
  2. Android 练习端
  3. 桌面端导入工作台
  4. 学习分析、复习与增强能力
- 主要风险：范围膨胀、题库导入难度、题型不断膨胀、同步复杂度、过早依赖 AI、版权合规、桌面端打包与更新。

---

## 2. 产品原则
### 2.1 三个用户客户端功能对等

Web、桌面端、Android 不是按“功能分工”设计，而是面向不同使用场景：

| 平台 | 核心场景 |
| --- | --- |
| Web | 免安装、跨设备、快速访问、大屏管理与批量编辑 |
| 桌面端 | 长时间学习、本地文件导入、离线使用、系统级能力 |
| Android | 碎片时间、移动场景、拍照采集、通知提醒 |

三端核心功能保持一致：

- 登录与账号
- 题库管理
- 题目创建与编辑
- 题目导入与导出
- 练习
- 错题本
- 收藏与笔记
- 学习统计
- 多端同步
- 设置与数据管理

平台差异只体现在交互方式和增强能力上，不体现在“某个功能只有某一端才有”。

### 2.2 多端同步是核心能力

同步不是附属功能，而是基础能力。用户应该能够：

- 在 Web 上创建题库，在 Android 上继续刷题
- 在桌面端离线练习，联网后同步到 Web
- 在任意设备查看错题、笔记和统计
- 在任意设备继续上一次练习
- 设备更换后恢复完整学习数据

因此客户端采用“本地数据库 + 服务端同步”的架构，而不是纯在线表单应用。

### 2.3 后端分为业务服务端与管理后台

| 端 | 使用者 | 职责 |
| --- | --- | --- |
| 业务服务端 | Web / 桌面 / Android | 认证、同步、题库、练习、复习、媒体、导入任务 |
| 管理后台 | 开发者 / 运营人员 | 用户、角色、内容、监控、日志、公告、功能开关 |

普通用户通过客户端间接使用业务 API，不直接访问服务端；管理后台有独立入口和独立权限边界。

### 2.4 本地优先

- 客户端先写本地数据库，再异步同步。
- 离线是正常路径，不是异常路径。
- 同步失败可重试、可恢复、可增量。
- 所有用户数据按 `owner_id` 隔离。

### 2.5 控制范围

第一阶段优先保证“题库 + 刷题 + 同步”闭环稳定，不为未来功能做过度设计，但保留扩展点：

- 题目载荷使用 JSONB
- 题型使用可扩展枚举
- AI 只预留接口、任务表、用量表和功能开关
- 后端保持模块化单体，不提前拆微服务

---

## 3. 用户与角色

### 3.1 普通用户

普通用户既是“做题者”，也可以是“出题者”。默认注册角色为 `USER`，可以：

- 创建和管理自己的题库
- 创建、编辑、导入、导出题目
- 进行练习和模拟考试
- 查看错题、收藏、笔记
- 查看学习统计和复习计划
- 在多个设备之间同步数据

题库默认私有，用户之间的数据通过 `owner_id` 隔离。

### 3.2 管理员

管理员使用独立管理后台，负责：

- 查看系统运行状态
- 监控 API、同步、数据库、存储、任务队列
- 用户管理：查询、启用/禁用、重置密码、强制下线、查看设备
- 角色与权限管理
- 内容与题库统计
- 公告与功能开关
- 审计日志

管理员不通过普通用户客户端进入管理功能。

### 3.3 第一阶段角色模型

| 角色 | 说明 |
| --- | --- |
| `USER` | 普通注册用户，默认角色 |
| `ADMIN` | 管理员，可进入管理后台 |

权限采用 RBAC：

- 一个用户可以有多个角色
- 一个角色可以有多个权限
- 后端按权限码校验，不只判断角色名称
- 角色与权限变更必须写审计日志
- 必须保护最后一个管理员
- 初始管理员通过部署脚本、CLI 或环境变量创建，不能通过开放注册获得

第一阶段权限码：

```text
USER_BASIC
ADMIN_USER_READ
ADMIN_USER_WRITE
ADMIN_ROLE_READ
ADMIN_ROLE_WRITE
ADMIN_CONTENT_READ
ADMIN_CONTENT_WRITE
ADMIN_MONITOR_READ
ADMIN_AUDIT_READ
ADMIN_SYSTEM_CONFIG
ADMIN_ANNOUNCEMENT_WRITE
```

后续可增加内容审核员、运营管理员、客服、教师/学生、班级管理员等角色，均通过 RBAC 扩展。

---

## 4. 平台职责与功能对等矩阵

| 功能 | Web | 桌面端 | Android | 说明 |
| --- | --- | --- | --- | --- |
| 登录/注册 | 支持 | 支持 | 支持 | 统一账号 |
| 题库管理 | 支持 | 支持 | 支持 | 创建、重命名、删除、归档 |
| 题目创建 | 支持 | 支持 | 支持 | 四种客观题 |
| 题目编辑 | 支持 | 支持 | 支持 | 图片、公式、解析、标签 |
| 公式渲染 | 支持 | 支持 | 支持 | KaTeX / flutter_math / SVG 兜底 |
| 批量导入 | 支持 | 重点优化 | 小批量 | 大文件优先桌面/Web |
| 导出备份 | 支持 | 支持 | 支持 | JSON/CSV 等 |
| 练习 | 支持 | 支持 | 支持 | 顺序、随机、错题、复习 |
| 错题本 | 支持 | 支持 | 支持 | 自动收集 |
| 收藏/笔记 | 支持 | 支持 | 支持 | 多端同步 |
| 学习统计 | 支持 | 支持 | 支持 | 正确率、用时、趋势 |
| 多端同步 | 支持 | 支持 | 支持 | 核心能力 |
| 离线使用 | 部分支持 | 完整支持 | 完整支持 | Web 使用 IndexedDB/PWA |
| 图片采集 | 文件上传 | 文件拖拽 | 相机/相册 | 三端都能添加图片 |
| 通知提醒 | 可选 | 系统通知 | 本地通知/推送 | 复习提醒 |
| 管理后台 | 不提供 | 不提供 | 不提供 | 独立管理端 |

---

## 5. 核心功能

### 5.1 账号与设备

- 开放注册，默认 `USER`
- 邮箱验证、登录、刷新 Token、退出
- 修改密码、忘记密码、重置密码
- 设备列表、移除设备、退出所有设备
- 登录失败锁定、限流、防滥用
- 管理员 2FA（后续阶段）

### 5.2 题库管理

- 创建、重命名、编辑、归档、删除
- 题库描述使用 Markdown 子集
- 默认私有，预留 `PRIVATE / UNLISTED / PUBLIC`
- 乐观锁控制并发编辑
- 用户之间按 `owner_id` 隔离

### 5.3 题目创建与编辑

- 四种题型：单选、多选、判断、填空
- 题干、选项、解析支持 Markdown + LaTeX
- 题目和选项支持图片引用
- 题目版本历史与快照
- 发布、归档、复制
- 草稿状态与发布状态
- 搜索文本与标签/知识点

### 5.4 练习

- 顺序、随机、错题、收藏、到期复习、自定义、模拟考试
- 即时反馈或结束后统一反馈
- 跳过、标记、暂停/恢复
- 作答记录追加写入，不覆盖
- 客户端幂等 ID，避免重复作答
- 练习结果与历史

### 5.5 错题与复习

- 错题自动进入错题本
- 复习状态：到期时间、间隔、难度因子、连续正确、遗忘次数、掌握度
- 今日复习队列
- 提交复习评分并更新调度
- 复习统计

### 5.6 学习统计

- 总览：题量、正确率、练习时长、连续天数
- 趋势：按日/周
- 维度：题库、标签、知识点
- 管理员侧：注册用户、活跃用户、练习量、同步指标

### 5.7 导入与导出

- 导入任务：上传、解析、待复核、提交、失败、取消
- 导入明细逐条校验
- 导出任务与下载
- 导入导出必须保留 LaTeX 原文与媒体引用
- 大文件优先桌面端/Web

### 5.8 通知与设置

- 复习提醒
- 系统公告
- 用户本地设置：语言、时区、练习偏好、通知开关
- 管理后台功能开关，AI 默认关闭

---

## 6. 题库建设与题型

### 6.1 第一阶段题型

| 题型 | 标识 | 说明 |
| --- | --- | --- |
| 单选题 | `SINGLE_CHOICE` | 一个正确选项 |
| 多选题 | `MULTIPLE_CHOICE` | 多个正确选项，可全对或部分得分 |
| 判断题 | `TRUE_FALSE` | 正确/错误 |
| 填空题 | `FILL_BLANK` | 一个或多个空，支持文本答案 |

题型特有数据保存在 `payload` JSONB 中，新增题型不需要修改主表结构。

### 6.2 图片支持

- 题干、选项、解析均可引用图片
- 图片先上传对象存储，内容中使用 `media://<id>` 引用
- 服务端保存媒体元数据：哈希、MIME、大小、宽高、存储键
- 同一用户相同内容哈希去重
- 删除采用软删除，避免引用失效

### 6.3 公式支持

- 保存原始 LaTeX，不把渲染图片作为唯一内容
- Web/Electron 使用 KaTeX
- Android 使用 flutter_math_fork 或同类库
- 服务端保留 SVG 兜底渲染
- 定义受支持的 LaTeX 子集，并做三端快照测试
- 搜索索引需要处理 LaTeX 标记

### 6.4 题目状态

```text
DRAFT -> PUBLISHED -> ARCHIVED
```

- 草稿可自由编辑
- 发布后修改生成新版本
- 归档不删除历史作答引用
- 题目版本快照用于作答追溯

### 6.5 内容格式

- 文本：Markdown 子集
- 图片：`media://<id>`
- 公式：LaTeX
- 代码：Markdown fenced code block
- 服务端保存规范化内容并做安全过滤
---

## 7. 多端同步设计

### 7.1 同步目标

- 三端本地优先，离线可用
- 增量同步，不每次全量拉取
- 推送可重试、幂等
- 拉取可续传、可恢复
- 冲突有明确策略
- 媒体文件可后台上传与续传
- 服务端是权威数据源，客户端不依赖本地时间做冲突判断

### 7.2 客户端本地数据库

| 平台 | 本地存储 |
| --- | --- |
| Web | IndexedDB（Dexie） |
| 桌面端 | SQLite（better-sqlite3） |
| Android | SQLite（Drift） |

本地库保存：

- 业务实体缓存
- 同步游标
- outbox 待推送队列
- 媒体本地缓存
- 本地设置

### 7.3 同步模型

```text
客户端写本地库
  -> 写入 outbox
  -> 后台推送 /sync/push
  -> 服务端写入业务表 + sync_change
  -> 返回新的服务端序列号
  -> 客户端更新本地游标
  -> 拉取 /sync/pull（按游标）
```

关键字段：

- `change_seq`：服务端全局递增序列号，作为同步游标
- `entity_type`：BANK / QUESTION / QUESTION_TAG / KNOWLEDGE_POINT / MEDIA / PRACTICE_SESSION / ATTEMPT / NOTE / FAVORITE / REVIEW_STATE / SETTING
- `entity_id`：实体 ID
- `operation`：`UPSERT` / `DELETE`
- `entity_version`：实体版本
- `payload`：变更数据或墓碑
- `client_change_id`：客户端幂等 ID

### 7.4 同步 API

| 接口 | 说明 |
| --- | --- |
| `POST /sync/push` | 批量推送客户端变更 |
| `GET /sync/pull` | 按游标拉取变更 |
| `POST /sync/full` | 全量同步 |
| `GET /sync/status` | 同步状态与游标 |

### 7.5 冲突处理

- 服务端序列号优先，不使用客户端时间判断新旧
- 可编辑资源使用 `version` 乐观锁
- 冲突时返回服务端当前版本与冲突字段
- 客户端按实体策略处理：
  - 题库/题目：字段级合并或提示用户
  - 笔记：保留双方版本，提示选择
  - 作答：追加，不覆盖
  - 收藏/错题：幂等 upsert
  - 复习状态：服务端调度结果优先
- 删除使用墓碑，避免“删除后被旧客户端复活”

### 7.6 媒体同步

- 图片先落本地缓存
- 上传成功后获得 `media_id`
- 内容中的 `media://<id>` 在渲染时解析
- 上传中断可续传
- 同一用户相同 SHA256 去重

### 7.7 离线策略

- 离线可创建/编辑题库与题目
- 离线可练习与作答
- 离线数据进入 outbox
- 恢复网络后按顺序推送
- 同步失败指数退避，有上限
- 账号切换时清理本地数据，避免串号

### 7.8 必测同步场景

1. 新设备首次全量同步
2. 离线创建/编辑，恢复网络后推送
3. 两端同时编辑同一题，冲突按策略处理
4. 推送成功但响应丢失，客户端重试不重复写入
5. 游标过期后触发全量同步
6. 媒体上传中断后继续
7. 客户端时钟异常不影响服务端序列号
8. 同步过程中退出登录/切换账号，不串数据

---

## 8. 技术架构

### 8.1 总体架构

```text
┌─────────────────────┐
│  Web / Electron /   │
│  Flutter Android    │
└──────────┬──────────┘
           │ HTTPS / REST
           │ WebSocket（可选）
┌──────────▼──────────┐
│  Spring Boot API     │
│  认证 / 同步 / 题库   │
│  练习 / 复习 / 媒体   │
└──────────┬──────────┘
           │
     ┌─────┼──────────────┬─────────────┐
     │     │              │             │
 PostgreSQL Redis        RustFS       Prometheus
     │     │              │             │
     └─────┴──────────────┴─────────────┘
                       │
              ┌────────▼────────┐
              │  Admin Console   │
              │  Vue 3 + Element │
              └─────────────────┘
```

### 8.2 后端模块划分

第一版采用模块化单体，不拆微服务：

```text
auth          认证、令牌、设备、注册、密码重置
user          用户、个人资料、状态
role          角色、权限、RBAC
content       题库、题目、版本、标签、知识点
practice      练习会话、作答
review        错题、复习调度、掌握度
sync          增量同步、冲突处理
media         图片和附件
import        导入、解析、校验
admin         管理后台 API
notification  通知
analytics     统计查询
ai            AI 扩展接口（预留，默认关闭）
audit         审计日志
```

一个 Spring Boot 应用 + 一个 PostgreSQL + 一个 Redis + 一个 S3 兼容存储即可运行。

### 8.3 客户端架构

#### Web

```text
Vue 3 + TypeScript + Vite
Pinia + Vue Router
Element Plus
Dexie（IndexedDB）
OpenAPI 生成的 TypeScript 客户端
```

#### Desktop

```text
Electron 主进程
  + Vue 3 渲染进程
  + SQLite（better-sqlite3）
  + 本地文件系统
  + 自动更新
  + 安全 preload
```

Electron 安全要求：

- `contextIsolation: true`
- `nodeIntegration: false`
- `sandbox: true`
- preload 只暴露白名单 API
- CSP
- 代码签名与自动更新校验

#### Android

```text
Flutter + Dart
Riverpod 状态管理
go_router 路由
Dio 网络请求
Drift + SQLite
Workmanager 后台任务
flutter_secure_storage
image_picker
本地通知
```

### 8.4 管理后台

- Vue 3 + TypeScript + Element Plus
- 独立入口、独立认证
- 不与普通用户客户端混合
- 提供用户、角色、内容、监控、审计、公告、功能开关

---

## 9. 技术选型

| 层 | 技术 | 版本/说明 |
| --- | --- | --- |
| Web | Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus | 当前 |
| 桌面端 | Electron + Vue 3 + TypeScript + better-sqlite3 | Electron 45 |
| Android | Flutter + Dart + Riverpod + Drift | Flutter 3.50 / Dart 3.14 |
| 后端 | Java 21 + Spring Boot + Spring Security + Spring Data JPA + Flyway | Spring Boot 3.5.16 |
| 数据库 | PostgreSQL | 17.12 |
| 缓存 | Redis | 8.10 |
| 对象存储 | S3 兼容：RustFS（本机/Docker） | RustFS 1.0.6 |
| 公式 | KaTeX + flutter_math_fork + 服务端 SVG 兜底 | 第一阶段 |
| 管理后台 | Vue 3 + Element Plus | 当前 |
| 构建 | Maven + pnpm + Flutter pub + Gradle | 当前 |
| 契约 | OpenAPI 3.1 + Redocly + openapi-typescript | 当前 |
| 迁移 | Flyway | V1/V2 已落地 |
| 监控 | Actuator + Micrometer + Prometheus + Loki + Grafana | 当前 |
| 网关 | Caddy | 2.11.9 |
| CI | GitHub Actions | 当前 |

### 9.1 选型说明

- **Vue 3**：生态成熟、TypeScript 支持好，可复用到 Electron 和管理后台。
- **Spring Boot**：企业生态完整，适合认证、后台任务、监控和管理 API。
- **Electron**：桌面生态成熟，可复用 Vue 渲染层；代价是包体和内存较大。
- **Flutter**：一套代码覆盖 Android，后续可扩展 iOS；离线、通知、相机插件成熟。
- **PostgreSQL**：事务、JSONB、索引、全文搜索能力强，适合多用户平台。
- **RustFS**：MinIO 上游归档后，作为 Windows/Docker 可用的 S3 兼容替代。
- **模块化单体**：降低第一阶段部署与开发复杂度，保留后续拆分空间。
- **AI 只预留**：不进入答题、判分、同步关键路径，默认关闭。

---

## 10. 数据模型概览

### 10.1 设计原则

- 主键统一 UUID（审计日志等追加表使用 bigserial）
- 时间统一 `timestamptz`
- 软删除使用 `deleted_at`
- 可编辑资源使用 `version` 乐观锁
- 所有用户数据带 `owner_id`
- 内容与学习状态分离
- 枚举使用 varchar + CHECK，便于扩展
- 题目特有字段放 JSONB

### 10.2 核心表分组

#### 账号与权限

```text
app_user
app_user_profile
app_role
app_permission
app_role_permission
app_user_role
app_device
app_user_session
app_email_verification
app_password_reset
app_user_setting
app_audit_log
app_announcement
app_feature_flag
```

#### 题库内容

```text
qb_bank
qb_question
qb_question_version
qb_tag
qb_question_tag
qb_knowledge_point
qb_question_knowledge_point
qb_media
```

#### 练习与复习

```text
practice_session
practice_session_item
practice_attempt
practice_note
practice_favorite
review_state
```

#### 同步

```text
sync_change
sync_idempotency
```

#### 导入与 AI

```text
import_batch
import_item
ai_job
ai_usage
ai_provider_config
```

### 10.3 关键约束

- `app_user.email` / `username` 软删除后唯一
- 题库名称在同一用户下唯一（忽略大小写）
- 笔记/收藏在同一用户+题目下唯一（未删除）
- 作答 `(owner_id, client_attempt_id)` 唯一，保证幂等
- 复习状态 `(owner_id, question_id)` 唯一
- 同步幂等 `(owner_id, client_change_id)` 唯一
- 管理员操作写审计日志
- 保护最后一个管理员

### 10.4 详细规格

完整字段、索引、DDL 与迁移说明见：

- `services/api/docs/DATABASE-DESIGN.md`
- `services/api/src/main/resources/db/migration/V1__init_schema.sql`
- `services/api/src/main/resources/db/migration/V2__seed_roles_and_permissions.sql`
---

## 11. API 概览

### 11.1 设计原则

- REST + JSON，统一 `/api/v1`
- 认证使用 JWT Access Token + Refresh Token
- 多设备会话，可撤销
- 所有用户数据按 `owner_id` 隔离
- 可编辑资源使用乐观锁 `version`
- 追加型资源（作答、同步变更）使用客户端幂等 ID
- 统一错误响应与业务错误码
- 分页使用游标或页码，避免大偏移
- 管理后台接口独立认证与权限

### 11.2 端点分组

| 分组 | 主要内容 |
| --- | --- |
| Auth | 注册、邮箱验证、登录、刷新、退出、密码重置、设备 |
| User | 当前用户、资料、设置、统计 |
| Admin | 管理员登录、用户、角色、权限、审计、公告、开关 |
| Banks | 题库 CRUD、列表、详情 |
| Questions | 题目 CRUD、版本、复制、发布、归档、搜索 |
| Tags / Knowledge | 标签、知识点树 |
| Media | 图片上传、元数据、删除、引用 |
| Imports | 导入任务、明细、修正、提交、导出 |
| Practice | 练习会话、作答、跳过、暂停、结束、结果、历史 |
| Review | 今日复习、提交评分、复习统计 |
| Notes / Favorites | 笔记、收藏 |
| Sync | push、pull、full、status |
| Admin Monitor | 系统概览、同步/导入监控、健康、公告、开关 |
| AI | 预留任务与用量接口，默认关闭 |

### 11.3 统一响应与错误

成功响应直接返回数据对象或分页对象；错误响应包含：

```json
{
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "邮箱或密码错误",
  "traceId": "...",
  "timestamp": "2026-11-20T10:00:00Z",
  "details": {}
}
```

错误码按领域分组：通用、账号、权限、题库、练习/同步、功能开关。

### 11.4 契约文件

- 机器可读契约：`packages/contracts/openapi.yaml`
- 完整接口说明：`packages/contracts/API-CONTRACT.md`
- 客户端类型：`packages/sdk/generated/`

当前 `openapi.yaml` 仍只包含 `/health`，详细端点需在 Sprint 1-5 中逐步补齐。

---

## 12. 管理后台与监控

### 12.1 管理后台定位

- 面向开发者和运营人员
- 独立域名或独立路径
- 独立认证与权限
- 普通用户客户端不暴露管理入口

### 12.2 第一阶段管理功能

- 管理员登录与权限
- 系统概览
- 用户查询、详情、启用/禁用、重置密码、强制下线
- 角色列表、创建/编辑、权限分配
- 用户角色分配
- 设备管理
- 题库与题目统计
- 同步状态
- 导入任务状态
- 系统日志与审计日志
- 公告
- 功能开关（AI 默认关闭）

### 12.3 监控指标

- 注册用户数、活跃用户数
- 日/周练习量
- API 请求量、错误率、P95/P99
- 同步成功/失败率、冲突数、平均耗时
- 数据库连接数、慢查询
- Redis 状态
- 对象存储用量
- 导入任务队列长度、失败率
- 磁盘空间
- JVM 内存、GC、线程数
- 服务健康状态

### 12.4 监控技术

- Spring Boot Actuator
- Micrometer
- Prometheus
- Grafana
- Loki
- Sentry（可选）
- 告警：邮件、Webhook、企业微信/钉钉/飞书

---

## 13. 安全与隐私

### 13.1 账号安全

- 密码使用 Argon2id 或 BCrypt
- 登录失败锁定与限流
- Refresh Token 只保存哈希
- 支持退出所有设备与移除设备
- 管理员 2FA（后续）
- 初始管理员必须修改默认密码

### 13.2 数据隔离

- 所有业务查询必须带 `owner_id`
- 不接受客户端传入的归属人
- 管理后台查询使用独立权限
- 审计日志记录管理员操作

### 13.3 内容安全

- Markdown/HTML 安全过滤
- 图片 MIME、大小、尺寸校验
- LaTeX 子集限制
- 导入内容逐条校验
- 版权与合规风险由内容审核策略控制

### 13.4 隐私

- 最小化收集个人信息
- 日志不输出密码、Token、完整题干
- 用户可导出和删除自己的数据
- 生产数据不用于开发测试

---

## 14. 非功能需求

### 14.1 性能目标

- 常规 API P95 < 300ms（本地/测试环境基准）
- 同步 push/pull 单批最多 500 条
- 题库列表与题目列表必须分页
- 图片上传支持压缩与大小限制
- 常用查询必须有索引

### 14.2 可用性

- 后端优雅停机
- 健康检查与就绪检查
- 数据库连接池监控
- 关键依赖故障时返回明确错误
- 同步失败可重试

### 14.3 数据备份

- PostgreSQL 每日全量 + WAL 归档（生产）
- 对象存储版本化
- 每月恢复演练
- 导出功能作为用户侧备份

### 14.4 可观测性

- JSON 结构化日志
- traceId 贯穿请求
- API、同步、导入、数据库指标
- Grafana 仪表盘与告警

### 14.5 可测试性

- 单元测试
- Testcontainers 集成测试
- 契约测试
- 同步场景测试
- Flutter widget 测试
- E2E 测试（后续）

### 14.6 可访问性与国际化

- 键盘操作与焦点管理
- 图片 alt 文本
- 中文优先，预留多语言
- 时区统一 Asia/Shanghai，服务端存 UTC

---

## 15. 开发路线

| 阶段 | 目标 |
| --- | --- |
| 阶段 0 | 工程基础、工具链、CI、契约、数据库基线 |
| 阶段 1 | 最小跨端闭环（Walking Skeleton） |
| 阶段 2 | 题库与四种题型 |
| 阶段 3 | 练习与复习 |
| 阶段 4 | 管理后台 |
| 阶段 5 | 体验与稳定性 |
| 阶段 6 | 第二阶段题型（主观题/编程题） |
| 阶段 7 | 综合学习平台 |

当前已完成阶段 0，进入阶段 1。详细迭代计划见 `docs/development-plan.md`。

---

## 16. 主要风险与对策

| 风险 | 对策 |
| --- | --- |
| 三端功能对等成本高 | 共享契约、共享 UI 组件、垂直切片、明确对等清单 |
| 同步复杂度高 | 本地优先、服务端序列号、幂等、固定测试场景 |
| Electron 体积与安全 | 最小 preload、CSP、代码签名、按需加载 |
| Flutter 富文本编辑 | 先使用 Markdown 子集，复杂编辑后置 |
| 中文全文搜索 | pg_trgm + 搜索文本字段，后续评估专用分词 |
| 范围膨胀 | 第一阶段范围锁定，AI 只预留 |
| 版权与内容合规 | 导入来源记录、审核与举报机制 |
| 开放注册滥用 | 限流、验证码、邮箱验证、审计 |
| 三端公式渲染不一致 | LaTeX 子集 + 快照测试 + SVG 兜底 |
| AI 预留导致范围膨胀 | 默认关闭、接口隔离、不进入关键路径 |
| 权限设计错误 | RBAC、权限码、审计、最后一个管理员保护 |

---

## 17. 关键决策摘要

| 编号 | 决策 |
| --- | --- |
| ADR-001 | 三个用户客户端功能对等 |
| ADR-002 | 后端拆分为业务服务端和管理后台 |
| ADR-003 | 第一版使用 Spring Boot 模块化单体 |
| ADR-004 | 本地优先 + 增量同步 |
| ADR-005 | Markdown 子集作为内容格式 |
| ADR-006 | PostgreSQL + JSONB |
| ADR-007 | 第二阶段独立 Judge 服务 |
| ADR-008 | Electron 复用 Vue 3 |
| ADR-009 | Flutter Android 优先 |
| ADR-010 | 开放注册 + RBAC |
| ADR-011 | 第一阶段支持 LaTeX |
| ADR-012 | AI 只预留接口 |

后续架构决策按 `docs/development-plan.md` 的 ADR 流程记录。

---

## 18. 文档与代码索引

### 18.1 三份主文档

- [项目整体介绍](project-overview.md)
- [开发计划书](development-plan.md)
- [项目运维和部署文档](operations-deployment.md)

### 18.2 参考规格

- API 契约说明：`packages/contracts/API-CONTRACT.md`
- OpenAPI：`packages/contracts/openapi.yaml`
- 数据库设计：`services/api/docs/DATABASE-DESIGN.md`
- 迁移脚本：`services/api/src/main/resources/db/migration/`

### 18.3 代码位置

```text
apps/web          Web
apps/admin        管理后台
apps/desktop      Electron
apps/android      Flutter Android
services/api      Spring Boot
packages/ui       共享 Vue 组件
packages/sdk      生成的客户端
infra/docker      Compose
infra/monitoring  Prometheus 配置
```

---

## 19. 待确认问题

1. 题库是否需要公开分享？如果暂时不需要，所有题库默认私有。
2. 图片来源是否主要是相册、截图、文件上传，还是还需要网页剪藏？
3. 是否需要 Web 端完整离线，还是只做本地缓存和有限离线？
4. 题库导入是否需要兼容其他刷题软件格式？
5. 是否需要后续支持 iOS？
6. 数据备份是自建服务器、对象存储，还是只做本地导出？
7. 第一阶段邮箱验证是否强制？
8. 注册是否需要邀请码或强制验证码？
9. 管理后台是否使用独立域名？
10. AI 后续第一个场景优先做解析、出题，还是 OCR/导入？

这些问题不阻塞当前开发，但会影响后续迭代范围与部署方式。
