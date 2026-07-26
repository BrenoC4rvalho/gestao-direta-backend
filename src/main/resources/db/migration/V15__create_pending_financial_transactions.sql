CREATE TABLE pending_financial_transactions (
    id BIGSERIAL PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    messaging_account_id BIGINT NOT NULL,
    messaging_conversation_id BIGINT NOT NULL,
    source_message_id BIGINT NOT NULL UNIQUE,
    source_channel VARCHAR(30) NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    transaction_date DATE NOT NULL,
    description VARCHAR(160) NOT NULL,
    suggested_category_id BIGINT,
    raw_category_name VARCHAR(100),
    status VARCHAR(30) NOT NULL,
    confidence NUMERIC(4, 3) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    ai_model VARCHAR(120),
    ai_processed_at TIMESTAMP,
    reviewed_by_user_id BIGINT,
    reviewed_at TIMESTAMP,
    rejection_reason VARCHAR(500),
    approved_financial_transaction_id BIGINT UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

ALTER TABLE pending_financial_transactions ADD CONSTRAINT fk_pending_financial_transactions_farm FOREIGN KEY (farm_id) REFERENCES farms (id);
ALTER TABLE pending_financial_transactions ADD CONSTRAINT fk_pending_financial_transactions_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES users (id);
ALTER TABLE pending_financial_transactions ADD CONSTRAINT fk_pending_financial_transactions_account FOREIGN KEY (messaging_account_id) REFERENCES messaging_accounts (id);
ALTER TABLE pending_financial_transactions ADD CONSTRAINT fk_pending_financial_transactions_conversation FOREIGN KEY (messaging_conversation_id) REFERENCES messaging_conversations (id);
ALTER TABLE pending_financial_transactions ADD CONSTRAINT fk_pending_financial_transactions_source_message FOREIGN KEY (source_message_id) REFERENCES messaging_messages (id);
ALTER TABLE pending_financial_transactions ADD CONSTRAINT fk_pending_financial_transactions_category FOREIGN KEY (suggested_category_id) REFERENCES financial_categories (id);
ALTER TABLE pending_financial_transactions ADD CONSTRAINT fk_pending_financial_transactions_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id);
ALTER TABLE pending_financial_transactions ADD CONSTRAINT fk_pending_financial_transactions_approved_transaction FOREIGN KEY (approved_financial_transaction_id) REFERENCES financial_transactions (id);

CREATE INDEX idx_pending_financial_transactions_farm_status ON pending_financial_transactions (farm_id, status);
CREATE INDEX idx_pending_financial_transactions_requested_by ON pending_financial_transactions (requested_by_user_id);
CREATE INDEX idx_pending_financial_transactions_transaction_date ON pending_financial_transactions (transaction_date);
