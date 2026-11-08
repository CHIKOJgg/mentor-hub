-- =============================================================================
-- MENTOR HUB / MENTOR TRACKER — INITIAL SCHEMA (V1)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ТАБЛИЦА ПРОФИЛЕЙ УЧЕНИЦ (students)
-- -----------------------------------------------------------------------------
CREATE TABLE students (
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
    completed_zooms_count INT DEFAULT 0,
    total_reels INT DEFAULT 0,
    total_carousels INT DEFAULT 0,
    total_storytells INT DEFAULT 0,
    total_mailings INT DEFAULT 0,
    total_new_clients INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_students_tg_chat_id ON students(tg_chat_id);
CREATE INDEX idx_students_tg_username ON students(tg_username);
CREATE INDEX idx_students_active ON students(is_active);

-- -----------------------------------------------------------------------------
-- 2. ТАБЛИЦА ТОКЕНОВ БЕСПАРОЛЬНОГО ВХОДА АДМИНИСТРАТОРОВ (admin_tokens)
-- -----------------------------------------------------------------------------
CREATE TABLE admin_tokens (
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

CREATE INDEX idx_admin_tokens_token ON admin_tokens(token);
CREATE INDEX idx_admin_tokens_tg_user ON admin_tokens(tg_user_id);

-- -----------------------------------------------------------------------------
-- 3. ТАБЛИЦА ZOOM-СОЗВОНОВ И РАСПИСАНИЯ ВСТРЕЧ (zoom_meetings)
-- -----------------------------------------------------------------------------
CREATE TABLE zoom_meetings (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    zoom_url TEXT,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    is_completed BOOLEAN DEFAULT FALSE,
    meeting_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_zoom_meetings_student ON zoom_meetings(student_id);
CREATE INDEX idx_zoom_meetings_scheduled ON zoom_meetings(scheduled_at);

-- -----------------------------------------------------------------------------
-- 4. ТАБЛИЦА РЕГЛАМЕНТНЫХ ПРАВИЛ И ХЭШТЕГОВ (task_rules)
-- -----------------------------------------------------------------------------
CREATE TABLE task_rules (
    id BIGSERIAL PRIMARY KEY,
    hashtag VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    example TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 5. ТАБЛИЦА ЕЖЕДНЕВНЫХ СУТОЧНЫХ ОТЧЕТОВ (daily_reports)
-- -----------------------------------------------------------------------------
CREATE TABLE daily_reports (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_on_time BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    mailings_count INT DEFAULT 0,
    sales_total NUMERIC(12, 2) DEFAULT 0.00,
    new_clients_count INT DEFAULT 0,
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
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_report_date UNIQUE (student_id, report_date)
);

CREATE INDEX idx_daily_reports_date_status ON daily_reports(report_date, status);
CREATE INDEX idx_daily_reports_student ON daily_reports(student_id);

-- -----------------------------------------------------------------------------
-- 6. ТАБЛИЦА ЧЕРНОВИКОВ КОНТЕНТА ДО ПУБЛИКАЦИИ (content_drafts)
-- -----------------------------------------------------------------------------
CREATE TABLE content_drafts (
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

CREATE INDEX idx_content_drafts_report ON content_drafts(report_id);

-- -----------------------------------------------------------------------------
-- 7. ТАБЛИЦА СДАННЫХ РОЛИКОВ REELS С АНТИФРОДОМ (reel_submissions)
-- -----------------------------------------------------------------------------
CREATE TABLE reel_submissions (
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
    http_status_code INT DEFAULT 200,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reel_submissions_hash ON reel_submissions(reel_hash);
CREATE INDEX idx_reel_submissions_report ON reel_submissions(report_id);

-- -----------------------------------------------------------------------------
-- 8. ТАБЛИЦА ДЕТАЛЬНЫХ ПРОВОДОК ПРОДАЖ (sales_entries)
-- -----------------------------------------------------------------------------
CREATE TABLE sales_entries (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    amount NUMERIC(12, 2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sales_entries_student ON sales_entries(student_id);
CREATE INDEX idx_sales_entries_report ON sales_entries(report_id);

-- -----------------------------------------------------------------------------
-- 9. ТАБЛИЦА КОНФИГУРАЦИИ НАСТАВНИКА (mentor_settings)
-- -----------------------------------------------------------------------------
CREATE TABLE mentor_settings (
    id BIGSERIAL PRIMARY KEY,
    mentor_tg_id BIGINT NOT NULL UNIQUE,
    mentor_name VARCHAR(255) NOT NULL DEFAULT 'Ксения',
    business_connection_id VARCHAR(255),
    is_business_connected BOOLEAN DEFAULT FALSE,
    reminder_cron VARCHAR(50) DEFAULT '0 0 20 * * MON-SAT',
    deadline_time TIME DEFAULT '21:00:00',
    auto_reminders_enabled BOOLEAN DEFAULT TRUE,
    saturday_poll_cron VARCHAR(50) DEFAULT '0 0 18 * * SAT',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 10. ЖУРНАЛ АУДИТА СЫРЫХ СООБЩЕНИЙ TELEGRAM (audit_message_logs)
-- -----------------------------------------------------------------------------
CREATE TABLE audit_message_logs (
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

CREATE INDEX idx_audit_logs_user_date ON audit_message_logs(tg_user_id, created_at);
CREATE INDEX idx_audit_logs_status ON audit_message_logs(processing_status);

-- =============================================================================
-- SEED DATA (Начальные данные)
-- =============================================================================

INSERT INTO admin_tokens (admin_name, role, token, tg_user_id) VALUES
('Ксения', 'MENTOR', 'magic-ksenia-mentor-token-998877', 11223344),
('Катя', 'CURATOR', 'magic-katya-curator-token-112233', 22334455);

INSERT INTO mentor_settings (mentor_tg_id, mentor_name, is_business_connected) VALUES
(11223344, 'Ксения', TRUE);

INSERT INTO task_rules (hashtag, title, description, is_required, example) VALUES
('#рилс', 'Черновик видео Reels', 'Видеофайл или черновик Reels на проверку перед публикацией. Защита от сдачи повторных роликов.', TRUE, '#рилс видео/ссылка на черновик'),
('#карусель', 'Черновик карусели (слайды / PDF)', 'Текст слайдов, фото-подборка или PDF-файл карусели для проверки смыслов и оферты.', FALSE, '#карусель 5 ошибок при подборе капсулы (слайды 1-5)'),
('#сторителл', 'Сценарий сторителлинга', 'Сценарий серии Instagram Stories: вовлечение, смысловая связка и подводка к продаже.', FALSE, '#сторителл прогрев на консультацию (10 историй)'),
('#рассылки', 'Исходящий трафик (Direct)', 'Количество отправленных сообщений целевой аудитории по утвержденному скрипту.', TRUE, '#рассылки 25 (по скрипту холодного контакта)'),
('#продажи', 'Сумма оформленной продажи', 'Фиксация оплат и предоплат. Автоматически суммирует доход, обновляет средний чек и Точку Б.', FALSE, '#продажи 45000 (оплата за пакет консультаций)'),
('#вопрос', 'Рефлексия дня / Вопрос ментору', 'Мысли, эмоциональное состояние, затыки дня или вопрос к Ксении (текстом или голосовым). AI формирует краткую выжимку.', FALSE, '#вопрос клиентка просит скидку 30%, как отработать?'),
('#клиенты', 'Новые клиенты (субботний опрос)', 'Бот каждую субботу в 18:00 автоматически опрашивает учениц инлайн-кнопками: сколько новых клиентов закрыто за неделю.', FALSE, '#клиенты выбор кнопки: [0] [1] [2] [3] [4] [5+] в субботу 18:00');

INSERT INTO students (full_name, avatar_text, tg_username, tg_chat_id, niche, initial_income, current_income, initial_avg_ticket, current_avg_ticket, tariff, total_reels, total_carousels, total_storytells, total_mailings, total_new_clients, is_active, is_paused, pause_reason, completed_zooms_count) VALUES
('Милана Горохова', 'МГ', 'milana_gorohova', 71010101, 'Стилист-имиджмейкер', 30000.00, 180000.00, 3000.00, 45000.00, 'VIP', 14, 8, 10, 145, 7, TRUE, FALSE, NULL, 4),
('Аня Петрова', 'АП', 'anya_petrova_style', 71020202, 'Психолог для экспертов', 25000.00, 45000.00, 2500.00, 15000.00, 'STANDARD', 7, 3, 4, 72, 2, TRUE, FALSE, NULL, 3),
('Карина Волкова', 'КВ', 'volkova_coach', 71030303, 'Наставник бьюти-мастеров', 50000.00, 310000.00, 5000.00, 60000.00, 'VIP', 18, 12, 14, 210, 11, TRUE, FALSE, NULL, 5),
('Виктория Лебедева', 'ВЛ', 'v_lebedeva_fit', 71040404, 'Фитнес-тренер онлайн', 35000.00, 140000.00, 3500.00, 28000.00, 'STANDARD', 12, 6, 8, 115, 5, TRUE, TRUE, 'Заболела (ОРВИ до 12.11)', 3),
('Полина Соколова', 'ПС', 'sokolova_smm', 71050505, 'SMM-маркетолог', 40000.00, 220000.00, 10000.00, 55000.00, 'VIP', 15, 9, 11, 160, 8, TRUE, FALSE, NULL, 4);
