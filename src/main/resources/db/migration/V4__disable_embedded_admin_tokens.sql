-- The V1 tokens are public demo credentials. Disable them for every upgraded install.
UPDATE admin_tokens
SET is_active = FALSE
WHERE token IN (
    'magic-ksenia-mentor-token-998877',
    'magic-katya-curator-token-112233'
);
