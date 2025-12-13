CREATE TABLE IF NOT EXISTS user_preferences (
  user_id BIGINT PRIMARY KEY,
  day_start_hour INT NOT NULL DEFAULT 8,
  day_end_hour INT NOT NULL DEFAULT 20,
  block_minutes INT NOT NULL DEFAULT 60,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
