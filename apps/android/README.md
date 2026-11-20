# 博学 Android

Flutter 3.50 Android 客户端骨架。

## 运行

```powershell
cd apps\android
flutter pub get
flutter run
```

构建 debug APK：

```powershell
flutter build apk --debug
```

## 当前配置

- Flutter 3.50 / Dart 3.14
- compileSdk 37，启用 core library desugaring
- Riverpod 3
- go_router 18
- Dio
- Drift 2.35 + SQLite
- flutter_secure_storage
- image_picker
- flutter_local_notifications 23
- Workmanager 0.10
- flutter_markdown
- flutter_math_fork

## 已知事项

- `sqlite3` 原生库默认从 GitHub Releases 下载；网络受限时参考
  `docs/operations-deployment.md` 的 SQLite 原生库下载说明。
- `workmanager_android` 目前仍会给出 Kotlin Gradle Plugin 非致命警告。

## 下一步

1. 实现登录与令牌安全存储
2. 实现本地数据库与 schema 迁移
3. 实现题库列表和练习页
4. 接入同步队列与离线作答
