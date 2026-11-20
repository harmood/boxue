# GitHub 参考项目调研

> 调研日期：2026-11-20  
> 用途：为博学的题库、刷题、复习、多端同步、管理后台和认证体系提供架构参考  
> 原则：只借鉴模式，不直接复制代码；所有项目请在使用前自行核对许可证

---

## 1. 结论先行

博学不需要从零发明所有轮子，但也不适合直接套用某个现成项目。最值得借鉴的组合是：

| 领域 | 首选参考 | 借鉴点 |
| --- | --- | --- |
| 题库/题型/作答 | Moodle、TAO | 题库分类、题型、作答记录、版本与评分 |
| 复习调度 | Anki、FSRS | 卡片/笔记分离、复习日志、间隔重复算法 |
| 离线学习 | Kolibri | 离线内容包、设备同步、低网络环境 |
| 多端本地优先 | Joplin、Actual Budget | 本地数据库、同步队列、冲突处理、资源同步 |
| Flutter 本地库 | Drift、AppFlowy | SQLite schema 迁移、本地优先架构 |
| 管理后台/RBAC | eladmin、ruoyi-vue-pro | 用户/角色/权限/审计/后台页面结构 |
| 契约与客户端生成 | OpenAPI Generator、Orval | 一份契约生成多端 SDK |
| 桌面端 | electron-vite-vue、Joplin | Electron + Vue 工程结构、自动更新 |

**不建议照搬**：Moodle/Open edX 这类大型 PHP/Python 单体、Anki 的卡片中心模型、Joplin 的笔记模型、CRDT 全家桶。博学第一阶段更适合“服务端权威变更日志 + 客户端 outbox”的同步模型。

---

## 2. 学习、题库与考试类项目

### 2.1 Anki

- 仓库：https://github.com/ankitects/anki
- 技术：Rust + Python + Svelte，桌面端为主，配套 AnkiWeb 同步
- 适合参考：
  - Note（内容）与 Card（学习单元）分离
  - 复习日志与调度状态
  - 多设备同步与冲突处理
  - 导入/导出与媒体资源管理
- 借鉴到博学：
  - `qb_question` 与 `review_state` 已经体现内容/学习状态分离
  - 复习调度可以参考其“调度状态独立于题目内容”的设计
  - 同步变更日志可参考其“变更 + 墓碑 + 游标”思路
- 注意：
  - Anki 是单用户、卡片中心模型，不是多用户题库平台
  - 不建议直接复制其数据模型

### 2.2 AnkiDroid

- 仓库：https://github.com/ankidroid/Anki-Android
- 技术：Kotlin/Java Android
- 适合参考：
  - Android 本地数据库与同步
  - 离线复习、后台同步、冲突提示
  - 大题库在移动端的性能处理
- 借鉴到博学：
  - Android 端“先本地、后同步”的交互
  - 同步失败提示与重试策略

### 2.3 FSRS（Free Spaced Repetition Scheduler）

- 仓库：
  - https://github.com/open-spaced-repetition/fsrs4anki
  - https://github.com/open-spaced-repetition/fsrs-rs
- 技术：Python / Rust
- 适合参考：
  - 现代间隔重复算法
  - 难度、稳定性、 retrievability 等参数
  - 复习日志到调度参数的训练
- 借鉴到博学：
  - 第一阶段可以先实现简化版 SM-2/FSRS 风格调度
  - `review_state` 已包含 `ease_factor`、`interval_days`、`repetitions`、`lapses`、`mastery`
  - 后续可将调度算法独立成 `review` 模块策略
- 注意：
  - 不要在第一阶段引入模型训练依赖
  - 先用固定参数，后续再离线训练

### 2.4 Kolibri

- 仓库：https://github.com/learningequality/kolibri
- 技术：Python + Vue，离线优先学习平台
- 适合参考：
  - 离线内容包
  - 设备间同步
  - 低带宽环境下的资源分发
  - 学习进度与内容分离
- 借鉴到博学：
  - 题库/题目作为可同步内容包
  - 媒体资源按需下载与缓存
  - 设备同步状态与冲突提示

### 2.5 Moodle

