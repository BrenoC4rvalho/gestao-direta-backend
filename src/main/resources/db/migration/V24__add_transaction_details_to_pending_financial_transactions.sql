ALTER TABLE pending_financial_transactions
    ADD COLUMN harvest_season_id BIGINT,
    ADD COLUMN payment_method VARCHAR(30),
    ADD COLUMN notes VARCHAR(500);

ALTER TABLE pending_financial_transactions
    ADD CONSTRAINT fk_pending_financial_transactions_harvest_season
    FOREIGN KEY (harvest_season_id) REFERENCES harvest_seasons (id);

CREATE INDEX idx_pending_financial_transactions_harvest_season
    ON pending_financial_transactions (harvest_season_id);
