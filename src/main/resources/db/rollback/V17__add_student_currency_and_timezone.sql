-- Rollback V17: Remove currency and timezone columns from students

ALTER TABLE students DROP COLUMN IF EXISTS currency;
ALTER TABLE students DROP COLUMN IF EXISTS timezone;
