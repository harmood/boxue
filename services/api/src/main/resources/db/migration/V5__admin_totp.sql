-- ---------------------------------------------------------------------
-- 管理员 TOTP 双因素认证
-- ---------------------------------------------------------------------
CREATE TABLE app_user_totp (
  user_id uuid PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  secret_ciphertext text NOT NULL,
  enabled boolean NOT NULL DEFAULT false,
  confirmed_at timestamptz,
  last_used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_user_totp_enabled ON app_user_totp (enabled);
