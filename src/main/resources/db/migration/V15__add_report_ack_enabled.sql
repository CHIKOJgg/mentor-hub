-- V15: Add report_ack_enabled column to mentor_settings (default false)
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS report_ack_enabled BOOLEAN NOT NULL DEFAULT FALSE;
