# 代码全盘检查与修复记录（2026-11-20）

## 检查范围

- 认证、刷新令牌、会话、设备、RBAC
- 管理后台用户/角色/审计
- 题库、题目、四种题型
- 图片上传、RustFS/S3、Markdown/KaTeX 渲染
- Web / Admin 页面与前端请求层

## 已修复问题

### P0：安全与数据一致性

1. **刷新令牌可跨 audience 提权**
   - 问题：普通客户端 refresh token 可在 `/api/v1/admin/auth/refresh` 换取管理员 access token。
   - 修复：V4 迁移为 `app_user_session` 增加 `audience`，刷新时校验 client/admin 必须匹配。
   - 测试：`AuthSecurityIntegrationTest`。

2. **禁用账号可通过邮箱验证重新激活**
   - 问题：`markEmailVerified` 无条件把用户状态改为 `ACTIVE`。
   - 修复：只允许 `PENDING_VERIFICATION -> ACTIVE`，禁用/锁定状态保持不变；验证后状态非 ACTIVE 返回 403。
   - 测试：`AuthSecurityIntegrationTest`。

3. **角色变化后旧令牌继续有效**
   - 问题：移除 ADMIN 或修改角色后，已有会话仍可用到 access token 过期。
   - 修复：角色集合变化时撤销目标用户全部会话。

4. **题库级联删除缺少题目同步墓碑**
   - 问题：删除非空题库只写 BANK 墓碑，多端无法删除题目。
   - 修复：级联删除前为每道题写 `QUESTION` DELETE 墓碑。
   - 测试：`BankCrudIntegrationTest`。

5. **JWT 畸形 claim 可能 500**
   - 修复：过滤器捕获解析/转换异常并清空 SecurityContext。

6. **默认访问策略过宽**
   - 修复：`anyRequest()` 收紧为 `AUD_client`；管理端仍走独立 admin audience。

### P1：功能正确性

7. **题库描述无法清空**
   - 修复：更新时空白描述转为 `null`，前端编辑空描述发送空字符串。
   - 测试：`BankCrudIntegrationTest`。

8. **管理端刷新 Cookie 固定 30 天**
   - 修复：改为读取 `jwtService.refreshTtlDays()`。

9. **前端退出登录在 access token 过期时不刷新**
   - 修复：仅对登录/注册/刷新/验证/重置等公开接口跳过自动刷新，logout 会先刷新再撤销会话。

10. **非法 UUID / 畸形 JSON 返回 500**
    - 修复：统一处理 `MethodArgumentTypeMismatchException`、`HttpMessageNotReadableException`、缺少参数，返回 400 `VALIDATION_ERROR`。

11. **头像可引用他人媒体**
    - 修复：更新资料时校验 `avatarMediaId` 属于当前用户。
    - 同时删除媒体时检查头像引用，避免悬空引用。

12. **选择题选项 key 大小写不一致**
    - 修复：payload 校验时统一转大写，避免答案 key 与选项 key 不一致。

13. **题目 payload 无大小上限**
    - 修复：payload 序列化后超过 200KB 返回 400。

14. **设备列表当前设备可伪造**
    - 修复：使用 JWT 中的 deviceId，而不是客户端 `X-Device-Id` 头。

15. **题目同步变更只有版本号**
    - 修复：`sync_change.payload` 写入完整题目快照，便于后续 pull 同步。

16. **同步令牌哈希缺少唯一索引**
    - 修复：V4 增加 `refresh_token_hash` 唯一索引。

## 尚未修复 / 已知限制

- 管理员 TOTP 2FA 与 Cookie 刷新 CSRF 防护已于后续修复。
- 登录限流已于后续修复（按 IP/账号，普通与管理端分别计数），详见 `docs/remaining-issues-temp.md`。
- 题库创建/更新尚未写 UPSERT 同步变更；同步模块仍是占位。
- 题目列表 N+1 查询已修复：媒体、标签、知识点改为按页批量加载。
- 图片压缩、EXIF 剥离、SVG 栅格化已于后续修复。
- 图片上传在“对象写入成功但数据库插入失败”的极端情况下可能产生孤儿对象。
- 多标签页并发刷新竞态已于后续修复：会话行锁 + 宽限重放 + 前端跨标签页锁。
- CORS 来源目前硬编码为本地开发地址，生产需改为配置项。
- 系统角色（USER/ADMIN）仍允许修改名称和权限，建议后续限制。
- 前端/Android 仍缺少业务级自动化测试。

## 验证

- `mvn -f services/api/pom.xml verify`：20 个测试全部通过
- `pnpm typecheck`：Web / Admin / Desktop 通过
- `pnpm build:web` / `pnpm build:admin`：通过
- 真实 RustFS 冒烟：上传、下载哈希一致、题目引用、删除保护均通过
