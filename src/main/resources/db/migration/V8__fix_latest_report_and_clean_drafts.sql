-- V8: Ensure today's report reflects the latest submission metrics and clean redundant drafts
-- 1. Remove redundant generic draft 45 on report 29 created prior to URL dedup fix
DELETE FROM content_drafts
WHERE id = 45 AND report_id = 29;

-- 2. Remove any generic drafts where label contains Instagram URL and a genuine evaluated reel draft exists
DELETE FROM content_drafts
WHERE label LIKE 'Черновик Reels: https%'
  AND report_id IN (
      SELECT report_id FROM reel_submissions
  );

-- 3. Update report 29 reflection and AI summary to accurately reflect the latest report (60k sales, 40 mailings)
UPDATE daily_reports
SET mailings_count = 40,
    sales_total = 60000.00,
    ai_summary = 'Милана отработала 40 рассылок и опубликовала Reels. Зафиксированы продажи на сумму 60 000 рублей.',
    reflection_actions = 'Отправлено 40 рассылок в Direct, опубликован 1 Reels. Зафиксированы продажи на сумму 60 000 рублей.'
WHERE id = 29;

-- 4. Clean up any extra sales entries for student 1's report 29, keeping exactly 1 entry for 60,000.00
DELETE FROM sales_entries
WHERE student_id = 1
  AND report_id = 29
  AND id NOT IN (
      SELECT MIN(id)
      FROM sales_entries
      WHERE student_id = 1 AND report_id = 29
  );

UPDATE sales_entries
SET amount = 60000.00,
    description = '#продажи 60000'
WHERE student_id = 1 AND report_id = 29;
