# 博学（Boxue）API 接口契约

> 状态：第一版设计草案
> OpenAPI 落地：Auth / User / Admin 已完成
> 风格：REST + JSON
> 基础路径：`/api/v1`
> 关联文档：[项目整体介绍](../../docs/project-overview.md)、[数据库设计](../../services/api/docs/DATABASE-DESIGN.md)

---

## 1. 设计原则

- 资源导向的 REST API
- JSON 字段使用 `camelCase`
- 数据库字段使用 `snake_case`
- 统一使用 `/api/v1` 版本前缀
- 认证使用 JWT Access Token
- 管理后台接口独立权限和独立 Audience
- 所有用户数据按 `owner_id` 隔离
- 同步接口支持增量拉取、批量推送、幂等重试
- OpenAPI 文档与代码同步生成

---

## 2. 通用约定

### 2.1 Base URL

```text
https://api.example.com/api/v1
```

本地开发：

```text
http://localhost:8080/api/v1
```

### 2.2 请求头

| Header | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 是（登录后） | `Bearer <accessToken>` |
| `X-Device-Id` | 是（登录后） | 客户端设备 ID |
| `X-Client-Platform` | 建议 | `web` / `desktop` / `android` |
| `X-App-Version` | 建议 | 客户端版本 |
| `X-Request-Id` | 可选 | 客户端请求追踪 ID |
| `Idempotency-Key` | 可选 | 创建类接口幂等键 |
| `If-Match` | 可选 | 乐观锁版本，如 `"3"` |

### 2.3 ID 与时间

- ID 使用 UUID v7 字符串
- 时间使用 ISO 8601 UTC，例如 `2026-11-19T10:00:00Z`
- 客户端展示时按用户时区转换

### 2.4 认证模型

- Access Token：JWT，短有效期，默认 15 分钟
- Refresh Token：不透明令牌，默认 30 天
- Web：Refresh Token 放在 `HttpOnly + Secure + SameSite=Lax` Cookie
- Electron / Android：Refresh Token 存放在系统安全存储
- 管理后台使用独立登录接口和独立 Token Audience
- 管理后台 Token 不能调用普通用户接口，普通用户 Token 不能调用管理接口

JWT Claims 建议：

```json
{
  "sub": "user-uuid",
  "aud": "client",
  "roles": ["USER"],
  "permissions": ["USER_BASIC"],
  "deviceId": "device-uuid",
  "iat": 1763546400,
  "exp": 1763547300
}
```

管理后台：

```json
{
  "sub": "admin-uuid",
  "aud": "admin",
  "roles": ["ADMIN"],
  "permissions": ["ADMIN_USER_READ", "ADMIN_ROLE_READ"],
  "deviceId": "device-uuid"
}
```

### 2.5 响应格式

单个资源直接返回对象：

```json
{
  "id": "uuid",
  "name": "示例"
}
```

列表返回：

```json
{
  "items": [],
  "nextCursor": "opaque-cursor",
  "hasMore": false
}
```

### 2.6 分页

使用游标分页：

```text
?cursor=<opaque>&limit=20
```

- `limit` 默认 20，最大 100
- `cursor` 由服务端生成，客户端不得解析
- 排序字段由接口固定
- 管理后台需要导出时使用单独异步导出接口

### 2.7 并发控制

可编辑资源使用 `version` 乐观锁：

```json
{
  "name": "新名称",
  "version": 3
}
```

或使用：

```text
If-Match: "3"
```

版本不匹配返回：

```http
409 Conflict
```

```json
{
  "code": "QUESTION_VERSION_CONFLICT",
  "message": "题目已被其他设备修改",
  "details": {
    "currentVersion": 4
  },
  "traceId": "..."
}
```

### 2.8 幂等

- 创建类请求可使用 `Idempotency-Key`
- 同步变更使用 `clientChangeId`
- 作答使用 `clientAttemptId`
- 重复请求返回第一次的结果，不重复写入

### 2.9 限流

建议响应头：

```text
X-RateLimit-Limit
X-RateLimit-Remaining
X-RateLimit-Reset
Retry-After
```

超限返回：

```http
429 Too Many Requests
Retry-After: <seconds>
```

登录接口当前按“IP + 账号”固定窗口限流，普通登录与管理端登录分别计数；超限返回 `AUTH_RATE_LIMITED`。

### 2.10 CSRF 防护

Web 端使用 HttpOnly Refresh Cookie 时，刷新接口采用双提交 Cookie：

- `GET /api/v1/auth/csrf` 或 `GET /api/v1/admin/auth/csrf` 获取 CSRF Token
- 服务端同时写入非 HttpOnly Cookie：`boxue_csrf` / `boxue_admin_csrf`
- 刷新时必须发送 `X-CSRF-Token`，且与 Cookie 值一致
- 不一致或缺失返回 `403 AUTH_CSRF_INVALID`
- 原生客户端通过请求体传 refresh token 时不需要 CSRF Token

### 2.11 状态码

| 状态码 | 用途 |
| --- | --- |
| 200 | 查询/更新成功 |
| 201 | 创建成功 |
| 202 | 异步任务已接受 |
| 204 | 删除成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 版本冲突/重复资源 |
| 413 | 请求体或文件过大 |
| 422 | 业务校验失败 |
| 429 | 限流 |
| 500 | 服务端错误 |

---

## 3. 错误格式

