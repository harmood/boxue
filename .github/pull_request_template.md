## 变更说明

<!-- 这个 PR 解决了什么问题？用户价值是什么？ -->

## 关联 Issue

Closes #

## 变更类型

- [ ] feat：新功能
- [ ] fix：缺陷修复
- [ ] refactor：重构（不改变行为）
- [ ] perf：性能优化
- [ ] test：测试
- [ ] docs：文档
- [ ] chore / build / ci：工程维护

## 影响面

- [ ] Contract（`packages/contracts/openapi.yaml`）
- [ ] API（`services/api`）
- [ ] 数据库迁移（`db/migration`）
- [ ] Web（`apps/web`）
- [ ] Admin（`apps/admin`）
- [ ] Desktop（`apps/desktop`）
- [ ] Android（`apps/android`）
- [ ] 同步（push / pull / cursor / conflict）
- [ ] 基础设施 / CI

## 契约与数据

- [ ] 无契约变更
- [ ] 契约已更新并通过 `pnpm contract:lint`
- [ ] SDK 已重新生成
- [ ] 无数据库变更
- [ ] 迁移已新增，且未修改历史迁移
- [ ] 迁移已在空库验证
- [ ] 迁移已在上一版本快照库验证
- [ ] 变更向前兼容（expand/contract）

## 三端对等

- [ ] Web
- [ ] Admin
- [ ] Desktop
- [ ] Android
- [ ] 同步
- [ ] 未涉及的端已说明原因 / 已建后续 Issue

## 测试证据

<!-- 粘贴关键命令与结果，不要只写“已测试” -->

```text
pnpm typecheck
pnpm build
mvn -f services/api/pom.xml test
flutter analyze
flutter test
```

同步相关 PR 必须覆盖 `docs/api-contract.md` §17.10 的场景：

- [ ] 首次全量同步
- [ ] 离线编辑后恢复
- [ ] 双端冲突
- [ ] 重试不重复写入
- [ ] 游标过期
- [ ] 媒体续传
- [ ] 时钟异常
- [ ] 账号切换隔离

## 风险与回滚

<!-- 最坏情况是什么？如何关闭/回滚？是否需要 feature flag？ -->

## 自查清单

- [ ] 所有用户数据查询都带 `owner_id`
- [ ] 接口已声明权限码
- [ ] 错误码复用 `docs/api-contract.md`
- [ ] 日志未输出密码 / Token / 题干全文
- [ ] 无密钥、无调试代码、无大文件
- [ ] 文档 / ADR 已更新
- [ ] CHANGELOG 已更新（用户可见变更）
