# 待修复问题临时清单

> 本文件仅用于逐个修复期间跟踪进度，全部完成后删除。

## 编号

| 编号 | 问题 | 状态 |
| --- | --- | --- |
| 1 | 登录限流与防暴力破解（原“管理员 2FA、登录限流、CSRF”拆分出的第一项） | 已修复 |
| 2 | 管理员 TOTP 2FA（含 TOTP 字段真正校验） | 已修复 |
| 3 | CSRF Token / Cookie 刷新接口防护 | 已修复 |
| 4 | 同步模块第一条完整链路；题库创建/更新 UPSERT 变更 | 已修复 |
| 5 | 题目列表 N+1 查询（媒体、标签、知识点批量加载） | 已修复 |
| 6 | 图片压缩、EXIF 剥离、SVG 兜底 | 已修复 |
| 7 | 多标签页并发刷新令牌轮换竞态 | 待修复 |
| 8 | CORS 来源改为配置项 | 待修复 |
| 9 | 系统角色（USER/ADMIN）修改保护 | 待修复 |
| 10 | 前端/Android 业务级自动化测试 | 待修复 |

## 第 1 项说明

- 普通登录：按 IP 和账号分别限流，默认 30/分钟、10/分钟
- 管理端登录：按 IP 和邮箱分别限流，默认 10/分钟、5/分钟
- 超限返回 `429 AUTH_RATE_LIMITED`，并带 `Retry-After`
- 当前为单实例内存限流；多实例部署前需迁移到 Redis
- IP 取自 `request.getRemoteAddr()`；上反向代理后需配置可信代理头

## 第 2 项说明

- 管理员可在 Admin Console「安全设置」输入当前密码后生成 TOTP 密钥并扫码绑定
- TOTP 密钥使用 AES-GCM 加密存储，密钥可由 `BOXUE_TOTP_SECRET_KEY` 配置
- 启用后登录必须提供 6 位动态验证码，支持 ±1 个时间窗口
- 关闭 2FA 需要当前密码 + 动态验证码
- 启用/关闭 2FA 会撤销其他会话
- 当前未启用 2FA 的管理员仍可登录，但控制台会提示尽快启用；生产环境建议配合后续“强制 2FA”开关

## 第 3 项说明

- Web 端登录/刷新/邮箱验证会写入 `boxue_csrf` Cookie
- 管理端使用独立 `boxue_admin_csrf` Cookie
- Cookie 刷新必须发送 `X-CSRF-Token`，服务端校验双提交值
- 原生客户端通过请求体 refresh token 刷新时不需要 CSRF
- 刷新成功会轮换 CSRF Token
- 新增 `GET /api/v1/auth/csrf` 与 `GET /api/v1/admin/auth/csrf` 引导接口

## 第 4 项说明

- 题库创建/更新会写入 `sync_change` UPSERT 完整快照
- 实现 `POST /api/v1/sync/push`、`GET /api/v1/sync/pull`、`GET /api/v1/sync/status`
- 当前 push 支持 BANK：CREATE/UPSERT/DELETE，UPSERT/DELETE 使用 baseVersion 乐观锁
- 支持 `sync_idempotency`，同一 clientChangeId 重试返回同一结果
- 冲突返回 rejected，不阻塞同批次其他变更
- V6 为设备增加 last_sync_pull_at / last_sync_push_at
- 其他实体、全量同步和 90 天清理仍待后续扩展

## 第 5 项说明

- 题目列表与管理员题目列表改为每页一次性批量加载媒体、标签、知识点
- 每页固定 3 条关联查询，不再按题目逐条查询
- 单题详情仍按单条查询

## 第 6 项说明

- JPEG/PNG/WebP 上传后重新编码，剥离 EXIF 等元数据
- 最大边默认压缩到 1600px，JPEG 质量默认 0.85
- GIF 保留原文件以保留动画
- SVG 先做安全解析与脚本/外链清理，再由 Batik 栅格化为 PNG
- 输出格式可能变化：透明图转 PNG，非透明 WebP 可转 JPEG
- 新增配置：`S3_IMAGE_MAX_DIMENSION`、`S3_JPEG_QUALITY`

## 说明

原剩余问题第一条包含“管理员 2FA、登录限流、CSRF”三个独立能力，已拆分为 1、2、3，便于逐步修复和验收。
