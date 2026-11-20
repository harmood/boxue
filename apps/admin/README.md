# 博学管理后台

Vue 3 + TypeScript + Vite + Element Plus。

## 开发

在仓库根目录：

```powershell
pnpm dev:admin
```

默认端口：`5174`
开发代理：`/api` → `http://localhost:8080`

## 当前页面

- `/login` 管理员登录
- `/dashboard` 系统概览
- `/users` 用户管理
- `/roles` 角色权限
- `/audit-logs` 审计日志

## 已接入能力

- 管理员独立登录与 Token Audience
- Access Token + HttpOnly Refresh Token
- 401 自动刷新并重试
- 路由守卫恢复会话
- 用户查询、详情、状态修改、角色分配
- 触发密码重置、强制下线
- 角色创建、编辑、删除、权限分配
- 权限列表、审计日志游标分页

## 参考项目

- eladmin：搜索 + 表格 + Drawer + Dialog 的后台交互
- ruoyi-vue-pro：RBAC、用户角色分配、审计日志
- vben-admin：后台布局与表格密度

## 下一步

- 系统概览接入真实监控数据
- 同步/导入监控页面
- 公告与功能开关页面
- 管理员 2FA
