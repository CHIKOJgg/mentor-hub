-- Read-only pre-deploy audit for databases that previously used Flyway V1-V9.
-- Review results with the data owner and take a backup before any cleanup.

SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT id, full_name, tg_username, tg_chat_id, current_income, total_mailings
FROM students
WHERE tg_username IN (
    'milana_gorohova',
    'anya_petrova_style',
    'volkova_coach',
    'v_lebedeva_fit',
    'sokolova_smm'
)
ORDER BY id;

SELECT id, student_id, report_date, status, sales_total, mailings_count, ai_summary
FROM daily_reports
WHERE report_date BETWEEN DATE '2026-11-02' AND DATE '2026-11-07'
ORDER BY student_id, report_date;

-- Rows targeted by legacy V7/V8 data-fix migrations; inspect before deciding
-- whether a restore or manual repair is required.
SELECT id, student_id, report_date, status, sales_total, mailings_count, ai_summary
FROM daily_reports
WHERE id = 29
   OR (student_id = 1 AND report_date = CURRENT_DATE);

SELECT id, student_id, report_id, amount, description
FROM sales_entries
WHERE report_id = 29
   OR (student_id = 1 AND report_id IN (
       SELECT id FROM daily_reports WHERE student_id = 1 AND report_date = CURRENT_DATE
   ));

SELECT id, admin_name, role, is_active
FROM admin_tokens
WHERE token IN (
    'magic-ksenia-mentor-token-998877',
    'magic-katya-curator-token-112233'
);
