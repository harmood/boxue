# 数据库迁移

本目录用于 Flyway 迁移脚本。

建议：

```text
V1__init_schema.sql
V2__seed_roles_and_permissions.sql
V3__seed_admin_bootstrap.sql
```

完整表结构见 `docs/database-design.md`。

在迁移脚本完成前，`application.yml` 中保持：

```yaml
spring:
  flyway:
    enabled: false
```

迁移完成并验证后改为 `true`。