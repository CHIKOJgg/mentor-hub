-- Safe rollback for V9 is application rollback only; the previous app ignores
-- these additive columns. Keep persisted evidence and quota data. Drop columns
-- only in a separate, explicitly approved maintenance migration after backup.
SELECT 1;
