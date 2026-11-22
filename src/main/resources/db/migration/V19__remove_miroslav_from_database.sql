DELETE FROM sales_entries WHERE student_id IN (
    SELECT id FROM students WHERE full_name ILIKE '%Мирослав%' 
    OR full_name ILIKE '%Miroslav%'
    OR tg_username = 'yxxtg' 
    OR tg_username ILIKE 'yxxtg' 
    OR tg_username ILIKE '@yxxtg' 
    OR (tg_chat_id = -5369189672 AND full_name NOT ILIKE '%Милана%' AND (tg_username IS NULL OR tg_username NOT ILIKE '%milana%'))
) OR report_id IN (
    SELECT id FROM daily_reports WHERE student_id IN (
        SELECT id FROM students WHERE full_name ILIKE '%Мирослав%' 
        OR full_name ILIKE '%Miroslav%'
        OR tg_username = 'yxxtg' 
        OR tg_username ILIKE 'yxxtg' 
        OR tg_username ILIKE '@yxxtg' 
        OR (tg_chat_id = -5369189672 AND full_name NOT ILIKE '%Милана%' AND (tg_username IS NULL OR tg_username NOT ILIKE '%milana%'))
    )
);

DELETE FROM reel_submissions WHERE report_id IN (
    SELECT id FROM daily_reports WHERE student_id IN (
        SELECT id FROM students WHERE full_name ILIKE '%Мирослав%' 
        OR full_name ILIKE '%Miroslav%'
        OR tg_username = 'yxxtg' 
        OR tg_username ILIKE 'yxxtg' 
        OR tg_username ILIKE '@yxxtg' 
        OR (tg_chat_id = -5369189672 AND full_name NOT ILIKE '%Милана%' AND (tg_username IS NULL OR tg_username NOT ILIKE '%milana%'))
    )
);

DELETE FROM content_drafts WHERE report_id IN (
    SELECT id FROM daily_reports WHERE student_id IN (
        SELECT id FROM students WHERE full_name ILIKE '%Мирослав%' 
        OR full_name ILIKE '%Miroslav%'
        OR tg_username = 'yxxtg' 
        OR tg_username ILIKE 'yxxtg' 
        OR tg_username ILIKE '@yxxtg' 
        OR (tg_chat_id = -5369189672 AND full_name NOT ILIKE '%Милана%' AND (tg_username IS NULL OR tg_username NOT ILIKE '%milana%'))
    )
);

DELETE FROM daily_reports WHERE student_id IN (
    SELECT id FROM students WHERE full_name ILIKE '%Мирослав%' 
    OR full_name ILIKE '%Miroslav%'
    OR tg_username = 'yxxtg' 
    OR tg_username ILIKE 'yxxtg' 
    OR tg_username ILIKE '@yxxtg' 
    OR (tg_chat_id = -5369189672 AND full_name NOT ILIKE '%Милана%' AND (tg_username IS NULL OR tg_username NOT ILIKE '%milana%'))
);

DELETE FROM zoom_meetings WHERE student_id IN (
    SELECT id FROM students WHERE full_name ILIKE '%Мирослав%' 
    OR full_name ILIKE '%Miroslav%'
    OR tg_username = 'yxxtg' 
    OR tg_username ILIKE 'yxxtg' 
    OR tg_username ILIKE '@yxxtg' 
    OR (tg_chat_id = -5369189672 AND full_name NOT ILIKE '%Милана%' AND (tg_username IS NULL OR tg_username NOT ILIKE '%milana%'))
);

DELETE FROM student_day_overrides WHERE student_id IN (
    SELECT id FROM students WHERE full_name ILIKE '%Мирослав%' 
    OR full_name ILIKE '%Miroslav%'
    OR tg_username = 'yxxtg' 
    OR tg_username ILIKE 'yxxtg' 
    OR tg_username ILIKE '@yxxtg' 
    OR (tg_chat_id = -5369189672 AND full_name NOT ILIKE '%Милана%' AND (tg_username IS NULL OR tg_username NOT ILIKE '%milana%'))
);

DELETE FROM students WHERE full_name ILIKE '%Мирослав%' 
    OR full_name ILIKE '%Miroslav%'
    OR tg_username = 'yxxtg' 
    OR tg_username ILIKE 'yxxtg' 
    OR tg_username ILIKE '@yxxtg' 
    OR (tg_chat_id = -5369189672 AND full_name NOT ILIKE '%Милана%' AND (tg_username IS NULL OR tg_username NOT ILIKE '%milana%'));

DELETE FROM audit_message_logs
WHERE tg_chat_id = -5369189672
   OR raw_payload ILIKE '%yxxtg%'
   OR raw_payload ILIKE '%мирослав%'
   OR raw_payload ILIKE '%miroslav%';
