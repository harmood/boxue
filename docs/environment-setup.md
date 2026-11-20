# 本地开发环境

## 工具链位置

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
└─ caches\
   ├─ maven-repo
   ├─ gradle-user-home
   ├─ pub-cache
   ├─ npm-cache
   └─ pnpm-store
```

## 激活环境

在项目根目录：

```powershell
. ..\devenv\scripts\env.ps1
```

或在任意目录：

```powershell
. C:\Users\ksl-54-it-n04\Project\devenv\scripts\env.ps1
```

激活后会设置：

- `JAVA_HOME`
- `MAVEN_HOME`
- `FLUTTER_HOME`
- `ANDROID_HOME`
- `ANDROID_SDK_ROOT`
- `GRADLE_USER_HOME`
- `PUB_CACHE`
- `NPM_CONFIG_CACHE`
- `MAVEN_OPTS`

## 验证

```powershell
java -version
mvn -version
flutter --version
node --version
pnpm --version
```

## 当前验证结果

- Web 构建：通过
- 管理后台构建：通过
- Electron 构建：通过
- Spring Boot 测试：通过
- Flutter 测试：通过
- Flutter analyze：通过，存在 1 条 `package:flutter/material.dart` 的 Flutter 3.50 弃用提示

## 已知问题

### Electron 二进制下载

当前网络下载 Electron 二进制时可能出现 `ECONNRESET`。

如果只需要类型检查和构建：

```powershell
$env:ELECTRON_SKIP_BINARY_DOWNLOAD = "1"
pnpm install --filter @boxue/desktop --no-frozen-lockfile
pnpm --filter @boxue/desktop build
```

如果需要运行 Electron 开发模式，需要重新下载 Electron 二进制，或配置代理/镜像。

### Docker 未安装

当前环境未检测到 Docker。`infra/docker/compose.yml` 已准备好，安装 Docker Desktop 后可以启动 PostgreSQL、Redis、MinIO 和 Mailpit。

### Flutter material 弃用提示

Flutter 3.50 对 `package:flutter/material.dart` 给出弃用提示，当前不影响编译和测试。后续可根据 Flutter 版本迁移到新的 material 包。

## 环境与 Git

- 工具链和缓存都在 `devenv` 下
- `devenv` 在 Git 仓库之外
- 只有 `workspace` 内容会提交