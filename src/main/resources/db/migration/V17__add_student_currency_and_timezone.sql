-- V17: Add per-student currency and timezone settings

ALTER TABLE students ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'BYN';
ALTER TABLE students ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) NOT NULL DEFAULT 'Europe/Minsk';

COMMENT ON COLUMN students.currency IS 'Валюта расчетов ученицы: BYN или RUB';
COMMENT ON COLUMN students.timezone IS 'Часовой пояс ученицы IANA, например Europe/Minsk, Asia/Yekaterinburg';
