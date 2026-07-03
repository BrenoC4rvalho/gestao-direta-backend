ALTER TABLE financial_transactions
    ADD COLUMN harvest_season_id BIGINT;

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_financial_transactions_harvest_season_id
        FOREIGN KEY (harvest_season_id) REFERENCES harvest_seasons (id);

CREATE INDEX idx_financial_transactions_harvest_season_id
    ON financial_transactions (harvest_season_id);
