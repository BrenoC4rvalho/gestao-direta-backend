CREATE TABLE financial_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    color VARCHAR(20),
    icon VARCHAR(60),
    farm_id BIGINT,
    is_default BOOLEAN NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

ALTER TABLE financial_categories
    ADD CONSTRAINT fk_financial_categories_farm_id FOREIGN KEY (farm_id) REFERENCES farms (id);

CREATE INDEX idx_financial_categories_farm_id ON financial_categories (farm_id);
CREATE INDEX idx_financial_categories_type ON financial_categories (type);
CREATE INDEX idx_financial_categories_status ON financial_categories (status);
CREATE INDEX idx_financial_categories_is_default ON financial_categories (is_default);
