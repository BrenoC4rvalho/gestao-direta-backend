ALTER TABLE messaging_accounts
    ADD COLUMN link_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN link_blocked_until TIMESTAMP,
    ADD COLUMN last_link_attempt_at TIMESTAMP;

CREATE INDEX idx_messaging_accounts_link_blocked_until
    ON messaging_accounts (link_blocked_until)
    WHERE link_blocked_until IS NOT NULL;
