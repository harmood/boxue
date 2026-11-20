# 博学（Boxue）文档索引

| 文档 | 说明 |
| --- | --- |
| [project-charter.md](project-charter.md) | 项目主文档：目标、平台职责、功能范围、技术架构、路线图 |
| [database-design.md](database-design.md) | PostgreSQL 数据库设计：表结构、索引、约束、DDL |
| [api-contract.md](api-contract.md) | REST API 契约：认证、用户、题库、练习、同步、管理后台、AI 预留 |
| [environment-setup.md](environment-setup.md) | 本地工具链、环境激活、验证结果与已知问题 |
| [development-workflow.md](development-workflow.md) | 开发流程：分支、契约、迁移、测试、CI、评审、发布与迭代 |
| [adr/README.md](adr/README.md) | 架构决策记录规范与模板 |
| [sprint-0-report.md](sprint-0-report.md) | Sprint 0 执行记录、验证结果与已知问题 |
| [docker-deployment.md](docker-deployment.md) | Docker Desktop/WSL2 安装、镜像加速、问题处理与验证 |
| [product-analysis.md](product-analysis.md) | 早期产品分析稿，已被项目主文档取代 |

## 当前阶段

- 刷题与题库建设
- 开放注册与 RBAC
- Web / Electron / Flutter Android 三端同步
- 单选、多选、判断、填空
- 图片与 LaTeX 公式
- AI 只预留接口，不启用

## 当前骨架

- Vue 3 Web：已建立
- Vue 3 管理后台：已建立
- Electron 桌面端：已建立
- Flutter Android：已建立并生成 Android 平台目录
- Spring Boot 后端：已建立
- OpenAPI 骨架：已建立
- Docker Compose 基础设施：已建立
- CI：已建立

## 下一步

1. 实现注册、登录、刷新、RBAC 与管理员引导（Sprint 1）
2. 补齐 OpenAPI Auth / User / Admin 端点
3. 实现题库 CRUD 与四种题型
4. 实现 Web → Android → 桌面端的第一条同步垂直切片
5. 完善同步冲突与离线场景测试

Sprint 0 已完成，详见 [sprint-0-report.md](sprint-0-report.md)。