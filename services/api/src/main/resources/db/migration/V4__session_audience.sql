-- ---------------------------------------------------------------------
-- 会话增加 audience，防止客户端刷新令牌被用于换取管理员令牌
-- ---------------------------------------------------------------------
ALTER TABLE app_user_session
  ADD COLUMN audience varchar(20) NOT NULL DEFAULT 'client'
  CHECK (audience IN ('client','admin'));

CREATE INDEX idx_app_user_session_audience
  ON app_user_session (user_id, audience, revoked_at);

CREATE UNIQUE INDEX uk_app_user_session_refresh_hash
  ON app_user_session (refresh_token_hash);