- 仓库：https://github.com/moodle/moodle
- 技术：PHP + PostgreSQL/MySQL
- 适合参考：
  - Question Bank（题库分类、题型、版本）
  - Quiz（练习会话、作答、评分）
  - 角色与权限（Context/Role/Capability）
  - 导入导出格式
- 借鉴到博学：
  - 题库分类与标签
  - 题型定义与评分策略
  - 作答记录与成绩
- 注意：
  - Moodle 体量巨大，不适合直接复用
  - 权限模型比博学第一阶段复杂很多

### 2.6 TAO Assessment Platform

- 仓库：https://github.com/oat-sa/tao-core
- 技术：PHP
- 适合参考：
  - 评估/考试交付
  - 题库与试卷组装
  - 作答与评分流程
- 借鉴到博学：
  - 练习会话与题目项的结构
  - 考试模式与普通练习模式的差异

### 2.7 Open edX / Oppia

- Open edX：https://github.com/openedx/openedx-platform
- Oppia：https://github.com/oppia/oppia
- 适合参考：
  - 课程/章节/课时结构
  - 互动式学习内容
  - 学习进度与状态
- 借鉴到博学：
  - 长期扩展到课程时的内容层级
  - 第一阶段的题库不需要照搬课程模型

### 2.8 Safe Exam Browser

- 仓库：https://github.com/SafeExamBrowser/SafeExamBrowser
- 技术：C# / Windows
- 适合参考：
  - 考试客户端锁定
  - 防切换、防复制、防多屏
- 借鉴到博学：
  - 第二阶段模拟考试/正式考试时再考虑
  - 第一阶段不做考试锁定

---

## 3. 多端本地优先与同步项目

### 3.1 Joplin

- 仓库：https://github.com/laurent22/joplin
- 技术：Electron + React Native + TypeScript
- 适合参考：
  - 本地优先数据模型
  - 多同步目标（WebDAV、S3、Nextcloud 等）
  - 同步冲突提示
  - 资源/附件同步
  - 端到端加密
- 借鉴到博学：
  - 客户端 outbox + 服务端变更日志
  - 媒体资源先本地、后上传
  - 冲突时保留双方版本并提示用户
- 注意：
  - Joplin 以笔记为中心，博学以题目/作答为中心

### 3.2 Actual Budget

- 仓库：https://github.com/actualbudget/actual
- 技术：React + Node，local-first
- 适合参考：
  - 本地数据库 + 同步服务器
  - 多设备冲突处理
  - 离线编辑
- 借鉴到博学：
  - 同步服务器与客户端职责边界
  - 变更日志与幂等

### 3.3 Logseq

- 仓库：https://github.com/logseq/logseq
- 技术：Electron + ClojureScript
- 适合参考：
  - 本地文件/数据库
  - 双向链接与知识图谱（长期）
  - 多端同步
- 借鉴到博学：
  - 本地优先的桌面端体验
  - 知识点/标签的关系建模

### 3.4 AppFlowy

- 仓库：https://github.com/AppFlowy-IO/AppFlowy
- 技术：Flutter + Rust
- 适合参考：
  - Flutter 本地优先架构
  - 本地数据库与同步
  - 插件化/模块化
- 借鉴到博学：
  - Android 端 Drift + outbox
  - Flutter 与原生能力边界

### 3.5 Automerge

- 仓库：https://github.com/automerge/automerge
- 技术：Rust/JS CRDT
- 适合参考：
  - 无冲突合并
  - 离线协作
- 借鉴到博学：
  - 不建议第一阶段直接引入 CRDT
  - 可作为“笔记/多端同时编辑”后续方案

### 3.6 ElectricSQL / PowerSync / RxDB / Zero / LiveStore

- ElectricSQL：https://github.com/electric-sql/electric
- PowerSync：https://github.com/powersync-ja/powersync-service
- RxDB：https://github.com/pubkey/rxdb
- Zero：https://github.com/rocicorp/zero
- LiveStore：https://github.com/livestorejs/livestore
- 适合参考：
  - Postgres 到客户端的增量同步
  - 本地查询与离线写入
  - 上传队列与冲突策略
- 借鉴到博学：
  - 服务端权威序列号 + 客户端游标
  - 不建议第一阶段引入整套同步框架，避免绑定