统一错误体：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求参数不合法",
  "details": {
    "email": "邮箱格式不正确"
  },
  "traceId": "trace-uuid",
  "timestamp": "2026-11-19T10:00:00Z"
}
```

### 3.1 通用错误码

| 错误码 | HTTP | 说明 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 参数校验失败 |
| `UNAUTHORIZED` | 401 | 未登录或 Token 无效 |
| `TOKEN_EXPIRED` | 401 | Token 过期 |
| `FORBIDDEN` | 403 | 无权限 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `CONFLICT` | 409 | 通用冲突 |
| `AUTH_RATE_LIMITED` | 429 | 登录尝试过多 |
| `AUTH_CSRF_INVALID` | 403 | CSRF Token 缺失或不匹配 |
| `AUTH_TOTP_REQUIRED` | 401 | 管理员登录需要 TOTP |
| `AUTH_TOTP_INVALID` | 401 | 管理员 TOTP 验证码错误 |
| `INTERNAL_ERROR` | 500 | 服务端错误 |

### 3.2 账号错误码

| 错误码 | HTTP | 说明 |
| --- | --- | --- |
| `EMAIL_ALREADY_REGISTERED` | 409 | 邮箱已注册 |
| `USERNAME_ALREADY_TAKEN` | 409 | 用户名已占用 |
| `INVALID_CREDENTIALS` | 401 | 账号或密码错误 |
| `EMAIL_NOT_VERIFIED` | 403 | 邮箱未验证 |
| `ACCOUNT_DISABLED` | 403 | 账号已禁用 |
| `ACCOUNT_LOCKED` | 423 | 账号临时锁定 |
| `INVALID_VERIFICATION_TOKEN` | 400 | 验证 token 无效 |
| `INVALID_RESET_TOKEN` | 400 | 重置 token 无效 |

### 3.3 权限与管理员错误码

| 错误码 | HTTP | 说明 |
| --- | --- | --- |
| `PERMISSION_DENIED` | 403 | 缺少权限 |
| `LAST_ADMIN_PROTECTED` | 409 | 不能删除/降级最后一个管理员 |
| `ROLE_IN_USE` | 409 | 角色仍被用户使用 |
| `SYSTEM_ROLE_PROTECTED` | 409 | 系统角色不可删除 |

### 3.4 题库错误码

| 错误码 | HTTP | 说明 |
| --- | --- | --- |
| `BANK_VERSION_CONFLICT` | 409 | 题库版本冲突 |
| `QUESTION_VERSION_CONFLICT` | 409 | 题目版本冲突 |
| `QUESTION_TYPE_UNSUPPORTED` | 422 | 题型不支持 |
| `QUESTION_PAYLOAD_INVALID` | 422 | 题型数据不合法 |
| `MEDIA_NOT_FOUND` | 422 | 图片不存在 |
| `MEDIA_NOT_OWNED` | 422 | 图片不属于当前用户 |
| `FORMULA_UNSUPPORTED` | 422 | 公式语法不受支持 |

### 3.5 练习与同步错误码

| 错误码 | HTTP | 说明 |
| --- | --- | --- |
| `SESSION_CONFLICT` | 409 | 练习会话被其他设备修改 |
| `SESSION_FINISHED` | 409 | 会话已结束 |
| `ATTEMPT_DUPLICATED` | 200 | 已存在，返回原记录 |
| `SYNC_CURSOR_INVALID` | 400 | 同步游标无效 |
| `SYNC_PAYLOAD_TOO_LARGE` | 413 | 同步批次过大 |
| `SYNC_CONFLICT` | 409 | 同步冲突 |

### 3.6 功能开关错误码

| 错误码 | HTTP | 说明 |
| --- | --- | --- |
| `FEATURE_DISABLED` | 403 | 功能未开启 |
| `AI_DISABLED` | 403 | AI 功能未开启 |

---

## 4. 端点总览

### 4.1 用户客户端

| 模块 | 路径前缀 |
| --- | --- |
| 认证 | `/auth` |
| 当前用户 | `/me` |
| 设备 | `/devices` |
| 题库 | `/banks` |
| 题目 | `/questions` |
| 标签 | `/tags` |
| 知识点 | `/knowledge-points` |
| 媒体 | `/media` |
| 导入 | `/imports` |
| 导出 | `/exports` |
| 练习 | `/practice` |
| 复习 | `/review` |
| 笔记/收藏 | `/questions/{id}/note`、`/questions/{id}/favorite` |
| 统计 | `/stats` |
| 设置 | `/settings` |
| 同步 | `/sync` |
| AI 预留 | `/ai` |

### 4.2 管理后台

| 模块 | 路径前缀 |
| --- | --- |
| 管理员认证 | `/admin/auth` |
| 用户管理 | `/admin/users` |
| 角色管理 | `/admin/roles` |
| 权限 | `/admin/permissions` |
| 审计日志 | `/admin/audit-logs` |
| 系统概览 | `/admin/dashboard` |
| 同步监控 | `/admin/sync` |
| 导入监控 | `/admin/imports` |
| 功能开关 | `/admin/feature-flags` |
| 公告 | `/admin/announcements` |
---

## 5. 认证与账号接口

### 5.1 注册

```http
POST /api/v1/auth/register
```

请求：

```json
{
  "email": "alice@example.com",
  "username": "alice",
  "password": "Str0ng-Password!",
  "displayName": "Alice",
  "acceptedTerms": true
}
```

响应 `201`：

```json
{
  "user": {
    "id": "019a...",
    "email": "alice@example.com",
    "username": "alice",
    "displayName": "Alice",
    "status": "PENDING_VERIFICATION"
  },
  "message": "验证邮件已发送"
}
```

说明：

- 邮箱和用户名必须唯一
- 密码强度由服务端校验
- 开放注册接口必须限流
- 验证码/人机校验可配置
- 如果关闭邮箱验证，用户状态直接为 `ACTIVE`，可返回登录 Token

### 5.2 验证邮箱

```http
POST /api/v1/auth/verify-email
```

请求：

```json
{
  "token": "verification-token"
}
```

响应 `200`：

```json
{
  "user": {
    "id": "019a...",
    "status": "ACTIVE"
  },
  "accessToken": "...",
  "expiresIn": 900
}
```

### 5.3 重新发送验证邮件

```http
POST /api/v1/auth/resend-verification
```

请求：

```json
{
  "email": "alice@example.com"
}
```

响应 `202`：

```json
{
  "message": "如果该邮箱存在，验证邮件将发送"
}
```

### 5.4 登录

```http
POST /api/v1/auth/login
```

请求：

```json
{
  "login": "alice@example.com",
  "password": "Str0ng-Password!",
  "device": {
    "deviceId": "019b...",
    "deviceName": "Alice Windows",
    "platform": "DESKTOP",
    "appVersion": "0.1.0"
  }
}
```

`login` 可以是邮箱或用户名。

响应 `200`：

```json
{
  "accessToken": "jwt...",
  "expiresIn": 900,
  "user": {
    "id": "019a...",
    "email": "alice@example.com",
    "username": "alice",
    "displayName": "Alice",
    "status": "ACTIVE"
  },
  "roles": ["USER"],
  "permissions": ["USER_BASIC"]
}
```

Web 端额外设置 Refresh Token Cookie：

```text
Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth
```

Electron/Android 可在响应体中获得 Refresh Token，并存放到安全存储；具体由 `clientType` 或 `X-Client-Platform` 决定。

### 5.5 刷新 Token

```http
POST /api/v1/auth/refresh
```

Web：

- 从 HttpOnly Cookie 读取 Refresh Token
- 需要 CSRF 防护
- 每次刷新轮换 Refresh Token；旧令牌在默认 30 秒宽限期内可重放，只返回 Access Token，不重复覆盖 Cookie
- 服务端使用会话行锁，避免多标签页同时轮换

Electron/Android：

```json
{
  "refreshToken": "..."
}
```

响应：

```json
{
  "accessToken": "jwt...",
  "expiresIn": 900
}
```

### 5.6 退出

```http
POST /api/v1/auth/logout
```

- 撤销当前 Refresh Token
- 清理 Cookie

### 5.7 退出所有设备

```http
POST /api/v1/auth/logout-all
```

- 撤销当前用户所有会话
- 需要重新登录

### 5.8 当前用户

```http
GET /api/v1/me
```

响应：

```json
{
  "id": "019a...",
  "email": "alice@example.com",
  "username": "alice",
  "displayName": "Alice",
  "avatarMediaId": null,
  "locale": "zh-CN",
  "timezone": "Asia/Shanghai",
  "status": "ACTIVE",
  "roles": ["USER"],
  "permissions": ["USER_BASIC"],
  "emailVerified": true
}
```

### 5.9 修改个人资料

```http
PATCH /api/v1/me
```

请求：

```json
{
  "displayName": "Alice Chen",
  "avatarMediaId": "media-uuid",
  "bio": "准备考试中",
  "locale": "zh-CN",
  "timezone": "Asia/Shanghai"
}
```

### 5.10 修改密码

```http
POST /api/v1/me/change-password
```

请求：

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password"
}
```

