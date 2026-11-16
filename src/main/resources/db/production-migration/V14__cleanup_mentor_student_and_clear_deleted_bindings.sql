-- =============================================================================
-- V14: Cleanup incorrectly registered mentor as student, unbind deleted students,
-- and restore mentor Telegram identity
-- =============================================================================

-- 1. Cascade cleanup of erroneously created student record for mentor Ksenia Sidarenka
DELETE FROM sales_entries WHERE student_id IN (
    SELECT id FROM students WHERE full_name ILIKE '%KSENIA SIDARENKA%' 
    OR full_name ILIKE '%БЬЮТИ НАСТАВНИК%' 
    OR tg_username = 'sidarenka_ks'
);

DELETE FROM reel_submissions WHERE report_id IN (
    SELECT id FROM daily_reports WHERE student_id IN (
        SELECT id FROM students WHERE full_name ILIKE '%KSENIA SIDARENKA%' 
        OR full_name ILIKE '%БЬЮТИ НАСТАВНИК%' 
        OR tg_username = 'sidarenka_ks'
    )
);

DELETE FROM content_drafts WHERE report_id IN (
    SELECT id FROM daily_reports WHERE student_id IN (
        SELECT id FROM students WHERE full_name ILIKE '%KSENIA SIDARENKA%' 
        OR full_name ILIKE '%БЬЮТИ НАСТАВНИК%' 
        OR tg_username = 'sidarenka_ks'
    )
);

DELETE FROM daily_reports WHERE student_id IN (
    SELECT id FROM students WHERE full_name ILIKE '%KSENIA SIDARENKA%' 
    OR full_name ILIKE '%БЬЮТИ НАСТАВНИК%' 
    OR tg_username = 'sidarenka_ks'
);

DELETE FROM zoom_meetings WHERE student_id IN (
    SELECT id FROM students WHERE full_name ILIKE '%KSENIA SIDARENKA%' 
    OR full_name ILIKE '%БЬЮТИ НАСТАВНИК%' 
    OR tg_username = 'sidarenka_ks'
);

DELETE FROM student_day_overrides WHERE student_id IN (
    SELECT id FROM students WHERE full_name ILIKE '%KSENIA SIDARENKA%' 
    OR full_name ILIKE '%БЬЮТИ НАСТАВНИК%' 
    OR tg_username = 'sidarenka_ks'
);

DELETE FROM students WHERE full_name ILIKE '%KSENIA SIDARENKA%' 
    OR full_name ILIKE '%БЬЮТИ НАСТАВНИК%' 
    OR tg_username = 'sidarenka_ks';

-- 2. Release tg_chat_id from any inactive/soft-deleted students so groups are not locked
UPDATE students SET tg_chat_id = NULL WHERE is_active = FALSE;

-- 3. Release group -1003279128599 (Настав | Вероничка) so it can be cleanly bound to Veronika
UPDATE students SET tg_chat_id = NULL WHERE tg_chat_id = -1003279128599;

-- 4. Update mentor_settings and admin_tokens with real Telegram user ID from audit logs if present
UPDATE mentor_settings
SET mentor_tg_id = (
    SELECT MAX(tg_user_id)
    FROM audit_message_logs
    WHERE (LOWER(raw_payload) LIKE '%ksenia sidarenka%' 
       OR LOWER(raw_payload) LIKE '%sidarenka_ks%'
       OR tg_chat_id = -1003279128599)
      AND tg_user_id IS NOT NULL AND tg_user_id > 0
)
WHERE EXISTS (
    SELECT 1
    FROM audit_message_logs
    WHERE (LOWER(raw_payload) LIKE '%ksenia sidarenka%' 
       OR LOWER(raw_payload) LIKE '%sidarenka_ks%'
       OR tg_chat_id = -1003279128599)
      AND tg_user_id IS NOT NULL AND tg_user_id > 0
);

UPDATE admin_tokens
SET tg_user_id = (
    SELECT MAX(tg_user_id)
    FROM audit_message_logs
    WHERE (LOWER(raw_payload) LIKE '%ksenia sidarenka%' 
       OR LOWER(raw_payload) LIKE '%sidarenka_ks%'
       OR tg_chat_id = -1003279128599)
      AND tg_user_id IS NOT NULL AND tg_user_id > 0
)
WHERE role = 'MENTOR'
  AND EXISTS (
    SELECT 1
    FROM audit_message_logs
    WHERE (LOWER(raw_payload) LIKE '%ksenia sidarenka%' 
       OR LOWER(raw_payload) LIKE '%sidarenka_ks%'
       OR tg_chat_id = -1003279128599)
      AND tg_user_id IS NOT NULL AND tg_user_id > 0
);
