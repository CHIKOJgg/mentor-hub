-- V10 is an additive schema baseline. The safe rollback is to roll back the
-- application image while retaining these backward-compatible tables/columns.
-- Do not drop them automatically: existing application versions or data may use them.
SELECT 1;