### 3.7 Drift / Riverpod

- Drift：https://github.com/simolus3/drift
- Riverpod：https://github.com/rrousselGit/riverpod
- 适合参考：
  - Flutter SQLite schema 迁移
  - 状态管理
- 博学已采用，继续保持

### 3.8 electron-vite-vue

- 仓库：https://github.com/electron-vite/electron-vite-vue
- 技术：Electron + Vue 3 + Vite
- 适合参考：
  - 主进程/渲染进程/preload 结构
  - 构建与打包配置
- 博学已采用类似结构

---

## 4. 后端、管理后台与 RBAC

### 4.1 eladmin

- 仓库：https://github.com/elunez/eladmin
- 技术：Spring Boot + Vue + JPA + Spring Security
- 适合参考：
  - 用户/角色/权限/菜单
  - 后台管理页面
  - 审计日志
  - 代码生成
- 借鉴到博学：
  - RBAC 表结构与后台交互
  - 管理后台布局
- 注意：
  - eladmin 是通用 CRUD 脚手架，不包含题库/学习领域

### 4.2 ruoyi-vue-pro

- 仓库：https://github.com/YunaiV/ruoyi-vue-pro
- 技术：Spring Boot + Vue + MyBatis
- 适合参考：
  - RBAC、数据权限、审计
  - 多租户（后续）
  - 管理后台模块划分
- 借鉴到博学：
  - 权限码与角色分配
  - 管理员操作审计
  - 但博学第一阶段不做多租户

### 4.3 mall

- 仓库：https://github.com/macrozheng/mall
- 技术：Spring Boot + Vue
- 适合参考：
  - 后端分层
  - 管理后台
  - 权限与审计
- 借鉴到博学：
  - 模块化单体结构
  - 但不要照搬电商领域模型

### 4.4 vue-vben-admin / vue-element-admin

- vben：https://github.com/vbenjs/vue-vben-admin
- vue-element-admin：https://github.com/PanJiaChen/vue-element-admin
- 适合参考：
  - 管理后台布局、权限路由、表格/表单
- 借鉴到博学：
  - Admin Console 的页面组织
  - 注意 vue-element-admin 基于 Vue 2，博学使用 Vue 3

### 4.5 OpenAPI 客户端生成

- OpenAPI Generator：https://github.com/OpenAPITools/openapi-generator
- Orval：https://github.com/orval-labs/orval
- hey-api：https://github.com/hey-api/openapi-ts
- 适合参考：
  - 一份契约生成 TypeScript/Dart/Java 客户端
  - 契约变更检测
- 博学当前使用 openapi-typescript，后续可评估 Orval 生成请求客户端

---

## 5. 对博学的具体建议

### 5.1 建议采用

- Anki / FSRS 的“内容与复习状态分离 + 复习日志”
- Moodle 的“题库分类 + 题型 + 作答记录”
- Kolibri 的“离线内容包 + 设备同步”
- Joplin / Actual 的“本地优先 + 同步队列 + 冲突提示”
- eladmin / ruoyi 的“RBAC + 审计 + 管理后台”
- Drift / Riverpod 作为 Flutter 本地与状态基础
- OpenAPI 生成客户端，避免三端手写接口

### 5.2 不建议第一阶段采用

- 直接套用 Moodle/Open edX 的课程模型
- Anki 的纯卡片模型
- Joplin 的笔记同步模型
- CRDT（Automerge/Zero/LiveStore）全套引入
- 多租户、微服务、消息队列
- 考试锁定/防作弊（第二阶段再做）
- 复杂的 AI 功能

### 5.3 推荐阅读顺序

1. Anki：理解内容/复习状态分离
2. FSRS：理解复习调度
3. Moodle Question Bank：理解题型与作答
4. Kolibri：理解离线学习与设备同步
5. Joplin / Actual：理解本地优先同步
6. eladmin / ruoyi：理解 RBAC 与管理后台
7. Drift / Riverpod：Flutter 本地实现
8. OpenAPI Generator：多端 SDK

---

## 6. 许可证提醒

