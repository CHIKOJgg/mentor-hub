-- Expand persisted dashboard data without renaming or removing existing columns.
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS meeting_title VARCHAR(255) NOT NULL DEFAULT 'Созвон';
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS recording_url TEXT;
ALTER TABLE zoom_meetings ADD COLUMN IF NOT EXISTS call_number INTEGER;

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

CREATE INDEX IF NOT EXISTS idx_curator_scripts_active_order
    ON curator_scripts(is_active, sort_order, id);

CREATE TABLE IF NOT EXISTS student_day_overrides (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    day_date DATE NOT NULL,
    status VARCHAR(40) NOT NULL,
    details TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_day_override UNIQUE (student_id, day_date)
);

CREATE INDEX IF NOT EXISTS idx_student_day_overrides_student_date
    ON student_day_overrides(student_id, day_date);

INSERT INTO curator_scripts (title, description, body, category, sort_order)
SELECT seed.title, seed.description, seed.body, seed.category, seed.sort_order
FROM (VALUES
    ('Скрипт Direct: Первое касание', 'Холодное касание экспертам без давления', '«Привет, [Имя]! Увидела твой сторителл про запуск — очень откликается глубина. Хочу подарить короткий экспресс-разбор. Актуально на этой неделе?»', 'Продажи', 10),
    ('Офер пакета консультаций', 'Предложение после бесплатного разбора', '«Чтобы убрать это системно за 3 недели и зафиксировать результат, предлагаю пакет из 4 сессий с сопровождением. Стартуем в четверг?»', 'Продажи', 20),
    ('Напоминание о дедлайне', 'Мягкое напоминание за час до срока', '«Привет! Напоминаю, что сегодня ждем твой отчет до 21:00. Если есть затруднение — напиши, вместе разберем.»', 'Кураторские', 30),
    ('Поддержка при спаде энергии', 'Помощь ученице вернуться в рабочий ритм', '«Вижу, что накопилась усталость. Давай сегодня снизим темп и выберем небольшой, выполнимый план. Я помогу пройти его без перегрузки.»', 'Удержание', 40)
) AS seed(title, description, body, category, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM curator_scripts);

-- Preserve existing meetings as the first numbered call for each student.
UPDATE zoom_meetings
SET call_number = (
    SELECT COUNT(*)
    FROM zoom_meetings earlier
    WHERE earlier.student_id = zoom_meetings.student_id
      AND (earlier.scheduled_at < zoom_meetings.scheduled_at
        OR (earlier.scheduled_at = zoom_meetings.scheduled_at AND earlier.id <= zoom_meetings.id))
)
WHERE call_number IS NULL;
