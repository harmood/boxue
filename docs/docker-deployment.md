# Docker 部署与运维记录

> 日期：2026-11-20
> 状态：已部署并验证
> 适用环境：Windows 11 + WSL2 + Docker Desktop

---

## 1. 部署结果

| 组件 | 版本 / 状态 |
| --- | --- |
| Docker Desktop | 4.99.0.245822 |
| Docker CLI | 29.9.3 |
| Docker Compose | v5.7.0 |
| WSL | 3.0.5 |
| VirtualMachinePlatform | 已启用 |
| Docker 引擎 | linux/amd64，运行中 |

当前 Compose 栈：

| 容器 | 镜像 | 端口 | 状态 |
| --- | --- | --- | --- |
| `boxue-postgres` | `postgres:17-alpine` | 5433 → 5432 | healthy |
| `boxue-redis` | `redis:8-alpine` | 6379 → 6379 | healthy |
| `boxue-object-storage` | `rustfs/rustfs:latest` | 9000 / 9001 | healthy |
| `boxue-mailpit` | `axllent/mailpit:latest` | 1025 / 8025 | healthy |

已验证：

- PostgreSQL 接受连接
- Redis 返回 `PONG`
- RustFS `/minio/health/live` 返回 `200`
- Mailpit 返回 `200`
- 后端对 Docker PostgreSQL 自动执行 Flyway `V1` / `V2`
- `/actuator/health` 返回 `200`

---

## 2. 端口与运行方式

原生中间件与 Docker 使用相同的宿主机端口，二者只能同时运行一套。

| 服务 | 宿主机端口 |
| --- | --- |
| PostgreSQL | 5433 |
| Redis | 6379 |
| RustFS S3 / 控制台 | 9000 / 9001 |
| Mailpit SMTP / Web | 1025 / 8025 |

### 从原生切换到 Docker

```powershell
C:\Users\ksl-54-it-n04\Project\devenv\scripts\stop.ps1 -Services postgres,redis,rustfs
C:\Users\ksl-54-it-n04\Project\devenv\scripts\docker-up.ps1
```

### 从 Docker 切换到原生

```powershell
docker compose -f C:\Users\ksl-54-it-n04\Project\workspace\infra\docker\compose.yml down
C:\Users\ksl-54-it-n04\Project\devenv\scripts\start.ps1 -Services postgres,redis,rustfs
```

---

## 3. 从零安装步骤

### 3.1 下载 Docker Desktop

安装包保存在：

```text
C:\Users\ksl-54-it-n04\Project\devenv\downloads\tools\DockerDesktopInstaller.exe
```

官方地址：

```text
https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe
```

网络受限时可通过代理或 aria2 多线程下载。

### 3.2 安装 WSL2（管理员）

```powershell
wsl --install --no-distribution
```

该命令会启用：

- `Microsoft-Windows-Subsystem-Linux`
- `VirtualMachinePlatform`

安装完成后必须重启系统，虚拟化才会生效。

### 3.3 安装 Docker Desktop（管理员）

```powershell
& "C:\Users\ksl-54-it-n04\Project\devenv\downloads\tools\DockerDesktopInstaller.exe" `
  install --quiet --accept-license --backend=wsl-2
