ALTER TABLE messaging_accounts DROP CONSTRAINT uk_messaging_accounts_channel_external_user;
ALTER TABLE messaging_accounts DROP CONSTRAINT uk_messaging_accounts_channel_external_chat;

CREATE UNIQUE INDEX uk_messaging_accounts_active_channel_external_user
    ON messaging_accounts (channel, external_user_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uk_messaging_accounts_active_channel_external_chat
    ON messaging_accounts (channel, external_chat_id)
    WHERE status = 'ACTIVE';
