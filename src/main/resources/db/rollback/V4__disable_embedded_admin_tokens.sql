-- Re-enables publicly known demo credentials; use only in an isolated test database.
UPDATE admin_tokens
SET is_active = TRUE
WHERE token IN (
    'magic-ksenia-mentor-token-998877',
    'magic-katya-curator-token-112233'
);
