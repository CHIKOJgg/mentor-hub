-- V11: Purge all test and demo data. Ensure production contains only authentic data or clean zero state.

-- 1. Remove all mock daily reports and child entities
DELETE FROM content_drafts;
DELETE FROM reel_submissions;
DELETE FROM sales_entries;
DELETE FROM zoom_meetings;
DELETE FROM student_day_overrides;
DELETE FROM daily_reports;
DELETE FROM audit_message_logs;

-- 2. Remove all fake demo students
DELETE FROM students WHERE tg_username IN (
    'milana_gorohova',
    'anya_petrova_style',
    'volkova_coach',
    'v_lebedeva_fit',
    'sokolova_smm'
);

-- 3. Reset any remaining student record (e.g. Miroslav @yxxtg) to clean zero metrics
UPDATE students
SET full_name = 'Мирослав',
    avatar_text = 'М',
    niche = 'Тестирование потока',
    initial_income = 0,
    current_income = 0,
    initial_avg_ticket = 0,
    current_avg_ticket = 0,
    total_reels = 0,
    total_carousels = 0,
    total_storytells = 0,
    total_mailings = 0,
    total_new_clients = 0,
    completed_zooms_count = 0,
    next_zoom_at = NULL,
    is_paused = FALSE,
    pause_reason = NULL,
    created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE tg_username = 'yxxtg' OR id = 1;
