-- V9: Persist link delivery time and Telegram evidence for mentorship calls.
-- Nullable columns make this an additive, non-blocking migration for existing sessions.
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS link_sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS evidence_url TEXT;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS source_chat_id BIGINT;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS source_message_id INTEGER;

ALTER TABLE students ADD COLUMN IF NOT EXISTS contract_zoom_quota INTEGER NOT NULL DEFAULT 8;
