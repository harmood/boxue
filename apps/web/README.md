# 博学 Web

Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus。

## 开发

在仓库根目录：

```powershell
pnpm dev:web
```

默认端口：`5173`
开发代理：`/api` → `http://localhost:8080`

## 当前页面

- `/login` 登录
- `/register` 注册
- `/verify-email` 邮箱验证
- `/forgot-password` 忘记密码
- `/reset-password` 重置密码
- `/dashboard` 学习概览
- `/banks` 我的题库
- `/practice` 练习占位页

## 认证行为

- Access Token 存在 `sessionStorage`
- Refresh Token 由后端写入 HttpOnly Cookie
- 401 时自动尝试刷新 Access Token 并重试一次
- 路由守卫在进入受保护页面前恢复会话

## 下一步

- 题库 CRUD
- 题目编辑器
- 公式与图片渲染
- 练习引擎
