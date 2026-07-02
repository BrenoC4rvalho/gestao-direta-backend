CREATE TABLE production_activities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT uk_production_activities_name UNIQUE (name)
);

CREATE TABLE harvest_seasons (
    id BIGSERIAL PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    production_activity_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    start_date DATE NOT NULL,
    end_date DATE,
    expected_revenue NUMERIC(15, 2),
    expected_cost NUMERIC(15, 2),
    area_hectares NUMERIC(12, 2),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_harvest_seasons_farm
        FOREIGN KEY (farm_id) REFERENCES farms (id),
    CONSTRAINT fk_harvest_seasons_production_activity
        FOREIGN KEY (production_activity_id) REFERENCES production_activities (id),
    CONSTRAINT uk_harvest_seasons_farm_name UNIQUE (farm_id, name)
);

CREATE INDEX idx_harvest_seasons_farm_id ON harvest_seasons (farm_id);
CREATE INDEX idx_harvest_seasons_production_activity_id
    ON harvest_seasons (production_activity_id);