以上项目许可证各不相同，常见包括 MIT、Apache-2.0、AGPL-3.0、GPL。  
博学可以参考架构和交互，但在复制任何代码、样式、数据结构或文档前，必须逐一核对许可证与署名要求。  
建议默认只借鉴“设计模式”，不直接复制源码。

---

## 7. Admin Console 实现参考（2026-11-20）

本次用户/角色管理页面主要参考以下开源后台的交互模式：

| 参考项目 | 借鉴点 | 博学落地 |
| --- | --- | --- |
| eladmin | 搜索表单 + 表格 + 详情抽屉 + 编辑弹窗；角色权限勾选 | `UsersView` 搜索/表格/详情 Drawer；`RolesView` 权限分组勾选 |
| ruoyi-vue-pro | RBAC 用户角色分配、状态变更、审计日志 | 用户状态/角色替换、审计日志页面 |
| vben-admin | 后台布局、表格密度、操作列与分页习惯 | AdminLayout、表格与分页交互 |
| vue-element-admin | 早期后台交互范式 | 仅参考布局和表格模式，不复制 Vue 2 代码 |

### 7.1 页面与模式映射

- 用户管理：
  - 顶部筛选：关键词、状态、角色
  - 表格：邮箱、用户名、状态、角色、最后登录
  - 详情：Drawer + Descriptions + 设备表
  - 操作：状态下拉、角色 Dialog、密码重置、强制下线
- 角色管理：
  - 表格：角色码、名称、系统角色、权限数量
  - 编辑：基础信息 + 按前缀分组的权限勾选
  - 系统角色禁止删除
- 审计日志：
  - 操作人、动作、目标类型筛选
  - 游标分页
  - 详情 JSON 展示

### 7.2 没有照搬的部分

- 没有引入 eladmin/ruoyi 的代码生成器
- 没有引入多租户、数据权限、菜单管理等超出第一阶段的能力
- 没有复制其 Vue 2 或复杂权限树实现
- 保持博学自己的 OpenAPI、游标分页和 RBAC 契约

## 8. 题库 CRUD 实现参考（2026-11-20）

题库 CRUD 的 API、Web 页面与 Admin 只读页面参考了以下开源项目：

| 参考项目 | 借鉴点 | 博学落地 |
| --- | --- | --- |
| Moodle Question Bank | 题库作为题目容器；列表中展示题目数量；删除前提示内容非空 | `qb_bank` + `questionCount`；`BANK_NOT_EMPTY` + `cascade=true` 二次确认 |
| Anki | 牌组（Deck）列表展示卡片数量与最近更新时间；归档而非物理删除 | 题库列表展示题目数、更新时间；`ACTIVE/ARCHIVED` 状态与软删除 |
| eladmin | 搜索表单 + 表格 + 新建/编辑 Dialog + 删除确认 | Web `BankListView`；Admin `BanksView` 只读列表 + 详情 Drawer |
| ruoyi-vue-pro | 后台列表按所有者、状态筛选；操作留审计 | Admin 支持 `ownerId/status/visibility` 筛选；写操作记录 `BANK_CREATED/BANK_UPDATED/BANK_DELETED` |

### 8.1 关键取舍

- 采用 Moodle 的“题库是题目容器”模型，但不引入其复杂分类树与课程上下文。
- 采用 Anki 的“归档优先、删除需确认”思路，但删除仍保留同步墓碑，便于多端收敛。
- 采用 eladmin 的 Dialog/Drawer 交互，但保持博学自己的 OpenAPI 契约、游标分页和乐观锁。
- 未采用 ruoyi 的代码生成器与多租户数据权限，避免第一阶段过度设计。

### 8.2 与开源项目的差异

- 博学题库名称在同一用户下忽略大小写唯一，开源项目多数允许重名。
- 博学更新使用 `version` 乐观锁，冲突返回 `409`，不静默覆盖。
- 删除题库默认拒绝非空题库，需显式 `cascade=true`，避免误删题目。

## 9. 题目 CRUD 与四种题型实现参考（2026-11-20）

题目 CRUD 与四种题型的 payload 校验、编辑器和版本快照参考了以下开源项目：