- 修改成功后撤销其他设备会话
- 当前设备可选择保留

### 5.11 忘记密码

```http
POST /api/v1/auth/forgot-password
```

请求：

```json
{
  "email": "alice@example.com"
}
```

响应 `202`：

```json
{
  "message": "如果该邮箱存在，重置邮件将发送"
}
```

### 5.12 重置密码

```http
POST /api/v1/auth/reset-password
```

请求：

```json
{
  "token": "reset-token",
  "newPassword": "new-password"
}
```

### 5.13 设备列表

```http
GET /api/v1/devices
```

响应：

```json
{
  "items": [
    {
      "id": "device-uuid",
      "deviceName": "Alice Windows",
      "platform": "DESKTOP",
      "appVersion": "0.1.0",
      "lastSeenAt": "2026-11-19T10:00:00Z",
      "current": true
    }
  ]
}
```

### 5.14 移除设备

```http
DELETE /api/v1/devices/{deviceId}
```

- 撤销该设备所有会话
- 不影响其他设备

---

## 6. 管理后台认证

### 6.1 管理员登录

```http
POST /api/v1/admin/auth/login
```

请求：

```json
{
  "email": "admin@example.com",
  "password": "...",
  "totpCode": "123456",
  "device": {
    "deviceId": "admin-device-uuid",
    "deviceName": "Admin Browser",
    "platform": "ADMIN"
  }
}
```

响应：

```json
{
  "accessToken": "jwt...",
  "expiresIn": 900,
  "totpEnabled": true,
  "admin": {
    "id": "admin-uuid",
    "displayName": "Admin"
  },
  "roles": ["ADMIN"],
  "permissions": [
    "ADMIN_USER_READ",
    "ADMIN_USER_WRITE",
    "ADMIN_ROLE_READ",
    "ADMIN_ROLE_WRITE"
  ]
}
```

要求：

- 仅 `ADMIN` 角色可登录
- 启用 TOTP 后登录必须携带动态验证码；未启用时允许登录并在控制台提示设置
- 独立 Token Audience
- 可选 IP 白名单
- 登录限流

### 6.2 管理员刷新/退出

```http
POST /api/v1/admin/auth/csrf
POST /api/v1/admin/auth/refresh
POST /api/v1/admin/auth/logout
POST /api/v1/admin/auth/logout-all
GET  /api/v1/admin/auth/me
```

### 6.3 管理员 TOTP 2FA

```http
GET  /api/v1/admin/auth/totp/status
POST /api/v1/admin/auth/totp/setup
POST /api/v1/admin/auth/totp/enable
POST /api/v1/admin/auth/totp/disable
```

- `setup` 请求体：`{"password":"..."}`，返回 Base32 密钥与 `otpauth://` URI，密钥用 AES-GCM 加密存储
- `enable` 请求体：`{"code":"123456"}`
- `disable` 请求体：`{"password":"...","code":"123456"}`
- 启用后登录缺少验证码返回 `401 AUTH_TOTP_REQUIRED`，验证码错误返回 `401 AUTH_TOTP_INVALID`
- 启用/关闭 2FA 会撤销当前管理员的其他会话

---

## 7. 管理后台：用户与角色

### 7.1 用户列表

```http
GET /api/v1/admin/users?q=&status=&role=&createdFrom=&createdTo=&cursor=&limit=20
```

响应：

