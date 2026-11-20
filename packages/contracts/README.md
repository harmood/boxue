# OpenAPI 契约

`openapi.yaml` 是机器可读契约骨架。

完整接口设计见：

- `docs/api-contract.md`
- `docs/database-design.md`

后续应按 API 文档逐步补齐：

- Auth
- Admin Users / Roles
- Banks / Questions
- Media
- Imports / Exports
- Practice / Review
- Sync
- AI 预留

生成客户端建议：

- TypeScript：`openapi-typescript` 或 `orval`
- Dart：`openapi-generator`
- Java：`springdoc-openapi` 反向生成/校验