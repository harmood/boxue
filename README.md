# 博学（Boxue）

个人多端学习平台。当前阶段聚焦题库、刷题、错题复习与多端同步，后续扩展到课程、主观题、编程题和 AI 能力。

## 仓库结构

```text
boxue/
├─ apps/
│  ├─ web/          # Vue 3 用户 Web 端
│  ├─ admin/        # Vue 3 管理后台
│  ├─ desktop/      # Electron + Vue 3 桌面端
│  └─ android/      # Flutter Android 客户端
├─ services/
│  └─ api/          # Java 21 + Spring Boot 业务服务端
├─ packages/
│  ├─ contracts/    # OpenAPI 契约
│  ├─ sdk/          # 生成的 TypeScript / Dart 客户端
│  └─ ui/           # 共享 Vue 组件与设计令牌
├─ infra/
│  ├─ docker/       # PostgreSQL / Redis / S3 / Mailpit
│  └─ monitoring/   # Prometheus 配置
├─ scripts/         # 本地开发脚本
├─ docs/            # 项目文档
└─ .github/         # CI
```

## 技术栈

- Web / Admin：Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus
- 桌面端：Electron + Vue 3 + TypeScript + better-sqlite3
- Android：Flutter + Dart + Riverpod + Drift
- 后端：Java 21 + Spring Boot 3.5 + Spring Security + Spring Data JPA + Flyway
- 数据库：PostgreSQL 17
- 缓存：Redis 8
- 对象存储：S3 兼容（RustFS）
- 契约：OpenAPI 3.1 + Redocly + openapi-typescript
- 监控：Actuator + Micrometer + Prometheus + Loki + Grafana

## 工具链

所有工具链与中间件位于 `C:\Users\ksl-54-it-n04\Project\devenv`，不在 Git 仓库内。

| 组件 | 版本 |
| --- | --- |
| JDK | Temurin 21.0.13+9 |
| Maven | 3.9.16 |
| Node.js | 26.13.1 LTS |
| pnpm | 12.21.0 |
| Flutter | 3.50.0 |
| Android SDK | platform 37.0 / build-tools 37.0.0 |
| PostgreSQL | 17.12（端口 **5433**） |
| Redis | 8.10.3（端口 6379） |
| RustFS | 1.0.6（S3 9000 / 控制台 9001） |

## 快速开始

### 0. 激活环境

```powershell
. C:\Users\ksl-54-it-n04\Project\devenv\scripts\env.ps1
```

### 1. 启动本机中间件（推荐）

```powershell
C:\Users\ksl-54-it-n04\Project\devenv\scripts\start.ps1
C:\Users\ksl-54-it-n04\Project\devenv\scripts\status.ps1
```

| 服务 | 地址 |
| --- | --- |
| PostgreSQL | `127.0.0.1:5433`，库 `boxue`，用户 `boxue` |
| Redis | `127.0.0.1:6379` |
| S3 API | `http://127.0.0.1:9000` |
| S3 控制台 | `http://127.0.0.1:9001` |
| Prometheus | `http://127.0.0.1:9090` |
| Loki | `http://127.0.0.1:3100` |
| Grafana | `http://127.0.0.1:3000` |

### 2. 安装前端依赖

```powershell
pnpm install
```

### 3. 启动应用

```powershell
pnpm dev:web
pnpm dev:admin
pnpm dev:desktop
```

后端：

```powershell
cd services\api
mvn spring-boot:run
```

首次初始化管理员（仅一次）：

```powershell
.\scripts\bootstrap-admin.ps1 -Email admin@boxue.local -Username admin
```

Android：

```powershell
cd apps\android
flutter pub get
flutter run
```

### 4. Docker（可选）

安装 Docker Desktop 后：

```powershell
.\scripts\db-up.ps1
```

Compose 同样把 PostgreSQL 映射到宿主机 **5433**，S3 使用 9000/9001，避免与原生环境端口冲突。

## 质量检查

```powershell
pnpm check                 # typecheck + OpenAPI lint
pnpm build                 # web + admin + desktop
mvn -f services/api/pom.xml verify
cd apps\android; flutter analyze; flutter test
```

契约与 SDK：

```powershell
pnpm contract:lint
pnpm sdk:gen
```

## 文档

三份主文档：

- [项目整体介绍](docs/project-overview.md)
- [开发计划书](docs/development-plan.md)
- [项目运维和部署文档](docs/operations-deployment.md)

参考规格：

- [API 契约](packages/contracts/API-CONTRACT.md)
- [数据库设计](services/api/docs/DATABASE-DESIGN.md)
- [文档索引](docs/README.md)
- [GitHub 参考项目调研](docs/reference-projects.md)

## 当前阶段

- 开放注册与 RBAC
- 题库与四种客观题
- 图片与 LaTeX 公式
- 练习、错题、笔记、复习
- Web / Electron / Flutter Android 多端同步
- 管理后台与监控
- AI 仅预留接口

## 开发原则

1. 用户数据按 `owner_id` 隔离。
2. 题目内容与学习状态分离。
3. 作答记录追加写入。
4. 可编辑资源使用乐观锁。
5. 多端同步可增量、可重试、可恢复。
6. AI 不进入答题、判分和同步关键路径。