```json
{
  "items": [
    {
      "id": "user-uuid",
      "email": "alice@example.com",
      "username": "alice",
      "displayName": "Alice",
      "status": "ACTIVE",
      "roles": ["USER"],
      "emailVerified": true,
      "lastLoginAt": "2026-11-19T10:00:00Z",
      "createdAt": "2026-11-01T10:00:00Z"
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

权限：`ADMIN_USER_READ`

### 7.2 用户详情

```http
GET /api/v1/admin/users/{userId}
```

返回：

- 基本资料
- 角色和权限
- 设备列表
- 题库/题目数量
- 存储用量
- 最近登录

权限：`ADMIN_USER_READ`

### 7.3 修改用户状态

```http
PATCH /api/v1/admin/users/{userId}
```

请求：

```json
{
  "status": "DISABLED",
  "reason": "违规内容"
}
```

- 禁止禁用最后一个管理员
- 写审计日志

权限：`ADMIN_USER_WRITE`

### 7.4 设置用户角色

```http
PUT /api/v1/admin/users/{userId}/roles
```

请求：

```json
{
  "roleCodes": ["USER", "ADMIN"]
}
```

- 替换用户全部角色
- 禁止移除最后一个管理员的 `ADMIN`
- 写审计日志

权限：`ADMIN_ROLE_WRITE`

### 7.5 触发密码重置

```http
POST /api/v1/admin/users/{userId}/password-reset
```

- 向用户邮箱发送重置邮件
- 管理员不直接设置明文密码
- 写审计日志

权限：`ADMIN_USER_WRITE`

### 7.6 强制下线

```http
POST /api/v1/admin/users/{userId}/force-logout
```

- 撤销该用户所有会话
- 写审计日志

权限：`ADMIN_USER_WRITE`

### 7.7 角色列表

```http
GET /api/v1/admin/roles
```

### 7.8 创建角色

```http
POST /api/v1/admin/roles
```

请求：

```json
{
  "code": "CONTENT_REVIEWER",
  "name": "内容审核员",
  "description": "审核公开题库",
  "permissionCodes": ["ADMIN_CONTENT_READ"]
}
```

### 7.9 更新角色

```http
PUT /api/v1/admin/roles/{roleId}
```

- 系统角色不允许删除
- 修改 `ADMIN` 角色权限需要额外审计
- 权限使用权限码列表替换

### 7.10 删除角色

```http
DELETE /api/v1/admin/roles/{roleId}
```

- 系统角色不可删除
- 仍被用户使用时返回 `ROLE_IN_USE`

### 7.11 权限列表

```http
GET /api/v1/admin/permissions
```

### 7.12 审计日志

```http
GET /api/v1/admin/audit-logs?actorId=&action=&targetType=&from=&to=&cursor=
```

权限：`ADMIN_AUDIT_READ`
---

## 8. 题库接口

### 8.1 题库对象

```json
{
  "id": "bank-uuid",
  "name": "高等数学",
  "description": "期末复习题库",
  "visibility": "PRIVATE",
  "status": "ACTIVE",
  "questionCount": 120,
  "practicedCount": 45,
  "accuracy": 0.72,
  "version": 3,
  "createdAt": "2026-11-01T10:00:00Z",
  "updatedAt": "2026-11-19T10:00:00Z"
}
```

### 8.2 题库列表

```http
GET /api/v1/banks?status=&q=&cursor=&limit=20
```

权限：当前用户自己的题库。

### 8.3 创建题库

```http
POST /api/v1/banks
```

请求：

```json
{
  "name": "高等数学",
  "description": "期末复习题库",
  "visibility": "PRIVATE"
}
```

响应 `201`：题库对象。

### 8.4 题库详情

```http
GET /api/v1/banks/{bankId}
```

### 8.5 更新题库

```http
PATCH /api/v1/banks/{bankId}
```

请求：

```json
{
  "name": "高等数学（上）",
  "description": "期末复习题库",
  "status": "ACTIVE",
  "version": 3
}
```

- 版本不匹配返回 `409 BANK_VERSION_CONFLICT`

### 8.6 删除题库

```http
DELETE /api/v1/banks/{bankId}
```

- 软删除
- 需要确认题库为空，或指定 `?cascade=true`
- 删除写入同步墓碑

### 8.7 管理员题库查询（只读）

```http
GET /api/v1/admin/banks?q=&ownerId=&status=&visibility=&cursor=&limit=20
GET /api/v1/admin/banks/{bankId}
```

- 需要 `AUD_admin` 管理员令牌与 `ROLE_ADMIN` 角色
- 返回 `AdminBankView`：额外包含 `ownerId`、`ownerEmail`，不返回练习统计

### 8.8 题库内题目列表

```http
GET /api/v1/banks/{bankId}/questions?type=&status=&tagId=&knowledgePointId=&q=&cursor=&limit=20
```

---

## 9. 题目接口

### 9.1 题目对象

```json
{
  "id": "question-uuid",
  "bankId": "bank-uuid",
  "type": "SINGLE_CHOICE",
  "status": "PUBLISHED",
  "stem": {
    "format": "MARKDOWN_LATEX",
    "content": "已知 $x^2 + 1 = 0$，求 $x$ 的取值范围。"
  },
  "explanation": {
    "format": "MARKDOWN_LATEX",
    "content": "在实数范围内无解。"
  },
  "difficulty": 3,
  "mediaIds": [],
  "tagIds": ["tag-uuid"],
  "knowledgePointIds": ["kp-uuid"],
  "source": "自编",
  "payload": {
    "options": [
      { "key": "A", "content": "有实数解", "mediaIds": [] },
      { "key": "B", "content": "无实数解", "mediaIds": [] }
    ],
    "answer": { "correctOptionKeys": ["B"] },
    "scoring": "ALL_OR_NOTHING"
  },
  "version": 2,
  "createdAt": "2026-11-01T10:00:00Z",
  "updatedAt": "2026-11-19T10:00:00Z"
}
```

内容格式：

```text
MARKDOWN_LATEX
```

- 使用 Markdown 子集
- 行内公式：`$...$`
- 块级公式：`$$...$$`
- 图片引用：`media://<mediaId>`
- 不使用原始 HTML

### 9.2 创建题目

```http
POST /api/v1/banks/{bankId}/questions
```

#### 单选题

```json
{
  "type": "SINGLE_CHOICE",
  "status": "DRAFT",
  "stem": {
    "format": "MARKDOWN_LATEX",
    "content": "已知 $x^2 + 1 = 0$，求 $x$ 的取值范围。"
  },
  "explanation": {
    "format": "MARKDOWN_LATEX",
    "content": "在实数范围内无解。"
  },
  "difficulty": 3,
  "mediaIds": [],
  "tagIds": [],
  "knowledgePointIds": [],
  "payload": {
    "options": [
      { "key": "A", "content": "有实数解", "mediaIds": [] },
      { "key": "B", "content": "无实数解", "mediaIds": [] }
    ],
    "answer": { "correctOptionKeys": ["B"] },
    "scoring": "ALL_OR_NOTHING"
  }
}
```

#### 多选题

```json
{
  "type": "MULTIPLE_CHOICE",
  "status": "DRAFT",
  "stem": {
    "format": "MARKDOWN_LATEX",
    "content": "下列哪些是质数？"
  },
  "difficulty": 2,
  "payload": {
    "options": [
      { "key": "A", "content": "2", "mediaIds": [] },
      { "key": "B", "content": "4", "mediaIds": [] },
      { "key": "C", "content": "5", "mediaIds": [] }
    ],
    "answer": { "correctOptionKeys": ["A", "C"] },
    "scoring": "ALL_OR_NOTHING"
  }
}
```

#### 判断题

```json
{
  "type": "TRUE_FALSE",
  "status": "DRAFT",
  "stem": {
    "format": "MARKDOWN_LATEX",
    "content": "若 $a > b$，则 $a^2 > b^2$。"
  },
  "difficulty": 3,
  "payload": {
    "answer": { "value": false }
  }
}
```

#### 填空题

```json
{
  "type": "FILL_BLANK",
  "status": "DRAFT",
  "stem": {
    "format": "MARKDOWN_LATEX",
    "content": "函数 $f(x)=x^2$ 的导数是 ____。"
  },
  "difficulty": 2,
  "payload": {
    "blanks": [
      {
        "id": "blank-1",
        "acceptedAnswers": ["2x", "2*x"],
        "caseSensitive": false,
        "trimWhitespace": true
      }
    ]
  }
}
```

### 9.3 题目详情

```http
GET /api/v1/questions/{questionId}
```

### 9.4 更新题目

```http
PATCH /api/v1/questions/{questionId}
```

请求可包含：

- `stem`
- `explanation`
- `difficulty`
- `mediaIds`
- `tagIds`
- `knowledgePointIds`
- `payload`
- `status`
- `version`

必须带当前 `version`。

### 9.5 删除题目

```http
DELETE /api/v1/questions/{questionId}
```

- 软删除
- 写入同步墓碑

### 9.6 题目历史版本

```http
GET /api/v1/questions/{questionId}/versions
```

响应：

