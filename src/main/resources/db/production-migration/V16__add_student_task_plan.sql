-- V16: Add task plan columns to students
ALTER TABLE students ADD COLUMN IF NOT EXISTS task_plan_raw TEXT;
ALTER TABLE students ADD COLUMN IF NOT EXISTS current_task_day INTEGER DEFAULT 1;
ALTER TABLE students ADD COLUMN IF NOT EXISTS task_plan_start_date DATE DEFAULT CURRENT_DATE;
