CREATE TABLE financial_transactions (
    id BIGSERIAL PRIMARY KEY,
    description VARCHAR(160) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_method VARCHAR(30),
    transaction_date DATE NOT NULL,
    due_date DATE,
    paid_at DATE,
    notes VARCHAR(500),
    farm_id BIGINT NOT NULL,
    category_id BIGINT,
    created_by_user_id BIGINT NOT NULL,
    updated_by_user_id BIGINT,
    record_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_financial_transactions_farm_id FOREIGN KEY (farm_id) REFERENCES farms (id);

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_financial_transactions_category_id
        FOREIGN KEY (category_id) REFERENCES financial_categories (id);

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_financial_transactions_created_by_user_id
        FOREIGN KEY (created_by_user_id) REFERENCES users (id);

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_financial_transactions_updated_by_user_id
        FOREIGN KEY (updated_by_user_id) REFERENCES users (id);

ALTER TABLE financial_transactions
    ADD CONSTRAINT ck_financial_transactions_amount_positive CHECK (amount > 0);

CREATE INDEX idx_financial_transactions_farm_id ON financial_transactions (farm_id);
CREATE INDEX idx_financial_transactions_type ON financial_transactions (type);
CREATE INDEX idx_financial_transactions_status ON financial_transactions (status);
CREATE INDEX idx_financial_transactions_record_status ON financial_transactions (record_status);
CREATE INDEX idx_financial_transactions_transaction_date
    ON financial_transactions (transaction_date);
CREATE INDEX idx_financial_transactions_due_date ON financial_transactions (due_date);
CREATE INDEX idx_financial_transactions_category_id ON financial_transactions (category_id);
CREATE INDEX idx_financial_transactions_created_by_user_id
    ON financial_transactions (created_by_user_id);
