-- V7: Clean up duplicates created during repeated Telegram test submissions and reset student metrics
-- 1. Remove duplicate reel submissions for the current test report, keeping the earliest one
DELETE FROM reel_submissions
WHERE id NOT IN (
    SELECT MIN(id)
    FROM reel_submissions
    GROUP BY report_id, shortcode
);

-- 2. Remove duplicate content drafts for the current test report, keeping the earliest one
DELETE FROM content_drafts
WHERE id NOT IN (
    SELECT MIN(id)
    FROM content_drafts
    GROUP BY report_id, label
);

-- 3. Remove duplicate sales entries for the current test report, keeping only the single genuine 60k entry
DELETE FROM sales_entries
WHERE student_id = 1
  AND report_id IN (SELECT id FROM daily_reports WHERE student_id = 1 AND report_date = CURRENT_DATE)
  AND id NOT IN (
    SELECT MIN(id)
    FROM sales_entries
    WHERE student_id = 1
      AND report_id IN (SELECT id FROM daily_reports WHERE student_id = 1 AND report_date = CURRENT_DATE)
);

-- 4. Reset today's report metrics for student 1 (Milana Gorokhova) to the true single submission values
UPDATE daily_reports
SET mailings_count = 40,
    sales_total = 60000.00,
    status = 'PENDING'
WHERE student_id = 1 AND report_date = CURRENT_DATE;

-- 5. Restore Milana Gorokhova's aggregates to clean baseline seed values (Point A: 30k -> 180k baseline; 145 mailings; 14 reels)
UPDATE students
SET current_income = 180000.00,
    total_mailings = 145,
    total_reels = 14
WHERE id = 1;

-- 6. Clean up temporary test student created during UI verification
DELETE FROM zoom_meetings WHERE student_id IN (SELECT id FROM students WHERE full_name LIKE 'Икшо%');
DELETE FROM sales_entries WHERE student_id IN (SELECT id FROM students WHERE full_name LIKE 'Икшо%');
DELETE FROM daily_reports WHERE student_id IN (SELECT id FROM students WHERE full_name LIKE 'Икшо%');
DELETE FROM students WHERE full_name LIKE 'Икшо%';