| 参考项目 | 借鉴点 | 博学落地 |
| --- | --- | --- |
| Moodle Question types | 每种题型独立的编辑表单与校验；题目草稿/发布状态；版本历史 | 四种题型独立 payload 校验；`DRAFT/PUBLISHED/ARCHIVED`；`qb_question_version` 快照 |
| TAO Assessment Platform | 题目与题库解耦；题型数据结构化存储；发布前校验 | `qb_question.payload` JSONB；创建/更新/发布/归档统一校验 |
| Open edX / Oppia | 富文本题干、解析与可扩展题型 | `MARKDOWN_LATEX` 内容格式；题型枚举预留扩展 |
| eladmin | 表格 + 弹窗/抽屉的编辑交互 | Web 题目列表 + 动态题型编辑器；Admin 只读列表 + payload Drawer |
| Anki | 卡片内容与模板分离 | 题干/解析与题型 payload 分离存储，便于后续模板化渲染 |

### 9.1 关键取舍

- 采用 Moodle 的“每题独立题型校验”思路，但 payload 统一存入 `qb_question.payload`，避免为每种题型建表。
- 采用 TAO 的“发布前完整校验”，但草稿创建时也做结构校验，避免脏数据进入同步链路。
- 采用版本快照而不是事件溯源，读取简单，满足历史追溯与多端冲突排查。
- 未引入 Moodle 的题库分类树、TAO 的复杂评分模型、Open edX 的课程上下文。

### 9.2 与开源项目的差异

- 博学使用乐观锁 `version`，更新冲突返回 `409 QUESTION_VERSION_CONFLICT`。
- 题目类型创建后不可修改，避免 payload 语义漂移。
- 所有写操作写入 `qb_question_version` 与 `sync_change`，为后续同步与历史作答追溯做准备。
- 图片上传与 LaTeX 实际渲染尚未实现，当前仅存储 `media://` 与公式文本。

## 10. 媒体上传与 Markdown/LaTeX 渲染参考（2026-11-20）

| 参考项目 | 借鉴点 | 博学落地 |
| --- | --- | --- |
| Moodle File API | 附件归属校验、内容中引用媒体、删除前检查引用 | `qb_media` + `qb_question_media`；`media://` 引用；`MEDIA_IN_USE` |
| Joplin Attachments | 附件按哈希去重、软删除、对象存储 key 稳定 | `owner_id + sha256` 去重；S3 key `{ownerId}/{sha256}.{ext}` |
| eladmin 文件上传 | 上传按钮、缩略图预览、失败提示 | Web 题目编辑器 `el-upload` + 缩略图 + 自动插入 Markdown |
| KaTeX | 浏览器端 LaTeX 渲染、错误容错 | `MarkdownContent.vue` 使用 KaTeX，`throwOnError: false` |
| markdown-it | 安全 Markdown 渲染（默认禁用原始 HTML） | `html: false`，避免题目内容注入脚本 |

### 10.1 关键取舍

- 采用内容寻址去重，但不做跨用户共享，避免越权读取。
- 图片内容接口需要 Bearer Token，Web 通过 fetch 获取 Blob URL；不暴露公开直链。
- 先保证上传、引用、渲染闭环，图片压缩、EXIF 剥离、SVG 兜底留到后续。
- 未采用 Moodle 的完整文件 API 与 Joplin 的本地附件同步模型。

### 10.2 与开源项目的差异

- 媒体删除采用软删除元数据 + 删除对象文件；重复上传已软删除的图片会恢复记录。
- 题目与媒体是多对多关联，便于同一图片在多个题目中复用。
- 媒体类型按文件头识别，不信任客户端 Content-Type。

## 11. 同步第一链路实现参考（2026-11-20）

BANK 同步的 push/pull/status、幂等和冲突处理参考：

| 参考项目 | 借鉴点 | 博学落地 |
| --- | --- | --- |
| Joplin | 增量同步、客户端变更 ID、冲突不静默覆盖 | `sync_change` 序列、`clientChangeId` 幂等、rejected 冲突返回 |
| Actual Budget | 本地优先、变更日志、按游标拉取 | `cursor` + `nextCursor` 升序拉取，客户端保存游标 |
| ElectricSQL / PowerSync | 服务端变更流、离线写入队列 | push 批量提交、单条失败不阻塞整批 |
| RxDB | 乐观锁与冲突响应 | BANK 更新/删除使用 `baseVersion`，冲突返回 `currentVersion` 与 `currentPayload` |
| Logseq | 设备维度同步状态 | V6 设备 `last_sync_pull_at` / `last_sync_push_at` |

