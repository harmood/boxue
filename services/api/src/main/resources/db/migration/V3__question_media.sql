-- ---------------------------------------------------------------------
-- 题目与媒体关联
-- ---------------------------------------------------------------------
CREATE TABLE qb_question_media (
  question_id uuid NOT NULL REFERENCES qb_question(id) ON DELETE CASCADE,
  media_id uuid NOT NULL REFERENCES qb_media(id) ON DELETE RESTRICT,
  sort_order integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (question_id, media_id)
);

CREATE INDEX idx_qb_question_media_media ON qb_question_media (media_id);
