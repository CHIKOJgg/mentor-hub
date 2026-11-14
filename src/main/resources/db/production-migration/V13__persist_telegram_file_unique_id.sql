-- Store Telegram's stable file identity separately from the downloadable file_id.
ALTER TABLE content_drafts ADD COLUMN IF NOT EXISTS file_unique_id VARCHAR(255);
