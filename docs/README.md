# 博学（Boxue）文档索引

> 更新日期：2026-11-20
> 文档集：三份主文档 + 两份参考规格

---

## 1. 三份主文档

| 文档 | 定位 | 主要读者 | 何时更新 |
| --- | --- | --- | --- |
| [项目整体介绍](project-overview.md) | 产品定位、范围、角色、功能、架构、技术选型、数据/API 总览、路线图、风险 | 所有人 | 范围、架构、技术选型、数据/API 总览变化 |
| [开发计划书](development-plan.md) | 开发流程、分支规范、DoR/DoD、测试、CI、评审、Sprint 计划、当前进度 | 开发、测试 | 流程、迭代、质量门禁变化 |
| [项目运维和部署文档](operations-deployment.md) | 环境、工具链、Docker、数据库、监控、发布、回滚、故障排查 | 开发、运维 | 环境、部署、运维、故障处理变化 |

---

## 2. 参考规格

这两份是随代码演进的详细规格，不属于三份主文档，但与主文档互相引用：

| 文档 | 位置 | 说明 |
| --- | --- | --- |
| API 契约说明 | `packages/contracts/API-CONTRACT.md` | 端点、字段、错误码、同步协议 |
| OpenAPI | `packages/contracts/openapi.yaml` | 机器可读契约，客户端类型由此生成 |
| 数据库设计 | `services/api/docs/DATABASE-DESIGN.md` | 表、字段、索引、约束、DDL |
| Flyway 迁移 | `services/api/src/main/resources/db/migration/` | 实际数据库结构 |

---

## 3. 快速导航

### 我想了解项目

- 项目是做什么的 → [项目整体介绍](project-overview.md)
- 现在做到哪一步 → [开发计划书](development-plan.md) 第 2 节
- 技术栈和架构 → [项目整体介绍](project-overview.md) 第 8-9 节
- 数据表和接口 → [数据库设计](../services/api/docs/DATABASE-DESIGN.md) / [API 契约](../packages/contracts/API-CONTRACT.md)

### 我要开始开发

- 环境怎么装 → [项目运维和部署文档](operations-deployment.md) 第 2-4 节
- 怎么启动 → [项目运维和部署文档](operations-deployment.md) 第 4 节
- 开发流程 → [开发计划书](development-plan.md) 第 8 节
- 什么算完成 → [开发计划书](development-plan.md) 第 15 节

### 我要部署/排障

- Docker 怎么装 → [项目运维和部署文档](operations-deployment.md) 第 7 节
- 数据库/迁移 → [项目运维和部署文档](operations-deployment.md) 第 6 节
- 监控和日志 → [项目运维和部署文档](operations-deployment.md) 第 8 节
- 故障处理 → [项目运维和部署文档](operations-deployment.md) 第 11 节

---

## 4. 当前状态

- Sprint 0 已完成
- 三端骨架、后端、契约、Flyway、CI、Docker 已建立
- 当前进入 Sprint 1：认证、RBAC 与管理员引导
- 详细待办见 [开发计划书](development-plan.md) 第 22 节；缺口清单见第 24 节

---

## 5. 文档维护规则

1. 三份主文档是唯一入口，新增文档前先判断能否并入现有章节。
2. API/数据库详细规格跟随代码目录，不重复复制到主文档。
3. 代码、契约、迁移、部署方式变化时，同一 PR 更新对应文档。
4. 架构决策使用 ADR，模板见 [开发计划书](development-plan.md) 第 17.2 节。
5. 历史文档已合并到三份主文档，Git 历史中仍可追溯。