### 11.1 关键取舍

- 先做 BANK 单实体闭环，验证推送、拉取、幂等、冲突和游标，再扩展 QUESTION 等实体。
- 使用服务端自增 `change_seq` 作为游标，简单可靠；不采用客户端时间戳排序。
- 冲突以 rejected 返回，不阻塞同批其他变更，客户端拉取最新快照后人工合并。
- 当前不做 CRDT 合并，BANK 使用乐观锁而非字段级合并。

### 11.2 与开源项目的差异

- 博学的 `sync_change.payload` 对 BANK 保存完整快照，便于新设备补齐。
- `sync_idempotency` 以 `owner_id + clientChangeId` 唯一，重试返回同一 serverSeq。
- 当前只支持 BANK；全量同步、90 天清理和其他实体仍待扩展。

## 12. 图片压缩与 SVG 处理参考（2026-11-20）

| 参考项目 | 借鉴点 | 博学落地 |
| --- | --- | --- |
| Thumbnailator | 按最大边缩放、保持比例、JPEG 质量控制 | `ImageProcessor` 统一压缩 PNG/JPEG/WebP |
| TwelveMonkeys ImageIO | 补足 Java ImageIO 的 WebP 读取能力 | 读取 WebP 后按透明通道输出 PNG 或 JPEG |
| Apache Batik | SVG 文档渲染与 PNG 转码 | `SvgRasterizer` 将清理后的 SVG 渲染为 PNG |
| OWASP File Upload / SVG 安全实践 | 不保存不可信 SVG、禁止脚本与外部资源 | 删除 script/foreignObject/style，过滤事件属性和外部 href |

### 12.1 关键取舍

- 不把原始 SVG 直接存入对象存储，统一栅格化为 PNG，避免 SVG 脚本与外部资源风险。
- 重新编码图片以剥离 EXIF；这也会移除 GPS 等隐私信息。
- GIF 不做重编码，避免丢失动画。
- 压缩参数可配置，后续可增加 WebP 输出和更细的质量策略。

### 12.2 与开源项目的差异

- 博学只做上传入口的统一处理，不做完整图片编辑器或 CDN 转码服务。
- SVG 白名单仍以安全为先，复杂 SVG 效果可能被简化。

## 13. 刷新令牌轮换与多标签页并发参考（2026-11-20）

| 参考项目/规范 | 借鉴点 | 博学落地 |
| --- | --- | --- |
| IETF OAuth 2.0 Security BCP | Refresh Token 轮换、重放检测、旧令牌失效 | 旧会话记录替换关系，默认 30 秒宽限重放 |
| Auth0 / Keycloak | Refresh Token Rotation 与并发客户端处理 | 宽限期内旧令牌只换 Access Token，不重复轮换 |
| MDN Web Locks API | 浏览器跨标签页互斥锁 | Web/Admin 刷新使用 Web Locks 串行化 |
| localStorage lock fallback | 不支持 Web Locks 的浏览器降级 | 带过期时间和随机持有者 ID 的本地锁 |
| PostgreSQL row lock | 用数据库行锁串行化并发刷新 | `SELECT ... FOR UPDATE` 锁定 refresh token 会话行 |

### 13.1 关键取舍

- 采用短宽限期而不是永久接受旧 Refresh Token，兼顾并发体验与安全性。
- 重放请求不返回新的 Refresh Token，避免覆盖其他标签页刚写入的新 Cookie。
- 会话行锁保证同一 Refresh Token 同时只有一个事务执行轮换。
- 超过宽限期、替换会话已撤销或账号禁用时仍然拒绝刷新。

### 13.2 与开源项目的差异

- 博学当前未实现 Refresh Token 重用检测告警（reuse detection alert）。
- Web Locks 不可用时退化为 localStorage 锁，跨浏览器锁精度弱于服务端行锁。
