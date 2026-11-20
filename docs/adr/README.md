# 架构决策记录（ADR）

本目录保存会影响长期维护的架构决策。

## 命名

```text
docs/adr/0001-use-postgresql-jsonb.md
docs/adr/0002-sync-cursor-strategy.md
```

## 模板

```markdown
# NNNN. 决策标题

- 状态：提议 / 接受 / 废弃 / 被 NNNN 取代
- 日期：YYYY-MM-DD
- 决策人：

## 背景

为什么现在必须做这个决定？有哪些约束？

## 决策

我们决定……

## 备选方案

1. 方案 A：优点 / 缺点
2. 方案 B：优点 / 缺点
3. 方案 C：优点 / 缺点

## 影响

- 正面：
- 负面：
- 迁移成本：
- 后续需要做什么：

## 参考

- 相关 Issue / PR / 文档
```

## 规则

- 一个 ADR 只记录一个决策。
- 已接受的 ADR 不修改结论；结论变化时新增 ADR 并标注“取代”。
- 代码、契约、部署方式的重大变化必须关联 ADR。
- `docs/project-charter.md` 第 19 节的既有决策可逐步迁移到此目录。
