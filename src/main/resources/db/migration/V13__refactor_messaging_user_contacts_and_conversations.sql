CREATE TABLE user_contacts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    phone_number VARCHAR(20),
    phone_verification_status VARCHAR(30) NOT NULL,
    phone_verified_at TIMESTAMP,
    preferred_channel VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

ALTER TABLE user_contacts ADD CONSTRAINT fk_user_contacts_user_id FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_contacts ADD CONSTRAINT uk_user_contacts_user_id UNIQUE (user_id);
ALTER TABLE user_contacts ADD CONSTRAINT uk_user_contacts_phone_number UNIQUE (phone_number);
CREATE INDEX idx_user_contacts_status ON user_contacts (status);

INSERT INTO user_contacts (user_id, phone_verification_status, preferred_channel, status, created_at, updated_at)
SELECT a.user_id, 'NOT_INFORMED',
       CASE WHEN bool_or(a.status = 'ACTIVE') THEN 'TELEGRAM' ELSE 'NONE' END,
       CASE WHEN bool_or(a.status = 'ACTIVE') THEN 'ACTIVE' ELSE 'PENDING' END,
       COALESCE(min(a.created_at), CURRENT_TIMESTAMP), CURRENT_TIMESTAMP
FROM messaging_accounts a
WHERE a.user_id IS NOT NULL
GROUP BY a.user_id;

ALTER TABLE messaging_accounts ADD COLUMN user_contact_id BIGINT;
UPDATE messaging_accounts a
SET user_contact_id = c.id
FROM user_contacts c
WHERE c.user_id = a.user_id;
ALTER TABLE messaging_accounts ADD CONSTRAINT fk_messaging_accounts_user_contact_id
    FOREIGN KEY (user_contact_id) REFERENCES user_contacts (id);
CREATE INDEX idx_messaging_accounts_user_contact_id ON messaging_accounts (user_contact_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM messaging_accounts GROUP BY channel, external_user_id HAVING count(*) > 1
    ) OR EXISTS (
        SELECT 1 FROM messaging_accounts GROUP BY channel, external_chat_id HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot apply messaging refactor: duplicate external Telegram identifiers must be resolved manually';
    END IF;
END $$;

ALTER TABLE messaging_accounts DROP CONSTRAINT uk_messaging_accounts_channel_user_chat;
ALTER TABLE messaging_accounts ADD CONSTRAINT uk_messaging_accounts_channel_external_user UNIQUE (channel, external_user_id);
ALTER TABLE messaging_accounts ADD CONSTRAINT uk_messaging_accounts_channel_external_chat UNIQUE (channel, external_chat_id);
CREATE UNIQUE INDEX uk_messaging_accounts_active_telegram_contact
    ON messaging_accounts (user_contact_id)
    WHERE channel = 'TELEGRAM' AND status = 'ACTIVE' AND user_contact_id IS NOT NULL;
DROP INDEX idx_messaging_accounts_user_id;
ALTER TABLE messaging_accounts DROP CONSTRAINT fk_messaging_accounts_user_id;
ALTER TABLE messaging_accounts DROP COLUMN user_id;

CREATE TABLE contact_verification_codes (
    id BIGSERIAL PRIMARY KEY,
    user_contact_id BIGINT NOT NULL,
    verification_type VARCHAR(40) NOT NULL,
    channel VARCHAR(30),
    code_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
ALTER TABLE contact_verification_codes ADD CONSTRAINT fk_contact_verification_codes_user_contact_id
    FOREIGN KEY (user_contact_id) REFERENCES user_contacts (id);
CREATE INDEX idx_contact_verification_codes_contact_type_status
    ON contact_verification_codes (user_contact_id, verification_type, status);
CREATE INDEX idx_contact_verification_codes_expires_at ON contact_verification_codes (expires_at);

CREATE TABLE messaging_conversations (
    id BIGSERIAL PRIMARY KEY,
    messaging_account_id BIGINT NOT NULL,
    farm_id BIGINT,
    status VARCHAR(40) NOT NULL,
    current_step VARCHAR(40) NOT NULL,
    last_interaction_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
ALTER TABLE messaging_conversations ADD CONSTRAINT fk_messaging_conversations_account_id
    FOREIGN KEY (messaging_account_id) REFERENCES messaging_accounts (id);
ALTER TABLE messaging_conversations ADD CONSTRAINT fk_messaging_conversations_farm_id
    FOREIGN KEY (farm_id) REFERENCES farms (id);
CREATE INDEX idx_messaging_conversations_account_status ON messaging_conversations (messaging_account_id, status);
CREATE INDEX idx_messaging_conversations_farm_id ON messaging_conversations (farm_id);
CREATE INDEX idx_messaging_conversations_expires_at ON messaging_conversations (expires_at);
CREATE INDEX idx_messaging_conversations_last_interaction_at ON messaging_conversations (last_interaction_at);

INSERT INTO messaging_conversations (messaging_account_id, status, current_step, last_interaction_at, expires_at, created_at, updated_at)
SELECT id, 'COMPLETED', 'NONE', COALESCE(last_interaction_at, created_at), COALESCE(last_interaction_at, created_at), created_at, updated_at
FROM messaging_accounts;

ALTER TABLE messaging_messages ADD COLUMN messaging_conversation_id BIGINT;
UPDATE messaging_messages m SET messaging_conversation_id = c.id
FROM messaging_conversations c
WHERE c.messaging_account_id = m.messaging_account_id AND c.status = 'COMPLETED';
ALTER TABLE messaging_messages ALTER COLUMN messaging_conversation_id SET NOT NULL;
ALTER TABLE messaging_messages ADD CONSTRAINT fk_messaging_messages_conversation_id
    FOREIGN KEY (messaging_conversation_id) REFERENCES messaging_conversations (id);
CREATE INDEX idx_messaging_messages_conversation_id ON messaging_messages (messaging_conversation_id);
ALTER TABLE messaging_messages DROP CONSTRAINT fk_messaging_messages_account_id;
DROP INDEX idx_messaging_messages_account_id;
ALTER TABLE messaging_messages DROP COLUMN messaging_account_id;

CREATE UNIQUE INDEX uk_messaging_conversations_active_account
    ON messaging_conversations (messaging_account_id)
    WHERE status IN ('ACTIVE', 'WAITING_FARM_SELECTION');
