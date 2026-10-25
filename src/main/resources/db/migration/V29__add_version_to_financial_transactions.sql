ALTER TABLE financial_transactions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
