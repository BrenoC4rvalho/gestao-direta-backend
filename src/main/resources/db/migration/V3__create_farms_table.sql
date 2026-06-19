CREATE TABLE farms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    document VARCHAR(30),
    city VARCHAR(100),
    state VARCHAR(2),
    total_area NUMERIC(12,2),
    production_type VARCHAR(40),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_farms_status ON farms (status);
CREATE INDEX idx_farms_name ON farms (name);
