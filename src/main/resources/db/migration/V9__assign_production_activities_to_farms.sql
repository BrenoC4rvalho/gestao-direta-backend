ALTER TABLE production_activities
    DROP CONSTRAINT uk_production_activities_name;

ALTER TABLE production_activities
    ADD COLUMN farm_id BIGINT;

CREATE TEMP TABLE tmp_production_activity_farms ON COMMIT DROP AS
SELECT
    production_activity_id AS activity_id,
    farm_id,
    ROW_NUMBER() OVER (PARTITION BY production_activity_id ORDER BY farm_id) AS farm_rank
FROM (
    SELECT DISTINCT
        production_activity_id,
        farm_id
    FROM harvest_seasons
) activity_farms;

UPDATE production_activities activity
SET farm_id = ranked.farm_id
FROM tmp_production_activity_farms ranked
WHERE activity.id = ranked.activity_id
  AND ranked.farm_rank = 1;

INSERT INTO production_activities (farm_id, name, description, status, created_at, updated_at)
SELECT
    ranked.farm_id,
    activity.name,
    activity.description,
    activity.status,
    activity.created_at,
    activity.updated_at
FROM tmp_production_activity_farms ranked
JOIN production_activities activity ON activity.id = ranked.activity_id
WHERE ranked.farm_rank > 1;

UPDATE harvest_seasons season
SET production_activity_id = farm_activity.id
FROM production_activities global_activity, production_activities farm_activity
WHERE season.production_activity_id = global_activity.id
  AND farm_activity.farm_id = season.farm_id
  AND lower(trim(farm_activity.name)) = lower(trim(global_activity.name))
  AND global_activity.farm_id IS DISTINCT FROM season.farm_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM production_activities
        WHERE farm_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot infer farm_id for existing production_activities not used by harvest_seasons. Assign farm_id manually before applying this migration.';
    END IF;
END $$;

ALTER TABLE production_activities
    ALTER COLUMN farm_id SET NOT NULL;

ALTER TABLE production_activities
    ADD CONSTRAINT fk_production_activities_farm
        FOREIGN KEY (farm_id) REFERENCES farms (id);

CREATE INDEX idx_production_activities_farm_id
    ON production_activities (farm_id);

CREATE UNIQUE INDEX uk_production_activities_farm_normalized_name
    ON production_activities (farm_id, lower(trim(name)));
