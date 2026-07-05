DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM financial_categories
        WHERE farm_id IS NOT NULL
        GROUP BY farm_id, lower(trim(name)), type
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate financial categories found for the same farm, normalized name and type. Resolve them manually before running this migration.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM financial_transactions transaction
        JOIN financial_categories category ON category.id = transaction.category_id
        WHERE category.farm_id IS NULL
        GROUP BY transaction.farm_id, lower(trim(category.name)), category.type
        HAVING count(DISTINCT category.id) > 1
    ) THEN
        RAISE EXCEPTION 'Multiple global financial categories map to the same farm, normalized name and type. Resolve them manually before running this migration.';
    END IF;
END $$;

CREATE TEMP TABLE gd_global_category_targets (
    source_category_id BIGINT NOT NULL,
    farm_id BIGINT NOT NULL,
    normalized_name TEXT NOT NULL,
    type VARCHAR(20) NOT NULL,
    target_category_id BIGINT
) ON COMMIT DROP;

INSERT INTO gd_global_category_targets (
    source_category_id,
    farm_id,
    normalized_name,
    type,
    target_category_id
)
SELECT DISTINCT
    category.id,
    transaction.farm_id,
    lower(trim(category.name)),
    category.type,
    existing_category.id
FROM financial_transactions transaction
JOIN financial_categories category ON category.id = transaction.category_id
LEFT JOIN financial_categories existing_category
    ON existing_category.farm_id = transaction.farm_id
    AND lower(trim(existing_category.name)) = lower(trim(category.name))
    AND existing_category.type = category.type
WHERE category.farm_id IS NULL;

INSERT INTO financial_categories (
    name,
    type,
    color,
    icon,
    farm_id,
    is_default,
    status,
    created_at,
    updated_at
)
SELECT DISTINCT ON (target.farm_id, target.normalized_name, target.type)
    category.name,
    category.type,
    category.color,
    category.icon,
    target.farm_id,
    false,
    category.status,
    category.created_at,
    category.updated_at
FROM gd_global_category_targets target
JOIN financial_categories category ON category.id = target.source_category_id
WHERE target.target_category_id IS NULL
ORDER BY target.farm_id, target.normalized_name, target.type, category.id;

UPDATE gd_global_category_targets target
SET target_category_id = category.id
FROM financial_categories category
WHERE target.target_category_id IS NULL
  AND category.farm_id = target.farm_id
  AND lower(trim(category.name)) = target.normalized_name
  AND category.type = target.type;

UPDATE financial_transactions transaction
SET category_id = target.target_category_id
FROM gd_global_category_targets target
WHERE transaction.category_id = target.source_category_id
  AND transaction.farm_id = target.farm_id;

DELETE FROM financial_categories
WHERE farm_id IS NULL;

ALTER TABLE financial_categories
    ALTER COLUMN farm_id SET NOT NULL;

DROP INDEX IF EXISTS idx_financial_categories_is_default;

ALTER TABLE financial_categories
    DROP COLUMN is_default;

CREATE UNIQUE INDEX uk_financial_categories_farm_normalized_name_type
    ON financial_categories (farm_id, lower(trim(name)), type);
