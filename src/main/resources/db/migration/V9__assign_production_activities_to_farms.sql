ALTER TABLE production_activities
    DROP CONSTRAINT IF EXISTS uk_production_activities_name;

ALTER TABLE production_activities
    ADD COLUMN IF NOT EXISTS farm_id BIGINT;

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
    WHERE production_activity_id IS NOT NULL
) activity_farms;

UPDATE production_activities activity
SET farm_id = ranked.farm_id
FROM tmp_production_activity_farms ranked
WHERE activity.id = ranked.activity_id
  AND ranked.farm_rank = 1
  AND activity.farm_id IS NULL;

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
FROM production_activities original_activity, production_activities farm_activity
WHERE season.production_activity_id = original_activity.id
  AND farm_activity.farm_id = season.farm_id
  AND lower(trim(farm_activity.name)) = lower(trim(original_activity.name))
  AND original_activity.farm_id IS DISTINCT FROM season.farm_id;

DELETE FROM production_activities activity
WHERE activity.farm_id IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM harvest_seasons season
      WHERE season.production_activity_id = activity.id
  );

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM production_activities
        WHERE farm_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'There are production_activities with null farm_id after migration.';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM harvest_seasons season
        JOIN production_activities activity ON activity.id = season.production_activity_id
        WHERE season.farm_id <> activity.farm_id
    ) THEN
        RAISE EXCEPTION
            'There are harvest_seasons linked to production_activities from another farm.';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM production_activities
        GROUP BY farm_id, lower(trim(name))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate production_activities found for the same farm and normalized name.';
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
