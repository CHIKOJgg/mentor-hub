-- Rollback for V3. Run only after deploying backend code compatible with V2.
DROP TABLE IF EXISTS student_day_overrides;
DROP TABLE IF EXISTS curator_scripts;

ALTER TABLE zoom_meetings
    DROP COLUMN IF EXISTS call_number,
    DROP COLUMN IF EXISTS recording_url,
    DROP COLUMN IF EXISTS meeting_title;

UPDATE zoom_meetings SET scheduled_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE scheduled_at IS NULL;
ALTER TABLE zoom_meetings ALTER COLUMN scheduled_at SET NOT NULL;

ALTER TABLE mentor_settings
    DROP COLUMN IF EXISTS morning_push_enabled,
    DROP COLUMN IF EXISTS morning_push_time,
    DROP COLUMN IF EXISTS evening_reminder_enabled,
    DROP COLUMN IF EXISTS evening_reminder_time,
    DROP COLUMN IF EXISTS strict_deadline_enabled,
    DROP COLUMN IF EXISTS saturday_poll_enabled,
    DROP COLUMN IF EXISTS saturday_poll_time,
    DROP COLUMN IF EXISTS sunday_rest_enabled,
    DROP COLUMN IF EXISTS ai_whisper_enabled,
    DROP COLUMN IF EXISTS ai_summary_enabled,
    DROP COLUMN IF EXISTS anti_fraud_dedup_enabled,
    DROP COLUMN IF EXISTS zoom_auto_detect_enabled;
