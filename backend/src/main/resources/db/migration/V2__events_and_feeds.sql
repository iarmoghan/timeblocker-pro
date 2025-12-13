CREATE TABLE IF NOT EXISTS source_feeds (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  feed_type VARCHAR(30) NOT NULL, -- e.g. TIMETABLE, DEADLINES
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS events (
  id BIGSERIAL PRIMARY KEY,
  source_feed_id BIGINT NOT NULL REFERENCES source_feeds(id),
  fingerprint TEXT NOT NULL,
  uid TEXT NULL,
  title TEXT NOT NULL,
  description TEXT NULL,
  location TEXT NULL,
  start_time TIMESTAMPTZ NOT NULL,
  end_time TIMESTAMPTZ NOT NULL,
  all_day BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- prevent duplicates per feed import
CREATE UNIQUE INDEX IF NOT EXISTS ux_events_feed_fingerprint
ON events (source_feed_id, fingerprint);

-- Seed a demo feed for user id=1 (so you can import immediately into /feeds/1/import)
INSERT INTO source_feeds (id, user_id, feed_type, name)
VALUES (1, 1, 'TIMETABLE', 'Demo Timetable')
ON CONFLICT DO NOTHING;
