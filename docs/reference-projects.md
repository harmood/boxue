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