```json
{
  "items": [
    {
      "versionNo": 1,
      "createdAt": "2026-11-01T10:00:00Z",
      "createdBy": "user-uuid"
    }
  ]
}
```

### 9.7 复制题目

```http
POST /api/v1/questions/{questionId}/duplicate
```

请求：

```json
{
  "targetBankId": "bank-uuid",
  "status": "DRAFT"
}
```

### 9.8 发布题目

```http
POST /api/v1/questions/{questionId}/publish
```

- 校验必填字段
- 校验图片 media 归属
- 校验公式语法
- 写入新版本
- 写入同步变更

### 9.9 归档题目

```http
POST /api/v1/questions/{questionId}/archive
```

### 9.10 全局题目搜索

```http
GET /api/v1/questions/search?q=&bankId=&type=&tagId=&knowledgePointId=&cursor=&limit=20
```
### 9.11 管理员题目查询（只读）

```http
GET /api/v1/admin/questions?q=&bankId=&ownerId=&type=&status=&cursor=&limit=20
GET /api/v1/admin/questions/{questionId}
```

- 需要 `AUD_admin` 管理员令牌与 `ROLE_ADMIN` 角色
- 返回题目内容、题型 payload、所属题库与所有者邮箱

### 9.12 四种题型校验规则

| 题型 | payload 必填 | 校验 |
| --- | --- | --- |
| `SINGLE_CHOICE` | `options`、`answer.correctOptionKeys` | 2-26 个选项，key 唯一，恰好 1 个正确选项 |
| `MULTIPLE_CHOICE` | `options`、`answer.correctOptionKeys` | 2-26 个选项，key 唯一，至少 1 个正确选项 |
| `TRUE_FALSE` | `answer.value` | 必须为布尔值 |
| `FILL_BLANK` | `blanks` | 1-50 个空，空 id 唯一，每个空 1-20 个可接受答案 |

- `stem`、`explanation` 的 `format` 仅支持 `MARKDOWN_LATEX`
- 题目创建/更新/发布/归档都会写入 `qb_question_version` 快照
- 更新必须携带当前 `version`，冲突返回 `409 QUESTION_VERSION_CONFLICT`
- 删除为软删除并写入 `QUESTION` 同步墓碑

---

## 10. 标签与知识点

### 10.1 标签列表

```http
GET /api/v1/tags?q=&cursor=&limit=50
```

### 10.2 创建标签

```http
POST /api/v1/tags
```

```json
{
  "name": "极限",
  "color": "#409EFF"
}
```

### 10.3 更新/删除标签

```http
PATCH  /api/v1/tags/{tagId}
DELETE /api/v1/tags/{tagId}
```

删除标签只解除关联，不删除题目。

### 10.4 知识点树

```http
GET /api/v1/knowledge-points?parentId=&q=
```

响应：

```json
{
  "items": [
    {
      "id": "kp-uuid",
      "parentId": null,
      "name": "高等数学",
      "path": "高等数学",
      "children": [
        {
          "id": "kp-child-uuid",
          "parentId": "kp-uuid",
          "name": "极限",
          "path": "高等数学/极限"
        }
      ]
    }
  ]
}
```

### 10.5 创建/更新/删除知识点

```http
POST   /api/v1/knowledge-points
PATCH  /api/v1/knowledge-points/{id}
DELETE /api/v1/knowledge-points/{id}
```

---

## 11. 媒体接口

### 11.1 上传图片

```http
POST /api/v1/media
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 说明 |
| --- | --- |
| `file` | 图片文件 |

响应 `201`：

```json
{
  "id": "media-uuid",
  "sha256": "5e3d382d...",
  "mimeType": "image/png",
  "sizeBytes": 123456,
  "width": 800,
  "height": 600,
  "url": "/api/v1/media/media-uuid/content",
  "deduped": false,
  "createdAt": "2026-11-20T10:00:00Z"
}
```

限制：

- 支持 PNG、JPEG、WebP、GIF、SVG
- PNG/JPEG/WebP 上传后会重新编码：压缩到最大边 1600px、剥离 EXIF 等元数据
- GIF 保留原始文件以保留动画
- SVG 会清理脚本/外链并栅格化为 PNG，不保存原始 SVG
- 单文件默认最大 10MB
- 服务端按文件头校验真实 MIME，不信任客户端 Content-Type
- 图片按 `owner_id + sha256` 去重；重复上传返回已有记录并标记 `deduped=true`
- 对象存储使用 RustFS/S3，key 为 `{ownerId}/{sha256}.{ext}`
- 首次上传自动创建 bucket

### 11.2 媒体元数据

```http
GET /api/v1/media/{mediaId}
```

### 11.3 读取图片内容

```http
GET /api/v1/media/{mediaId}/content
Authorization: Bearer <access-token>
```

- 返回图片二进制
- 需要认证；Web 端通过带 Token 的 fetch 获取 Blob 后展示
- 响应带 `Cache-Control: private, max-age=300`

### 11.4 删除媒体

```http
DELETE /api/v1/media/{mediaId}
```

- 被未删除题目引用时返回 `409 MEDIA_IN_USE`
- 先解除题目引用再删除
- 软删除元数据并删除对象存储文件

### 11.5 图片引用

题目内容中使用：

```text
media://<mediaId>
```

服务端校验：

- 媒体存在
- 属于当前用户
- 未被删除

Web 端渲染时将 `media://id` 替换为带认证获取的 Blob URL，并支持 Markdown 图片语法：

```markdown
![示意图](media://media-uuid)
```

---

## 12. 导入与导出

### 12.1 创建导入任务

```http
POST /api/v1/imports
Content-Type: multipart/form-data
```

表单：

| 字段 | 说明 |
| --- | --- |
| `file` | CSV/XLSX/JSON/ZIP |
| `bankId` | 目标题库 |
| `format` | `CSV` / `XLSX` / `JSON` / `MARKDOWN_ZIP` |
| `duplicatePolicy` | `SKIP` / `IMPORT_ANYWAY` / `ERROR` |
| `createMissingTags` | boolean |

响应 `202`：

```json
{
  "importId": "import-uuid",
  "status": "PARSING"
}
```

### 12.2 导入状态

```http
GET /api/v1/imports/{importId}
```

响应：

```json
{
  "id": "import-uuid",
  "status": "NEEDS_REVIEW",
  "totalCount": 100,
  "successCount": 92,
  "errorCount": 8,
  "errorSummary": {
    "MISSING_ANSWER": 3,
    "INVALID_TYPE": 2,
    "MEDIA_NOT_FOUND": 3
  }
}
```

### 12.3 导入明细

```http
GET /api/v1/imports/{importId}/items?status=INVALID&cursor=&limit=20
```

### 12.4 修正导入项

```http
PATCH /api/v1/imports/{importId}/items/{itemId}
```

请求为解析后的题目对象。

