-- =====================================================================
-- V1: 博学初始数据库结构
-- 依据 docs/database-design.md（2026-11-20）
-- 规则：只向前迁移；本文件发布后不再修改。
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- 通用 updated_at 触发器
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 账号与权限
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
  id uuid PRIMARY KEY,
  email citext NOT NULL,
  username citext NOT NULL,
  password_hash text NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'PENDING_VERIFICATION'
    CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','DISABLED','LOCKED','DELETED')),
  email_verified_at timestamptz,
  last_login_at timestamptz,
  failed_login_count integer NOT NULL DEFAULT 0
    CHECK (failed_login_count >= 0),
  locked_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE UNIQUE INDEX uk_app_user_email
  ON app_user (email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uk_app_user_username
  ON app_user (username) WHERE deleted_at IS NULL;
CREATE INDEX idx_app_user_status ON app_user (status);

CREATE TABLE app_user_profile (
  user_id uuid PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  display_name varchar(100),
  avatar_media_id uuid,
  bio text,
  locale varchar(20) NOT NULL DEFAULT 'zh-CN',
  timezone varchar(64) NOT NULL DEFAULT 'Asia/Shanghai',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_role (
  id uuid PRIMARY KEY,
  code varchar(64) NOT NULL UNIQUE,
  name varchar(100) NOT NULL,
  description text,
  is_system boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_permission (
  id uuid PRIMARY KEY,
  code varchar(100) NOT NULL UNIQUE,
  name varchar(100) NOT NULL,
  description text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_role_permission (
  role_id uuid NOT NULL REFERENCES app_role(id) ON DELETE CASCADE,
  permission_id uuid NOT NULL REFERENCES app_permission(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE app_user_role (
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  role_id uuid NOT NULL REFERENCES app_role(id) ON DELETE RESTRICT,
  granted_by uuid REFERENCES app_user(id),
  granted_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE app_device (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  device_name varchar(100),
  platform varchar(20) NOT NULL CHECK (platform IN ('WEB','DESKTOP','ANDROID','ADMIN')),
  app_version varchar(50),
  last_seen_at timestamptz,
  sync_cursor bigint NOT NULL DEFAULT 0 CHECK (sync_cursor >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_device_user_seen ON app_device (user_id, last_seen_at DESC);

CREATE TABLE app_user_session (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  device_id uuid REFERENCES app_device(id) ON DELETE SET NULL,
  refresh_token_hash char(64) NOT NULL,
  user_agent text,
  ip inet,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz
);

CREATE INDEX idx_app_user_session_user ON app_user_session (user_id, expires_at DESC);
CREATE INDEX idx_app_user_session_expires ON app_user_session (expires_at);

CREATE TABLE app_email_verification (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  email citext NOT NULL,
  token_hash char(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_password_reset (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  token_hash char(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user_setting (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  key varchar(100) NOT NULL,
  value jsonb NOT NULL DEFAULT '{}'::jsonb,
  version integer NOT NULL DEFAULT 1,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, key)
);

CREATE TABLE app_audit_log (
  id bigserial PRIMARY KEY,
  actor_user_id uuid REFERENCES app_user(id) ON DELETE SET NULL,
  action varchar(100) NOT NULL,
  target_type varchar(50),
  target_id varchar(100),
  details jsonb,
  ip inet,
  user_agent text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_audit_log_created ON app_audit_log (created_at DESC);
CREATE INDEX idx_app_audit_log_actor ON app_audit_log (actor_user_id, created_at DESC);

CREATE TABLE app_announcement (
  id uuid PRIMARY KEY,
  title varchar(200) NOT NULL,
  content_md text NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
  published_at timestamptz,
  created_by uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_announcement_status ON app_announcement (status, published_at DESC);

CREATE TABLE app_feature_flag (
  id uuid PRIMARY KEY,
  key varchar(100) NOT NULL UNIQUE,
  description text,
  enabled boolean NOT NULL DEFAULT false,
  rollout jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 题库内容
-- ---------------------------------------------------------------------
CREATE TABLE qb_bank (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name varchar(200) NOT NULL,
  description_md text,
  visibility varchar(20) NOT NULL DEFAULT 'PRIVATE'
    CHECK (visibility IN ('PRIVATE','UNLISTED','PUBLIC')),
  status varchar(20) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE','ARCHIVED')),
  version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE INDEX idx_qb_bank_owner_status ON qb_bank (owner_id, status, updated_at DESC);
CREATE UNIQUE INDEX uk_qb_bank_owner_name
  ON qb_bank (owner_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE qb_question (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  bank_id uuid NOT NULL REFERENCES qb_bank(id) ON DELETE RESTRICT,
  type varchar(30) NOT NULL
    CHECK (type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','FILL_BLANK')),
  status varchar(20) NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
  stem_md text NOT NULL,
  explanation_md text,
  difficulty smallint NOT NULL DEFAULT 3 CHECK (difficulty BETWEEN 1 AND 5),
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  search_text text,
  source varchar(200),
  version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE INDEX idx_qb_question_owner_bank_status
  ON qb_question (owner_id, bank_id, status, updated_at DESC);
CREATE INDEX idx_qb_question_owner_type ON qb_question (owner_id, type);
CREATE INDEX idx_qb_question_search_trgm
  ON qb_question USING gin (search_text gin_trgm_ops);

CREATE TABLE qb_question_version (
  id uuid PRIMARY KEY,
  question_id uuid NOT NULL REFERENCES qb_question(id) ON DELETE CASCADE,
  version_no integer NOT NULL CHECK (version_no >= 1),
  snapshot jsonb NOT NULL,
  created_by uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (question_id, version_no)
);

CREATE TABLE qb_media (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  sha256 char(64) NOT NULL,
  mime_type varchar(100) NOT NULL,
  size_bytes bigint NOT NULL CHECK (size_bytes >= 0),
  width integer CHECK (width IS NULL OR width > 0),
  height integer CHECK (height IS NULL OR height > 0),
  storage_provider varchar(20) NOT NULL DEFAULT 'S3',
  storage_key text NOT NULL,
  alt_text varchar(500),
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  UNIQUE (owner_id, sha256)
);

-- 头像逻辑引用 qb_media，建表完成后补外键
ALTER TABLE app_user_profile
  ADD CONSTRAINT fk_app_user_profile_avatar_media
  FOREIGN KEY (avatar_media_id) REFERENCES qb_media(id) ON DELETE SET NULL;

CREATE TABLE qb_tag (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name varchar(64) NOT NULL,
  color varchar(7),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (owner_id, name)
);

CREATE TABLE qb_question_tag (
  question_id uuid NOT NULL REFERENCES qb_question(id) ON DELETE CASCADE,
  tag_id uuid NOT NULL REFERENCES qb_tag(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (question_id, tag_id)
);

CREATE INDEX idx_qb_question_tag_tag ON qb_question_tag (tag_id, question_id);

CREATE TABLE qb_knowledge_point (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  parent_id uuid REFERENCES qb_knowledge_point(id) ON DELETE CASCADE,
  name varchar(100) NOT NULL,
  path text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_qb_knowledge_point_owner_parent
  ON qb_knowledge_point (owner_id, parent_id);
CREATE INDEX idx_qb_knowledge_point_owner_path
  ON qb_knowledge_point (owner_id, path);

CREATE TABLE qb_question_knowledge_point (
  question_id uuid NOT NULL REFERENCES qb_question(id) ON DELETE CASCADE,
  knowledge_point_id uuid NOT NULL REFERENCES qb_knowledge_point(id) ON DELETE CASCADE,
  PRIMARY KEY (question_id, knowledge_point_id)
);

CREATE INDEX idx_qb_question_kp_kp
  ON qb_question_knowledge_point (knowledge_point_id, question_id);

-- ---------------------------------------------------------------------
-- 练习与复习
-- ---------------------------------------------------------------------
CREATE TABLE practice_session (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  device_id uuid REFERENCES app_device(id) ON DELETE SET NULL,
  bank_id uuid REFERENCES qb_bank(id) ON DELETE SET NULL,
  mode varchar(30) NOT NULL
    CHECK (mode IN ('SEQUENTIAL','RANDOM','WRONG_ONLY','FAVORITE','DUE_REVIEW','CUSTOM','MOCK_EXAM')),
  status varchar(20) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE','PAUSED','FINISHED','ABANDONED')),
  feedback_mode varchar(20) NOT NULL DEFAULT 'IMMEDIATE'
    CHECK (feedback_mode IN ('IMMEDIATE','AFTER_FINISH')),
  question_count integer NOT NULL CHECK (question_count >= 0),
  current_index integer NOT NULL DEFAULT 0 CHECK (current_index >= 0),
  started_at timestamptz NOT NULL DEFAULT now(),
  finished_at timestamptz,
  last_active_at timestamptz NOT NULL DEFAULT now(),
  state jsonb NOT NULL DEFAULT '{}'::jsonb,
  version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_practice_session_owner_active
  ON practice_session (owner_id, status, last_active_at DESC);

CREATE TABLE practice_session_item (
  id uuid PRIMARY KEY,
  session_id uuid NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
  question_id uuid NOT NULL REFERENCES qb_question(id) ON DELETE RESTRICT,
  question_version_id uuid NOT NULL REFERENCES qb_question_version(id) ON DELETE RESTRICT,
  sequence integer NOT NULL CHECK (sequence >= 0),
  answered_at timestamptz,
  skipped boolean NOT NULL DEFAULT false,
  flagged boolean NOT NULL DEFAULT false,
  UNIQUE (session_id, sequence),
  UNIQUE (session_id, question_id)
);

CREATE TABLE practice_attempt (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  session_id uuid REFERENCES practice_session(id) ON DELETE SET NULL,
  session_item_id uuid REFERENCES practice_session_item(id) ON DELETE SET NULL,
  question_id uuid NOT NULL REFERENCES qb_question(id) ON DELETE RESTRICT,
  question_version_id uuid NOT NULL REFERENCES qb_question_version(id) ON DELETE RESTRICT,
  answer jsonb NOT NULL,
  is_correct boolean,
  score numeric(5,2) CHECK (score IS NULL OR (score >= 0 AND score <= 100)),
  duration_ms integer CHECK (duration_ms IS NULL OR duration_ms >= 0),
  confidence varchar(20) CHECK (confidence IS NULL OR confidence IN ('SURE','UNSURE','GUESS')),
  error_reason varchar(30) CHECK (error_reason IS NULL OR error_reason IN ('CONCEPT','CARELESS','MEMORY','GUESS','OTHER')),
  client_attempt_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (owner_id, client_attempt_id)
);

CREATE INDEX idx_practice_attempt_owner_question
  ON practice_attempt (owner_id, question_id, created_at DESC);
CREATE INDEX idx_practice_attempt_owner_session
  ON practice_attempt (owner_id, session_id);

CREATE TABLE practice_note (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  question_id uuid NOT NULL REFERENCES qb_question(id) ON DELETE CASCADE,
  content_md text NOT NULL,
  version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE UNIQUE INDEX uk_practice_note_owner_question
  ON practice_note (owner_id, question_id) WHERE deleted_at IS NULL;

CREATE TABLE practice_favorite (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  question_id uuid NOT NULL REFERENCES qb_question(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE UNIQUE INDEX uk_practice_favorite_owner_question
  ON practice_favorite (owner_id, question_id) WHERE deleted_at IS NULL;

CREATE TABLE review_state (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  question_id uuid NOT NULL REFERENCES qb_question(id) ON DELETE CASCADE,
  due_at timestamptz NOT NULL,
  interval_days integer NOT NULL DEFAULT 0 CHECK (interval_days >= 0),
  ease_factor numeric(4,2) NOT NULL DEFAULT 2.50 CHECK (ease_factor > 0),
  repetitions integer NOT NULL DEFAULT 0 CHECK (repetitions >= 0),
  lapses integer NOT NULL DEFAULT 0 CHECK (lapses >= 0),
  last_reviewed_at timestamptz,
  mastery numeric(5,2) NOT NULL DEFAULT 0 CHECK (mastery >= 0 AND mastery <= 100),
  version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (owner_id, question_id)
);

CREATE INDEX idx_review_state_owner_due ON review_state (owner_id, due_at);

-- ---------------------------------------------------------------------
-- 同步
-- ---------------------------------------------------------------------
CREATE TABLE sync_change (
  change_seq bigserial PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  entity_type varchar(40) NOT NULL,
  entity_id uuid NOT NULL,
  operation varchar(20) NOT NULL CHECK (operation IN ('UPSERT','DELETE')),
  entity_version integer,
  payload jsonb,
  device_id uuid,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_sync_change_owner_seq ON sync_change (owner_id, change_seq);
CREATE INDEX idx_sync_change_entity ON sync_change (entity_type, entity_id);

CREATE TABLE sync_idempotency (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  client_change_id uuid NOT NULL,
  device_id uuid,
  response jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (owner_id, client_change_id)
);

-- ---------------------------------------------------------------------
-- 导入与 AI 预留
-- ---------------------------------------------------------------------
CREATE TABLE import_batch (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  bank_id uuid NOT NULL REFERENCES qb_bank(id) ON DELETE RESTRICT,
  format varchar(20) NOT NULL,
  file_name varchar(255),
  storage_key text,
  status varchar(20) NOT NULL
    CHECK (status IN ('UPLOADED','PARSING','NEEDS_REVIEW','COMMITTING','COMPLETED','FAILED','CANCELLED')),
  total_count integer NOT NULL DEFAULT 0 CHECK (total_count >= 0),
  success_count integer NOT NULL DEFAULT 0 CHECK (success_count >= 0),
  error_count integer NOT NULL DEFAULT 0 CHECK (error_count >= 0),
  error_summary jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

CREATE INDEX idx_import_batch_owner_created ON import_batch (owner_id, created_at DESC);

CREATE TABLE import_item (
  id uuid PRIMARY KEY,
  batch_id uuid NOT NULL REFERENCES import_batch(id) ON DELETE CASCADE,
  row_no integer,
  raw_data jsonb,
  parsed_question jsonb,
  status varchar(20) NOT NULL
    CHECK (status IN ('PENDING','VALID','INVALID','COMMITTED','SKIPPED')),
  error_message text,
  question_id uuid REFERENCES qb_question(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_import_item_batch_status ON import_item (batch_id, status);

CREATE TABLE ai_job (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  task_type varchar(40) NOT NULL
    CHECK (task_type IN ('EXPLAIN_QUESTION','GENERATE_QUESTIONS','OCR_IMPORT','GRADE_SUBJECTIVE')),
  status varchar(20) NOT NULL
    CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
  feature_flag_key varchar(100),
  input jsonb NOT NULL,
  result jsonb,
  error_message text,
  provider varchar(40),
  model varchar(100),
  created_by uuid REFERENCES app_user(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  started_at timestamptz,
  finished_at timestamptz
);

CREATE INDEX idx_ai_job_owner_created ON ai_job (owner_id, created_at DESC);

CREATE TABLE ai_usage (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  job_id uuid REFERENCES ai_job(id) ON DELETE SET NULL,
  provider varchar(40),
  model varchar(100),
  prompt_tokens integer CHECK (prompt_tokens IS NULL OR prompt_tokens >= 0),
  completion_tokens integer CHECK (completion_tokens IS NULL OR completion_tokens >= 0),
  cost numeric(12,6) CHECK (cost IS NULL OR cost >= 0),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_usage_user_created ON ai_usage (user_id, created_at DESC);

CREATE TABLE ai_provider_config (
  id uuid PRIMARY KEY,
  provider_key varchar(50) NOT NULL UNIQUE,
  display_name varchar(100) NOT NULL,
  config_encrypted bytea,
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- updated_at 触发器（统一由数据库维护）
-- ---------------------------------------------------------------------
DO $$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'app_user',
    'app_user_profile',
    'app_role',
    'app_device',
    'app_user_setting',
    'app_announcement',
    'app_feature_flag',
    'qb_bank',
    'qb_question',
    'qb_tag',
    'qb_knowledge_point',
    'practice_session',
    'practice_note',
    'review_state',
    'import_batch',
    'import_item',
    'ai_provider_config'
  ] LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_updated_at ON %I', t, t);
    EXECUTE format(
      'CREATE TRIGGER trg_%I_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
      t, t
    );
  END LOOP;
END;
$$;
