# 数据库迁移

本目录使用 Flyway 管理 PostgreSQL 结构变更。

## 当前迁移

```text
V1__init_schema.sql               # 全部表、索引、updated_at 触发器
V2__seed_roles_and_permissions.sql # USER/ADMIN 角色、权限码、AI 功能开关
```

## 规则

- 只向前迁移，已应用的脚本不得修改。
- 每次结构调整新增 `V<序号>__<snake_case>.sql`。
- 生产环境禁止 `flyway clean`。
- 迁移脚本必须通过空库和上一版本快照库验证。
- 初始管理员不在迁移中创建，通过 CLI / 环境变量一次性创建。

`spring.flyway.enabled` 已开启，`local` profile 启动时自动迁移。
