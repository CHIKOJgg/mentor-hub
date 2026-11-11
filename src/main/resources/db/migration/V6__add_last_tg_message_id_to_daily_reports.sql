-- V6: Store last Telegram message ID on daily_reports for threaded mentor replies
ALTER TABLE daily_reports ADD COLUMN IF NOT EXISTS last_tg_message_id INT;
