-- Production-only, data-preserving schema baseline.
-- This location intentionally excludes legacy demo/data-fix migrations V1-V9.
-- All table creation is idempotent so this can initialize a new database or
-- reconcile an existing database whose Flyway history ends at any v1-v9 step.

CREATE TABLE IF NOT EXISTS students (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    avatar_text VARCHAR(10) NOT NULL,
    tg_username VARCHAR(100),
    tg_chat_id BIGINT UNIQUE,
    niche VARCHAR(255) NOT NULL,
    initial_income NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    current_income NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    initial_avg_ticket NUMERIC(12, 2) DEFAULT 0.00,
    current_avg_ticket NUMERIC(12, 2) DEFAULT 0.00,
    tariff VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_paused BOOLEAN NOT NULL DEFAULT FALSE,
    pause_reason VARCHAR(255),
    next_zoom_at TIMESTAMP WITH TIME ZONE,
    completed_zooms_count INTEGER DEFAULT 0,
    contract_zoom_quota INTEGER NOT NULL DEFAULT 8,
    total_reels INTEGER DEFAULT 0,
    total_carousels INTEGER DEFAULT 0,
    total_storytells INTEGER DEFAULT 0,
    total_mailings INTEGER DEFAULT 0,
    total_new_clients INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_tokens (
    id BIGSERIAL PRIMARY KEY,
    admin_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    tg_user_id BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN admin_tokens.token IS 'SHA-256 hex digest of the admin bearer key';

CREATE TABLE IF NOT EXISTS zoom_meetings (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    meeting_title VARCHAR(255) NOT NULL DEFAULT 'Созвон',
    call_number INTEGER,
    zoom_url TEXT,
    recording_url TEXT,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    link_sent_at TIMESTAMP WITH TIME ZONE,
    evidence_url TEXT,
    source_chat_id BIGINT,
    source_message_id INTEGER,
    completed_at TIMESTAMP WITH TIME ZONE,
    is_completed BOOLEAN DEFAULT FALSE,
    meeting_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_rules (
    id BIGSERIAL PRIMARY KEY,
    hashtag VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    example TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS daily_reports (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_on_time BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    mailings_count INTEGER DEFAULT 0,
    sales_total NUMERIC(12, 2) DEFAULT 0.00,
    new_clients_count INTEGER DEFAULT 0,
    mentor_question TEXT,
    mentor_reply TEXT,
    reply_sent_at TIMESTAMP WITH TIME ZONE,
    voice_transcript TEXT,
    ai_summary TEXT,
    reflection_emotions TEXT,
    reflection_actions TEXT,
    reflection_question TEXT,
    emotional_status VARCHAR(50) DEFAULT 'NORMAL',
    raw_message_text TEXT,
    last_tg_message_id INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_report_date UNIQUE (student_id, report_date)
);

CREATE TABLE IF NOT EXISTS content_drafts (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    draft_type VARCHAR(50) NOT NULL,
    label VARCHAR(255) NOT NULL,
    url TEXT,
    file_tg_id VARCHAR(255),
    is_duplicate BOOLEAN DEFAULT FALSE,
    duplicate_date VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reel_submissions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    reel_url TEXT NOT NULL,
    canonical_url TEXT NOT NULL,
    shortcode VARCHAR(100) NOT NULL,
    reel_hash VARCHAR(64) NOT NULL,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_date VARCHAR(50),
    duplicate_of_id BIGINT REFERENCES reel_submissions(id) ON DELETE SET NULL,
    http_status_code INTEGER DEFAULT 200,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sales_entries (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    amount NUMERIC(12, 2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mentor_settings (
    id BIGSERIAL PRIMARY KEY,
    mentor_tg_id BIGINT NOT NULL UNIQUE,
    mentor_name VARCHAR(255) NOT NULL DEFAULT 'Ксения',
    business_connection_id VARCHAR(255),
    is_business_connected BOOLEAN DEFAULT FALSE,
    reminder_cron VARCHAR(50) DEFAULT '0 0 20 * * MON-SAT',
    deadline_time TIME DEFAULT '21:00:00',
    auto_reminders_enabled BOOLEAN DEFAULT TRUE,
    saturday_poll_cron VARCHAR(50) DEFAULT '0 0 18 * * SAT',
    morning_push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    morning_push_time TIME NOT NULL DEFAULT '09:00:00',
    evening_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    evening_reminder_time TIME NOT NULL DEFAULT '20:00:00',
    strict_deadline_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    saturday_poll_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    saturday_poll_time TIME NOT NULL DEFAULT '18:00:00',
    sunday_rest_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ai_whisper_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ai_summary_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    anti_fraud_dedup_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    zoom_auto_detect_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_message_logs (
    id BIGSERIAL PRIMARY KEY,
    update_id BIGINT NOT NULL,
    tg_chat_id BIGINT,
    tg_user_id BIGINT,
    source_type VARCHAR(50) NOT NULL,
    raw_payload TEXT,
    processing_status VARCHAR(50) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS curator_scripts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    body TEXT NOT NULL DEFAULT '',
    category VARCHAR(100) NOT NULL DEFAULT 'Кураторские',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS student_day_overrides (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    day_date DATE NOT NULL,
    status VARCHAR(40) NOT NULL,
    details TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_day_override UNIQUE (student_id, day_date)
);

-- Reconcile additive columns for databases that already have an older schema.
ALTER TABLE students ADD COLUMN IF NOT EXISTS contract_zoom_quota INTEGER NOT NULL DEFAULT 8;
ALTER TABLE daily_reports ADD COLUMN IF NOT EXISTS last_tg_message_id INTEGER;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS meeting_title VARCHAR(255) NOT NULL DEFAULT 'Созвон';
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS recording_url TEXT;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS call_number INTEGER;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS link_sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS evidence_url TEXT;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS source_chat_id BIGINT;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS source_message_id INTEGER;
ALTER TABLE zoom_meetings ALTER COLUMN scheduled_at DROP NOT NULL;

ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS morning_push_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS morning_push_time TIME NOT NULL DEFAULT '09:00:00';
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS evening_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS evening_reminder_time TIME NOT NULL DEFAULT '20:00:00';
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS strict_deadline_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS saturday_poll_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS saturday_poll_time TIME NOT NULL DEFAULT '18:00:00';
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS sunday_rest_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS ai_whisper_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS ai_summary_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS anti_fraud_dedup_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mentor_settings ADD COLUMN IF NOT EXISTS zoom_auto_detect_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_students_tg_chat_id ON students(tg_chat_id);
CREATE INDEX IF NOT EXISTS idx_students_tg_username ON students(tg_username);
CREATE INDEX IF NOT EXISTS idx_students_active ON students(is_active);
CREATE INDEX IF NOT EXISTS idx_admin_tokens_tg_user ON admin_tokens(tg_user_id);
CREATE INDEX IF NOT EXISTS idx_zoom_meetings_student ON zoom_meetings(student_id);
CREATE INDEX IF NOT EXISTS idx_zoom_meetings_scheduled ON zoom_meetings(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_daily_reports_date_status ON daily_reports(report_date, status);
CREATE INDEX IF NOT EXISTS idx_daily_reports_student ON daily_reports(student_id);
CREATE INDEX IF NOT EXISTS idx_content_drafts_report ON content_drafts(report_id);
CREATE INDEX IF NOT EXISTS idx_reel_submissions_hash ON reel_submissions(reel_hash);
CREATE INDEX IF NOT EXISTS idx_reel_submissions_report ON reel_submissions(report_id);
CREATE INDEX IF NOT EXISTS idx_sales_entries_student ON sales_entries(student_id);
CREATE INDEX IF NOT EXISTS idx_sales_entries_report ON sales_entries(report_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_date ON audit_message_logs(tg_user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_status ON audit_message_logs(processing_status);
CREATE INDEX IF NOT EXISTS idx_curator_scripts_active_order ON curator_scripts(is_active, sort_order, id);
CREATE INDEX IF NOT EXISTS idx_student_day_overrides_student_date ON student_day_overrides(student_id, day_date);

-- Never permit publicly known development credentials from the legacy schema.
UPDATE admin_tokens
SET is_active = FALSE
WHERE token IN (
    'magic-ksenia-mentor-token-998877',
    'magic-katya-curator-token-112233'
);

-- A non-user-specific settings row is needed by the settings API. The real
-- Telegram user ID, when configured, is applied by AdminTokenProvisioner.
INSERT INTO mentor_settings (mentor_tg_id, mentor_name)
SELECT 0, 'Ксения'
WHERE NOT EXISTS (SELECT 1 FROM mentor_settings WHERE mentor_tg_id = 0);