```

### 3.4 验证 Docker

```powershell
docker version
docker compose version
docker info
```

应看到：

```text
Server: Docker Desktop
OSType: linux
```

### 3.5 配置镜像加速

配置文件：

```text
C:\Users\ksl-54-it-n04\.docker\daemon.json
```

内容示例：

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

### 3.6 启动基础设施

```powershell
C:\Users\ksl-54-it-n04\Project\devenv\scripts\docker-up.ps1
```

---

## 4. Docker Desktop 本机配置

配置文件：

```text
C:\Users\ksl-54-it-n04\AppData\Roaming\Docker\settings-store.json
```

关键配置：

```json
{
  "AutoStart": true,
  "ProxyHTTPMode": "none"
}
```

说明：

- `AutoStart=true`：登录 Windows 后自动启动 Docker Desktop。
- `ProxyHTTPMode=none`：Docker 镜像拉取走上文的 registry mirrors，不依赖系统代理。

---

## 5. 遇到的问题与解决记录

### 5.1 重启后系统代理未运行

**现象**

系统代理设置为 `127.0.0.1:12450`，但重启后端口无人监听，Flutter/GitHub 下载再次变慢。

**原因**

代理客户端没有随系统启动。

**处理**

- `devenv\scripts\env.ps1` 自动探测 `127.0.0.1:12450`：
  - 端口可用时设置 `HTTP_PROXY` / `HTTPS_PROXY` / `NO_PROXY`
  - 端口不可用时保持直连
- Docker 镜像拉取改用国内 registry mirrors，不依赖系统代理。

### 5.2 WSL2 安装后 Docker 引擎报 500

**现象**

```text
ERROR: request returned 500 Internal Server Error .../dockerDesktopLinuxEngine/v1.56/info
```

**原因**

`VirtualMachinePlatform` 已启用，但系统未重启，WSL2 虚拟化尚未生效。

**处理**

重启 Windows 后 Docker 引擎正常启动。

### 5.3 Docker 拉取镜像时没有代理

**现象**

```text
dialing registry-1.docker.io:443 container via direct connection
because Docker Desktop has no HTTPS proxy
```

**原因**

Docker Desktop 没有读取到可用的系统代理。

**处理**

配置 Docker Desktop manual proxy 后，又发现 Linux VM 内的 `127.0.0.1:12450`
指向 VM 自身，无法访问宿主机代理。

**最终方案**

不再让 Docker 走宿主机代理，改用 registry mirrors：

```text
docker.1panel.live
docker.m.daocloud.io
docker.1ms.run
```

### 5.4 Docker Hub 直连超时

**现象**

```text
Get "https://registry-1.docker.io/v2/": context deadline exceeded
```

**原因**

Docker Hub 在当前网络下不可稳定直连。

**处理**

- 配置 registry mirrors
- 对个别镜像使用镜像前缀显式拉取，再重新打标签

### 5.5 MinIO 镜像不可用

**现象**

```text
Error response from daemon: denied
```

**原因**

MinIO 上游已归档，当前网络下镜像无法正常获取。

**处理**

Compose 从 `minio/minio` 切换为 `rustfs/rustfs`：

- 与本机原生环境一致
- 同样提供 S3 兼容 API
- 端口仍为 9000 / 9001
- 数据卷改为 `boxue-s3-data`

### 5.6 Redis 镜像通过镜像前缀拉取

**现象**

`docker compose` 期望 `redis:8-alpine`，但实际拉取到：

```text
docker.1panel.live/library/redis:8-alpine
```

**处理**

```powershell
docker tag docker.1panel.live/library/redis:8-alpine redis:8-alpine
```

后续 Docker 镜像加速生效后，普通 `docker pull redis:8-alpine` 可正常使用。

### 5.7 SQLite 原生库下载失败

**现象**

```text
By default, this package downloads a pre-compiled SQLite library.
This failed ... libsqlite3.x64.android.so
```

**原因**

`sqlite3` 的 build hook 从 GitHub Releases 下载 Android 原生库，直连不稳定。

**处理**

- `env.ps1` 自动识别本机代理
- 删除手工缓存的 x64 原生库后重新构建
- Flutter 自动下载并校验 SHA256 成功
- APK 构建通过

### 5.8 Workmanager Kotlin 警告

**现象**

```text
Your app uses the following plugins that apply Kotlin Gradle Plugin (KGP): workmanager_android
```

**状态**

非致命警告，不影响 APK 构建。等待插件升级支持 Built-in Kotlin。

---

## 6. 日常命令

```powershell
$compose = "C:\Users\ksl-54-it-n04\Project\workspace\infra\docker\compose.yml"

docker compose -f $compose ps
docker compose -f $compose logs -f
docker compose -f $compose up -d
docker compose -f $compose down
docker compose -f $compose restart
```

重置数据卷（会删除本地数据库/对象存储数据）：

```powershell
docker compose -f $compose down -v
```

> 该命令具有破坏性，只在确认无需保留数据时使用。

---

## 7. 验证清单

```powershell
docker version
docker compose version
docker compose -f $compose ps
docker compose -f $compose exec -T postgres pg_isready -U boxue -d boxue
docker compose -f $compose exec -T redis redis-cli ping
curl.exe http://127.0.0.1:9000/minio/health/live
curl.exe http://127.0.0.1:8025
```

Flyway 验证：

```powershell
docker compose -f $compose exec -T postgres `
  psql -U boxue -d boxue `
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

---

## 8. 机器级文件清单

这些文件位于仓库之外，不进入 Git：

| 文件 | 作用 |
| --- | --- |
| `devenv\scripts\env.ps1` | 设置工具链、代理自动探测、Docker PATH |
| `devenv\scripts\docker-up.ps1` | 启动 Docker Desktop 与 Compose |
| `devenv\downloads\tools\DockerDesktopInstaller.exe` | Docker Desktop 安装包 |
| `%USERPROFILE%\.docker\daemon.json` | registry mirrors |
| `%APPDATA%\Docker\settings-store.json` | Docker Desktop 自启动与代理模式 |

仓库内文件：

| 文件 | 作用 |
| --- | --- |
| `infra\docker\compose.yml` | PostgreSQL / Redis / RustFS / Mailpit |
| `.env.example` | 数据库与 S3 默认参数 |
| `docs\environment-setup.md` | 环境说明 |
| `docs\sprint-0-report.md` | Sprint 0 验证记录 |
| `docs\docker-deployment.md` | 本文档 |

---

## 9. 已知限制

1. 国内 registry mirrors 是第三方服务，长期可用性不受本项目控制；失效时需要替换。
2. Docker 容器端口当前绑定 `0.0.0.0`，局域网内可访问；如需仅本机访问，可改为 `127.0.0.1:<port>:<port>`。
3. 原生中间件不会开机自启；Docker Desktop 已设置 `AutoStart=true`。
4. 系统代理客户端不会自动启动；`env.ps1` 只在代理端口已监听时启用代理。
5. `workmanager_android` 的 Kotlin 插件警告仍然存在。
