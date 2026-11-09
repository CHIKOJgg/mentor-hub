-- V5: Simplify student niches to strictly Beauty & Styling, and reset today's report to PENDING

UPDATE students
SET niche = 'Бьюти-мастер (Маникюр / Брови)'
WHERE id = 2;

UPDATE students
SET niche = 'Визажист-стилист'
WHERE id = 4;

UPDATE students
SET niche = 'Косметолог-эстетист'
WHERE id = 5;

-- Ensure latest live report for student 1 is in PENDING state for mentor review
UPDATE daily_reports
SET status = 'PENDING'
WHERE student_id = 1 AND report_date = CURRENT_DATE;

-- Bind student 1 to the live Telegram group
UPDATE students
SET tg_chat_id = -5369189672
WHERE id = 1;
