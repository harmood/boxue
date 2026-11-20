# 博学桌面端

Electron + Vue 3 + TypeScript。

## 开发

在仓库根目录：

```powershell
pnpm dev:desktop
```

## 构建

```powershell
pnpm build:desktop
```

## 安全配置

- `contextIsolation: true`
- `nodeIntegration: false`
- `sandbox: true`
- 通过 preload 暴露最小 API

## 下一步

- SQLite 本地数据库
- 文件导入
- 离线题库
- 同步队列
- 自动更新与代码签名

## 注意

当前网络下载 Electron 二进制可能失败。构建和类型检查可设置：

```powershell
$env:ELECTRON_SKIP_BINARY_DOWNLOAD = "1"
```

运行桌面端开发模式仍需要完整 Electron 二进制。