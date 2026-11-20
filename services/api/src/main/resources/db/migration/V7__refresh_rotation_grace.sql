-- ---------------------------------------------------------------------
-- 刷新令牌轮换宽限：记录旧会话被哪个新会话替换
-- ---------------------------------------------------------------------
ALTER TABLE app_user_session
  ADD COLUMN replaced_by_session_id uuid REFERENCES app_user_session(id) ON DELETE SET NULL;

CREATE INDEX idx_app_user_session_replaced_by
  ON app_user_session (replaced_by_session_id);
