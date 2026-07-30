ALTER TABLE users
    ADD COLUMN IF NOT EXISTS credentials_version INTEGER NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS password_recovery_codes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    code_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    invalidated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_recovery_codes_user_status
    ON password_recovery_codes (user_id, status);
CREATE INDEX IF NOT EXISTS idx_password_recovery_codes_requested_at
    ON password_recovery_codes (user_id, requested_at);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    password_recovery_code_id BIGINT NOT NULL REFERENCES password_recovery_codes (id),
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    invalidated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user
    ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at
    ON password_reset_tokens (expires_at);
