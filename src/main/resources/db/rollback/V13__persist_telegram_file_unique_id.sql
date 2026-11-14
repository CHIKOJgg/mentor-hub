-- Safe rollback is application-only: older code ignores this nullable column.
-- Keep any collected identifiers; drop the column only in a separate approved maintenance migration.
SELECT 1;
