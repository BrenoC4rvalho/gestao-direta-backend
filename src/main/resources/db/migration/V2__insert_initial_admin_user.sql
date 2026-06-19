INSERT INTO users (
    name,
    email,
    password,
    document,
    user_type,
    status,
    created_at,
    updated_at
)
SELECT
    'Administrador',
    'admin@gestaodireta.com',
    '$2a$10$a9nII7LWt7Df5REk8Czz9e8XtolbDMkU3KoCeZ/dzdi8ik5eZaRea',
    NULL,
    'ADMIN',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'admin@gestaodireta.com'
);
