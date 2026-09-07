CREATE TABLE harvest_season_budget_items (
    id BIGSERIAL PRIMARY KEY,
    harvest_season_id BIGINT NOT NULL,
    category_id BIGINT,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(500) NOT NULL,
    planned_amount NUMERIC(15, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_harvest_season_budget_items_season
        FOREIGN KEY (harvest_season_id) REFERENCES harvest_seasons (id),
    CONSTRAINT fk_harvest_season_budget_items_category
        FOREIGN KEY (category_id) REFERENCES financial_categories (id)
);

CREATE INDEX idx_harvest_season_budget_items_season_id
    ON harvest_season_budget_items (harvest_season_id);
CREATE INDEX idx_harvest_season_budget_items_category_id
    ON harvest_season_budget_items (category_id);
CREATE INDEX idx_harvest_season_budget_items_season_type
    ON harvest_season_budget_items (harvest_season_id, type);

INSERT INTO harvest_season_budget_items (
    harvest_season_id, category_id, type, description, planned_amount, created_at, updated_at
)
SELECT id, NULL, 'EXPENSE', 'Planejamento anterior', expected_cost, created_at, updated_at
FROM harvest_seasons
WHERE expected_cost IS NOT NULL AND expected_cost > 0;

INSERT INTO harvest_season_budget_items (
    harvest_season_id, category_id, type, description, planned_amount, created_at, updated_at
)
SELECT id, NULL, 'INCOME', 'Planejamento anterior', expected_revenue, created_at, updated_at
FROM harvest_seasons
WHERE expected_revenue IS NOT NULL AND expected_revenue > 0;
