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
│  ├─ docker/       # PostgreSQL / Redis / MinIO / Mailpit
│  └─ monitoring/   # Prometheus 配置
├─ scripts/         # 本地开发脚本
├─ docs/            # 项目文档
└─ .github/         # CI
```

## 当前技术栈

- Web：Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus
- 管理后台：Vue 3 + TypeScript + Vite + Element Plus
- 桌面端：Electron + Vue 3 + TypeScript
- Android：Flutter + Dart + Riverpod + Drift
- 后端：Java 21 + Spring Boot + Spring Security + Spring Data JPA + Flyway
- 数据库：PostgreSQL
- 缓存/队列：Redis
- 对象存储：MinIO
- 契约：OpenAPI

## 本地环境要求

- Node.js 22+
- pnpm 10+
- Java 21 LTS
- Maven 3.9+
- Flutter 3.x
- Docker Desktop

> 项目工具链已放在 `C:\Users\ksl-54-it-n04\Project\devenv\toolchains`。执行 `. ..\devenv\scripts\env.ps1` 激活后即可使用 Java、Maven、Flutter、Android SDK、Node 和 pnpm。

## 快速开始

### 0. 激活本地工具链

```powershell
. ..\devenv\scripts\env.ps1
```

### 1. 启动基础设施

```powershell
.\scripts\db-up.ps1
```

服务：

- PostgreSQL：`localhost:5432`
- Redis：`localhost:6379`
- MinIO：`localhost:9000`，控制台 `localhost:9001`
- Mailpit：`localhost:8025`，SMTP `localhost:1025`

### 2. 安装前端依赖

```powershell
pnpm install
```

### 3. 启动 Web

```powershell
pnpm dev:web
```

### 4. 启动管理后台

```powershell
pnpm dev:admin
```

### 5. 启动桌面端

```powershell
pnpm dev:desktop
```

### 6. 启动后端

```powershell
cd services\api
mvn spring-boot:run
```

### 7. 启动 Android

```powershell
cd apps\android
flutter pub get
flutter run
```

首次使用 Flutter 时，如果缺少 Android 平台目录，执行：

```powershell
flutter create . --platforms=android
```

## 文档

- [项目主文档](docs/project-charter.md)
- [数据库设计](docs/database-design.md)
- [API 契约](docs/api-contract.md)
- [本地开发环境](docs/environment-setup.md)
- [开发流程](docs/development-workflow.md)
- [架构决策记录](docs/adr/README.md)
- [文档索引](docs/README.md)

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