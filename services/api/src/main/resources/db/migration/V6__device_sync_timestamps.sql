-- ---------------------------------------------------------------------
-- 设备同步时间
-- ---------------------------------------------------------------------
ALTER TABLE app_device
  ADD COLUMN last_sync_pull_at timestamptz;

ALTER TABLE app_device
  ADD COLUMN last_sync_push_at timestamptz;
