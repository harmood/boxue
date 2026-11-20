# 博学业务服务端

Java 21 + Spring Boot 模块化单体骨架。

## 启动

```powershell
cd services\api
mvn spring-boot:run
```

默认端口：`8080`

## 当前接口

- `GET /api/v1/health`
- `GET /actuator/health`
- `GET /v3/api-docs`
- `GET /swagger-ui.html`
- `POST /api/v1/auth/register`（占位）
- `POST /api/v1/auth/login`（占位）
- `GET /api/v1/sync/status`（占位）
- `GET /api/v1/admin/ping`（需要 ADMIN 角色）

## 模块

```text
auth       认证、注册、令牌
user       用户与资料
role       角色与权限
content    题库、题目、标签、知识点
practice   练习与作答
review     错题与复习
sync       增量同步
media      图片与媒体
imports    导入导出
admin      管理后台 API
ai         AI 预留
common     通用配置、错误处理、安全
```

## 说明

- 当前 `spring.flyway.enabled=false`，数据库迁移脚本写入后改为 `true`。
- 当前安全配置是骨架，JWT 过滤器、RBAC 权限校验和注册登录逻辑待实现。
- 本地开发默认管理员：`admin / admin`，仅用于骨架验证，生产环境必须修改。
- 数据库设计见 `docs/database-design.md`。
- API 契约见 `docs/api-contract.md`。