### 12.5 提交导入

```http
POST /api/v1/imports/{importId}/commit
```

请求：

```json
{
  "itemIds": ["item-uuid-1", "item-uuid-2"]
}
```

- 只提交 `VALID` 或人工修正后的项
- 返回创建数量和失败数量

### 12.6 取消导入

```http
POST /api/v1/imports/{importId}/cancel
```

### 12.7 导入历史

```http
GET /api/v1/imports?cursor=&limit=20
```

### 12.8 创建导出任务

```http
POST /api/v1/exports
```

请求：

```json
{
  "bankId": "bank-uuid",
  "format": "JSON",
  "includeMedia": true
}
```

- `format`: `JSON` / `CSV` / `XLSX` / `MARKDOWN_ZIP`
- 响应 `202`，返回 `exportId`

### 12.9 导出状态与下载

```http
GET /api/v1/exports/{exportId}
GET /api/v1/exports/{exportId}/download
```

下载接口返回短期签名 URL 或文件流。
---

## 13. 练习接口

### 13.1 创建练习会话

```http
POST /api/v1/practice/sessions
```

请求：

```json
{
  "bankId": "bank-uuid",
  "mode": "RANDOM",
  "questionCount": 20,
  "feedbackMode": "IMMEDIATE",
  "timed": false,
  "timeLimitSeconds": null,
  "filters": {
    "tagIds": [],
    "knowledgePointIds": [],
    "difficultyMin": 1,
    "difficultyMax": 5
  }
}
```

模式：

```text
SEQUENTIAL
RANDOM
WRONG_ONLY
FAVORITE
DUE_REVIEW
CUSTOM
MOCK_EXAM
```

响应 `201`：

```json
{
  "id": "session-uuid",
  "mode": "RANDOM",
  "status": "ACTIVE",
  "feedbackMode": "IMMEDIATE",
  "questionCount": 20,
  "currentIndex": 0,
  "startedAt": "2026-11-19T10:00:00Z",
  "version": 1,
  "items": [
    {
      "id": "session-item-uuid",
      "questionId": "question-uuid",
      "sequence": 1,
      "type": "SINGLE_CHOICE",
      "stem": {
        "format": "MARKDOWN_LATEX",
        "content": "..."
      }
    }
  ]
}
```

### 13.2 获取会话

```http
GET /api/v1/practice/sessions/{sessionId}
```

用于跨设备恢复练习。

### 13.3 提交答案

```http
POST /api/v1/practice/sessions/{sessionId}/answers
```

请求：

```json
{
  "sessionItemId": "session-item-uuid",
  "answer": {
    "optionKeys": ["B"]
  },
  "durationMs": 12000,
  "confidence": "UNSURE",
  "errorReason": null,
  "clientAttemptId": "attempt-uuid"
}
```

响应：

- `feedbackMode=IMMEDIATE`：返回是否正确和解析
- `feedbackMode=AFTER_FINISH`：只返回已记录

```json
{
  "attemptId": "attempt-uuid",
  "isCorrect": true,
  "score": 1.0,
  "explanation": {
    "format": "MARKDOWN_LATEX",
    "content": "..."
  }
}
```

### 13.4 跳过题目

```http
POST /api/v1/practice/sessions/{sessionId}/skip
```

```json
{
  "sessionItemId": "session-item-uuid"
}
```

### 13.5 暂停/恢复

```http
POST /api/v1/practice/sessions/{sessionId}/pause
POST /api/v1/practice/sessions/{sessionId}/resume
```

### 13.6 结束会话

```http
POST /api/v1/practice/sessions/{sessionId}/finish
```

响应：

```json
{
  "sessionId": "session-uuid",
  "status": "FINISHED",
  "answeredCount": 20,
  "correctCount": 15,
  "accuracy": 0.75,
  "durationMs": 600000,
  "wrongQuestionIds": ["question-uuid-1", "question-uuid-2"]
}
```

### 13.7 会话结果

```http
GET /api/v1/practice/sessions/{sessionId}/result
```

### 13.8 会话历史

```http
GET /api/v1/practice/sessions?status=&cursor=&limit=20
```

### 13.9 错题列表

```http
GET /api/v1/wrong-questions?bankId=&tagId=&cursor=&limit=20
```

### 13.10 标记已掌握

```http
POST /api/v1/wrong-questions/{questionId}/mastered
```

---

## 14. 复习接口

### 14.1 今日复习队列

```http
GET /api/v1/review/queue?dueBefore=2026-11-19T23:59:59Z&limit=50
```

响应：

```json
{
  "items": [
    {
      "questionId": "question-uuid",
      "dueAt": "2026-11-19T10:00:00Z",
      "intervalDays": 3,
      "mastery": 62.5
    }
  ]
}
```

### 14.2 提交复习评分

```http
POST /api/v1/review/items/{questionId}/review
```

请求：

```json
{
  "rating": "GOOD",
  "clientAttemptId": "attempt-uuid"
}
```

评分：

```text
AGAIN
HARD
GOOD
EASY
```

响应：

```json
{
  "questionId": "question-uuid",
  "nextDueAt": "2026-11-22T10:00:00Z",
  "intervalDays": 3,
  "easeFactor": 2.5
}
```

### 14.3 复习统计

```http
GET /api/v1/review/stats
```

---

## 15. 笔记与收藏

### 15.1 获取笔记

```http
GET /api/v1/questions/{questionId}/note
```

### 15.2 保存笔记

```http
PUT /api/v1/questions/{questionId}/note
```

```json
{
  "content": "这道题容易忽略定义域。",
  "version": 2
}
```

### 15.3 删除笔记

```http
DELETE /api/v1/questions/{questionId}/note
```

### 15.4 收藏题目

```http
POST   /api/v1/questions/{questionId}/favorite
DELETE /api/v1/questions/{questionId}/favorite
```

---

## 16. 统计与设置

### 16.1 总览

```http
GET /api/v1/stats/overview
```

响应：

```json
{
  "totalQuestions": 1200,
  "practicedQuestions": 430,
  "totalAttempts": 2600,
  "accuracy": 0.76,
  "studyDurationMs": 7200000,
  "streakDays": 7,
  "dueReviewCount": 35
}
```

### 16.2 趋势

```http
GET /api/v1/stats/trends?from=2026-11-01&to=2026-11-19&granularity=DAY
```

### 16.3 按题库/标签/知识点统计

```http
GET /api/v1/stats/by-bank
GET /api/v1/stats/by-tag
GET /api/v1/stats/by-knowledge-point
```

### 16.4 用户设置

```http
GET   /api/v1/settings
PATCH /api/v1/settings
```

示例：

```json
{
  "theme": "SYSTEM",
  "fontSize": "MEDIUM",
  "feedbackMode": "IMMEDIATE",
  "dailyReviewReminder": "20:00",
  "syncOnCellular": true
}
```
---

