-- V12: Add paused_from_date to students for accurate illness/pause history tracking
ALTER TABLE students ADD COLUMN IF NOT EXISTS paused_from_date DATE;
