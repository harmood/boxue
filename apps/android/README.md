# 博学 Android

Flutter Android 客户端骨架。

## 生成 Android 平台目录

当前目录只包含 Dart 骨架。安装 Flutter 后执行：

```powershell
cd apps\android
flutter create . --platforms=android
flutter pub get
flutter run
```

## 当前内容

- Riverpod
- go_router
- Dio
- Drift / SQLite
- flutter_secure_storage
- image_picker
- flutter_local_notifications
- Workmanager
- flutter_markdown
- flutter_math_fork

## 下一步

1. 生成 Android 平台目录
2. 实现登录与令牌安全存储
3. 实现本地数据库
4. 实现题库列表和练习页
5. 接入同步队列与离线作答