## 17. 同步接口

> 当前已实现 BANK 实体第一条完整链路：服务端创建/更新题库写 UPSERT、push/pull/status；其他实体后续扩展。

多端同步是博学的核心能力。同步接口分为：

- `push`：客户端上传本地变更
- `pull`：客户端拉取服务端变更
- `status`：查看同步状态

### 17.1 同步实体类型

```text
BANK
QUESTION
QUESTION_TAG
KNOWLEDGE_POINT
MEDIA
PRACTICE_SESSION
ATTEMPT
NOTE
FAVORITE
REVIEW_STATE
SETTING
```

说明：

- `ATTEMPT` 只追加，不修改
- `QUESTION`、`BANK`、`NOTE` 使用乐观锁
- `REVIEW_STATE` 由服务端计算，客户端只缓存
- `MEDIA` 的二进制通过媒体上传接口同步，变更日志只记录元数据

### 17.2 推送变更

```http
POST /api/v1/sync/push
```

请求：

```json
{
  "deviceId": "device-uuid",
  "changes": [
    {
      "clientChangeId": "change-uuid-1",
      "entityType": "ATTEMPT",
      "entityId": "attempt-uuid",
      "operation": "CREATE",
      "baseVersion": null,
      "clientTimestamp": "2026-11-19T10:00:00Z",
      "payload": {
        "sessionId": "session-uuid",
        "sessionItemId": "session-item-uuid",
        "questionId": "question-uuid",
        "questionVersionId": "question-version-uuid",
        "answer": { "optionKeys": ["B"] },
        "isCorrect": true,
        "durationMs": 12000,
        "confidence": "SURE",
        "clientAttemptId": "attempt-uuid"
      }
    },
    {
      "clientChangeId": "change-uuid-2",
      "entityType": "NOTE",
      "entityId": "note-uuid",
      "operation": "UPSERT",
      "baseVersion": 2,
      "clientTimestamp": "2026-11-19T10:01:00Z",
      "payload": {
        "questionId": "question-uuid",
        "content": "更新后的笔记"
      }
    }
  ]
}
```

限制：

- 单次最多 500 条变更
- 单次请求体建议不超过 5MB
- `clientChangeId` 必须唯一
- `ATTEMPT` 使用 `clientAttemptId` 幂等
- `operation` 支持 `CREATE` / `UPSERT` / `DELETE`
- 服务端将 `CREATE` 映射为 `sync_change.operation=UPSERT`
- 题目/题库更新必须带 `baseVersion`

响应 `200`：

```json
{
  "accepted": [
    {
      "clientChangeId": "change-uuid-1",
      "serverSeq": 1201,
      "entityVersion": 1
    }
  ],
  "rejected": [
    {
      "clientChangeId": "change-uuid-2",
      "code": "QUESTION_VERSION_CONFLICT",
      "message": "题目已被其他设备修改",
      "conflict": {
        "currentVersion": 3,
        "currentPayload": {}
      }
    }
  ],
  "serverTime": "2026-11-19T10:02:00Z"
}
```

冲突处理：

- 服务端接受成功的变更
- 失败项不阻塞其他项
- 客户端根据 `rejected` 显示冲突并拉取最新数据
- 不允许静默覆盖题目内容

### 17.3 拉取变更

```http
GET /api/v1/sync/pull?cursor=1200&limit=500
```

响应：

```json
{
  "changes": [
    {
      "seq": 1201,
      "entityType": "NOTE",
      "entityId": "note-uuid",
      "operation": "UPSERT",
      "entityVersion": 3,
      "payload": {
        "questionId": "question-uuid",
        "content": "更新后的笔记"
      },
      "serverTime": "2026-11-19T10:02:00Z"
    }
  ],
  "nextCursor": 1201,
  "hasMore": false,
  "serverTime": "2026-11-19T10:02:00Z"
}
```

规则：

- 只返回当前用户 `owner_id` 的变更
- 按 `seq` 升序返回
- 客户端保存 `nextCursor`
- `hasMore=true` 时继续拉取
- 删除事件 `operation=DELETE`
- 90 天以前的变更可能被清理，客户端需要全量同步

### 17.4 全量同步

```http
GET /api/v1/sync/full?cursor=0&limit=500
```

用于：

- 新设备首次登录
- 本地数据库丢失
- 游标过期
- 长期离线后恢复

全量同步返回当前有效实体快照，而不是历史变更。

建议按实体类型分页：

```text
GET /api/v1/sync/full?entityType=QUESTION&cursor=&limit=200
```

### 17.5 同步状态

```http
GET /api/v1/sync/status
```

响应：

```json
{
  "deviceId": "device-uuid",
  "serverCursor": 1201,
  "lastPulledAt": "2026-11-19T10:02:00Z",
  "lastPushedAt": "2026-11-19T10:01:30Z",
  "pendingConflictCount": 1,
  "serverTime": "2026-11-19T10:03:00Z"
}
```

### 17.6 冲突策略

| 实体 | 冲突策略 |
| --- | --- |
| `ATTEMPT` | 追加写入，不冲突 |
| `PRACTICE_SESSION` | 乐观锁，冲突返回 `SESSION_CONFLICT` |
| `NOTE` | 乐观锁；冲突保留本地副本 |
| `FAVORITE` | 最后写入优先 |
| `SETTING` | 最后写入优先，按字段合并可选 |
| `QUESTION` | 乐观锁；冲突要求人工合并 |
| `BANK` | 乐观锁；冲突要求人工合并 |
| `REVIEW_STATE` | 服务端计算，客户端只读缓存 |
| `MEDIA` | 内容哈希去重，不做内容冲突 |

### 17.7 媒体同步

```text
1. 客户端计算文件 SHA-256
2. POST /media 上传
3. 服务端返回 mediaId
4. 题目内容引用 media://<mediaId>
5. sync_change 只同步媒体元数据引用
6. 其他设备按需下载图片
```

要求：

- 上传成功后才能发布题目
- 离线时允许本地临时引用
- 联网后补传并替换为正式 mediaId
- 图片下载失败不影响文字题目显示

### 17.8 同步游标与保留

- 游标由服务端 `change_seq` 生成
- 客户端不得自行计算或修改
- `sync_change` 保留 90 天
- 超过 90 天未同步的设备执行全量同步
- 删除使用墓碑，避免“删除后又被旧设备恢复”

### 17.9 同步客户端要求

每个客户端需要：

- 本地数据库
- 待上传队列
- 同步游标
- 冲突列表
- 重试与退避
- 同步状态界面
- 手动同步按钮
- 网络恢复自动同步
- 同一会话的活跃设备控制

### 17.10 同步测试场景

必须覆盖：

