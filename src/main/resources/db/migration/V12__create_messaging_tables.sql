CREATE TABLE messaging_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    channel VARCHAR(30) NOT NULL,
    external_user_id VARCHAR(100) NOT NULL,
    external_chat_id VARCHAR(100) NOT NULL,
    username VARCHAR(100), display_name VARCHAR(200), status VARCHAR(30) NOT NULL,
    verified_at TIMESTAMP, last_interaction_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP
);
ALTER TABLE messaging_accounts ADD CONSTRAINT fk_messaging_accounts_user_id FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE messaging_accounts ADD CONSTRAINT uk_messaging_accounts_channel_user_chat UNIQUE (channel, external_user_id, external_chat_id);
CREATE INDEX idx_messaging_accounts_channel_chat ON messaging_accounts (channel, external_chat_id);
CREATE INDEX idx_messaging_accounts_status ON messaging_accounts (status);
CREATE INDEX idx_messaging_accounts_user_id ON messaging_accounts (user_id);
CREATE TABLE messaging_messages (
    id BIGSERIAL PRIMARY KEY, messaging_account_id BIGINT NOT NULL, channel VARCHAR(30) NOT NULL,
    provider_update_id VARCHAR(100), provider_message_id VARCHAR(100), external_user_id VARCHAR(100), external_chat_id VARCHAR(100),
    direction VARCHAR(20) NOT NULL, message_type VARCHAR(20) NOT NULL, content VARCHAR(4096) NOT NULL, status VARCHAR(30) NOT NULL,
    received_at TIMESTAMP, sent_at TIMESTAMP, processed_at TIMESTAMP, error_message VARCHAR(1000), raw_payload TEXT,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP
);
ALTER TABLE messaging_messages ADD CONSTRAINT fk_messaging_messages_account_id FOREIGN KEY (messaging_account_id) REFERENCES messaging_accounts (id);
ALTER TABLE messaging_messages ADD CONSTRAINT uk_messaging_messages_channel_update UNIQUE (channel, provider_update_id);
CREATE INDEX idx_messaging_messages_account_id ON messaging_messages (messaging_account_id);
CREATE INDEX idx_messaging_messages_status ON messaging_messages (status);
CREATE INDEX idx_messaging_messages_created_at ON messaging_messages (created_at);