- 新设备首次全量同步
- 两台设备离线修改同一道题
- 手机离线答题后联网
- 重复推送同一作答
- 删除后另一台设备修改
- 大题库分页同步
- 图片上传中断和重试
- 时钟不一致
- 同步过程中应用被杀掉
- 游标过期后的全量同步
---

## 18. 管理后台监控接口

### 18.1 系统概览

```http
GET /api/v1/admin/dashboard/overview
```

响应：

```json
{
  "users": {
    "total": 1200,
    "active24h": 180,
    "new7d": 65
  },
  "content": {
    "banks": 900,
    "questions": 120000
  },
  "practice": {
    "attempts24h": 24000,
    "accuracy24h": 0.74
  },
  "sync": {
    "successRate24h": 0.998,
    "conflictCount24h": 12
  },
  "system": {
    "apiErrorRate": 0.002,
    "apiP95Ms": 180,
    "dbConnections": 32,
    "storageUsedBytes": 1234567890
  }
}
```

权限：`ADMIN_MONITOR_READ`

### 18.2 同步监控

```http
GET /api/v1/admin/sync/stats?from=&to=
```

### 18.3 导入监控

```http
GET /api/v1/admin/imports?status=&cursor=&limit=20
GET /api/v1/admin/imports/{importId}
```

### 18.4 系统健康

```http
GET /api/v1/admin/system/health
```

- 数据库连接
- Redis 状态
- MinIO 状态
- 任务队列
- JVM 指标
- 磁盘空间

### 18.5 功能开关

```http
GET   /api/v1/admin/feature-flags
PATCH /api/v1/admin/feature-flags/{key}
```

请求：

```json
{
  "enabled": false,
  "rollout": {
    "percentage": 0
  }
}
```

AI 功能开关默认关闭。

### 18.6 公告

```http
GET    /api/v1/admin/announcements
POST   /api/v1/admin/announcements
PATCH  /api/v1/admin/announcements/{id}
DELETE /api/v1/admin/announcements/{id}
```

---

## 19. AI 预留接口

> 第一阶段不对外开放，所有接口受功能开关控制，默认返回 `403 AI_DISABLED`。

### 19.1 创建 AI 任务

```http
POST /api/v1/ai/tasks
```

请求：

```json
{
  "taskType": "EXPLAIN_QUESTION",
  "input": {
    "questionId": "question-uuid",
    "language": "zh-CN"
  }
}
```

响应 `202`：

```json
{
  "taskId": "ai-job-uuid",
  "status": "QUEUED"
}
```

### 19.2 查询 AI 任务

```http
GET /api/v1/ai/tasks/{taskId}
```

```json
{
  "id": "ai-job-uuid",
  "taskType": "EXPLAIN_QUESTION",
  "status": "SUCCEEDED",
  "result": {
    "explanation": "..."
  },
  "createdAt": "2026-11-19T10:00:00Z",
  "finishedAt": "2026-11-19T10:00:05Z"
}
```

### 19.3 取消 AI 任务

```http
POST /api/v1/ai/tasks/{taskId}/cancel
```

### 19.4 任务类型预留

```text
EXPLAIN_QUESTION
GENERATE_QUESTIONS
OCR_IMPORT
GRADE_SUBJECTIVE
```

### 19.5 管理端 AI 用量

```http
GET /api/v1/admin/ai/usage?from=&to=&userId=
```

权限：`ADMIN_MONITOR_READ`

### 19.6 AI 约束

- 默认关闭
- 不进入答题、判分、同步关键路径
- AI 结果是建议，不是权威答案
- 需要记录 provider、model、prompt version、用量
- 需要用户同意和隐私提示
- 需要配额和成本控制

---

## 20. 通用数据结构

### 20.1 Page

```json
{
  "items": [],
  "nextCursor": "opaque",
  "hasMore": false
}
```

### 20.2 UserSummary

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "username": "alice",
  "displayName": "Alice",
  "status": "ACTIVE",
  "roles": ["USER"],
  "permissions": ["USER_BASIC"],
  "emailVerified": true,
  "createdAt": "2026-11-19T10:00:00Z"
}
```

### 20.3 Device

```json
{
  "id": "uuid",
  "deviceName": "Alice Windows",
  "platform": "DESKTOP",
  "appVersion": "0.1.0",
  "lastSeenAt": "2026-11-19T10:00:00Z",
  "current": true
}
```

### 20.4 Content

```json
{
  "format": "MARKDOWN_LATEX",
  "content": "题干内容"
}
```

### 20.5 QuestionOption

```json
{
  "key": "A",
  "content": "选项内容",
  "mediaIds": []
}
```

### 20.6 Question

见 9.1。

### 20.7 Attempt

```json
{
  "id": "uuid",
  "questionId": "uuid",
  "questionVersionId": "uuid",
  "answer": {},
  "isCorrect": true,
  "score": 1.0,
  "durationMs": 12000,
  "confidence": "SURE",
  "errorReason": null,
  "createdAt": "2026-11-19T10:00:00Z"
}
```

### 20.8 SyncChange

```json
{
  "seq": 1201,
  "entityType": "NOTE",
  "entityId": "uuid",
  "operation": "UPSERT",
  "entityVersion": 3,
  "payload": {},
  "serverTime": "2026-11-19T10:00:00Z"
}
```

### 20.9 Error

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求参数不合法",
  "details": {},
  "traceId": "trace-uuid",
  "timestamp": "2026-11-19T10:00:00Z"
}
```

---

## 21. OpenAPI 落地建议

建议仓库中维护：

```text
packages/contracts/openapi.yaml
```

生成：

- TypeScript 客户端：`openapi-typescript` 或 `orval`
- Dart 客户端：`openapi-generator`
- Spring 接口/文档：`springdoc-openapi`
- 服务端契约测试：`springdoc` + OpenAPI diff

CI 检查：

- OpenAPI 语法校验
- 破坏性变更检测
- 生成客户端编译
- 主要接口契约测试

---

## 22. 待确认

1. 注册是否强制邮箱验证？当前设计支持可配置。
2. Web 端 Refresh Token 是否统一使用 HttpOnly Cookie？当前建议使用。
3. 是否允许用户在 Web 端完整离线？当前建议 IndexedDB + PWA 有限离线。
4. 题目列表是否需要批量操作接口？当前设计可通过循环调用 PATCH，后续增加批量接口。
5. 是否需要题目分享/公开题库接口？当前设计保留 `visibility`，但未开放公开查询。
6. AI 接口是否只在管理后台开放，还是普通用户也可见？当前设计预留普通用户接口，但默认关闭。
7. 是否需要题库导出任务的历史记录接口？当前设计只保留任务状态。
8. 是否需要管理后台操作的双人复核？当前设计只做权限和审计。
