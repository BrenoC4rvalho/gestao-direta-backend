-- Gestão Direta — base demonstrativa para desenvolvimento e TCC.
-- DESTRUTIVO: remove todos os dados de negócio e usuários, mas não altera o schema
-- nem a tabela flyway_schema_history. Data de referência do cenário: 2026-09-07.

BEGIN;

DO $$
BEGIN
    IF current_database() <> 'gestaodireta' THEN
        RAISE EXCEPTION
            'This destructive seed only runs in the local gestaodireta database. Current database: %',
            current_database();
    END IF;
END $$;

-- Exclusão em ordem de dependências. Não há TRUNCATE CASCADE nem alteração de constraints.
DELETE FROM password_reset_tokens;
DELETE FROM password_recovery_codes;
DELETE FROM pending_financial_transactions;
DELETE FROM messaging_messages;
DELETE FROM messaging_conversations;
DELETE FROM messaging_accounts;
DELETE FROM contact_verification_codes;
DELETE FROM harvest_season_budget_items;
DELETE FROM financial_transactions;
DELETE FROM harvest_seasons;
DELETE FROM financial_categories;
DELETE FROM production_activities;
DELETE FROM farm_users;
DELETE FROM farms;
DELETE FROM user_contacts;
DELETE FROM users;

-- A numeração fica previsível sem depender dos IDs da execução anterior.
SELECT setval(pg_get_serial_sequence('users', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('user_contacts', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('farms', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('farm_users', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('financial_categories', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('production_activities', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('harvest_seasons', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('harvest_season_budget_items', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('financial_transactions', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('messaging_accounts', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('messaging_conversations', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('messaging_messages', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('contact_verification_codes', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('pending_financial_transactions', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('password_recovery_codes', 'id'), 1, false);
SELECT setval(pg_get_serial_sequence('password_reset_tokens', 'id'), 1, false);

CREATE TEMP TABLE demo_users (
    user_key TEXT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO users (
    name, email, password, document, user_type, status, credentials_version, created_at, updated_at
)
VALUES
    ('Marina Duarte', 'admin@gestaodireta.local', '$2a$10$bVzr6y4xJNLJVQexuIQv/OARRsCL0I5ggfuXXnUYZjIk8VzOh8WbO', NULL, 'ADMIN', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('João Ferreira', 'produtor.boaesperanca@gestaodireta.local', '$2a$10$bVzr6y4xJNLJVQexuIQv/OARRsCL0I5ggfuXXnUYZjIk8VzOh8WbO', NULL, 'USER', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Ana Ribeiro', 'produtor.santahelena@gestaodireta.local', '$2a$10$bVzr6y4xJNLJVQexuIQv/OARRsCL0I5ggfuXXnUYZjIk8VzOh8WbO', NULL, 'USER', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Carlos Mendes', 'produtor.saomiguel@gestaodireta.local', '$2a$10$bVzr6y4xJNLJVQexuIQv/OARRsCL0I5ggfuXXnUYZjIk8VzOh8WbO', NULL, 'USER', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Renata Lopes', 'produtor.valeverde@gestaodireta.local', '$2a$10$bVzr6y4xJNLJVQexuIQv/OARRsCL0I5ggfuXXnUYZjIk8VzOh8WbO', NULL, 'USER', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Diego Martins', 'colaborador.valeverde@gestaodireta.local', '$2a$10$bVzr6y4xJNLJVQexuIQv/OARRsCL0I5ggfuXXnUYZjIk8VzOh8WbO', NULL, 'USER', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Paula Nogueira', 'contador@gestaodireta.local', '$2a$10$bVzr6y4xJNLJVQexuIQv/OARRsCL0I5ggfuXXnUYZjIk8VzOh8WbO', NULL, 'USER', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO demo_users (user_key, user_id, email)
SELECT user_key, user_record.id, user_record.email
FROM (
    VALUES
        ('admin', 'admin@gestaodireta.local'),
        ('boa_produtor', 'produtor.boaesperanca@gestaodireta.local'),
        ('santa_produtor', 'produtor.santahelena@gestaodireta.local'),
        ('miguel_produtor', 'produtor.saomiguel@gestaodireta.local'),
        ('vale_produtor', 'produtor.valeverde@gestaodireta.local'),
        ('vale_colaborador', 'colaborador.valeverde@gestaodireta.local'),
        ('contador', 'contador@gestaodireta.local')
) AS seed(user_key, email)
JOIN users user_record ON user_record.email = seed.email;

INSERT INTO user_contacts (
    user_id, phone_number, phone_verification_status, phone_verified_at,
    preferred_channel, status, created_at, updated_at
)
SELECT
    user_id,
    phone_number,
    'NOT_INFORMED',
    NULL,
    'NONE',
    'PENDING',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (
    VALUES
        ('admin', '+5531991001001'),
        ('boa_produtor', '+5531991001002'),
        ('santa_produtor', '+5531991001003'),
        ('miguel_produtor', '+5531991001004'),
        ('vale_produtor', '+5531991001005'),
        ('vale_colaborador', '+5531991001006'),
        ('contador', '+5531991001007')
) AS contact_seed(user_key, phone_number)
JOIN demo_users user_seed ON user_seed.user_key = contact_seed.user_key;

CREATE TEMP TABLE demo_farms (
    farm_key TEXT PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    producer_user_key TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO farms (
    name, document, city, state, total_area, production_type, status, created_at, updated_at
)
VALUES
    ('Fazenda Boa Esperança', NULL, 'Patrocínio', 'MG', 68.00, 'AGRICULTURE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Fazenda Santa Helena', NULL, 'Rio Verde', 'GO', 245.00, 'AGRICULTURE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Fazenda São Miguel', NULL, 'Unaí', 'MG', 118.00, 'AGRICULTURE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Fazenda Vale Verde', NULL, 'Campo Verde', 'MT', 430.00, 'AGRICULTURE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO demo_farms (farm_key, farm_id, producer_user_key)
SELECT seed.farm_key, farm.id, seed.producer_user_key
FROM (
    VALUES
        ('boa', 'Fazenda Boa Esperança', 'boa_produtor'),
        ('santa', 'Fazenda Santa Helena', 'santa_produtor'),
        ('miguel', 'Fazenda São Miguel', 'miguel_produtor'),
        ('vale', 'Fazenda Vale Verde', 'vale_produtor')
) AS seed(farm_key, farm_name, producer_user_key)
JOIN farms farm ON farm.name = seed.farm_name;

INSERT INTO farm_users (farm_id, user_id, role, created_at, updated_at)
SELECT farm_seed.farm_id, user_seed.user_id, 'PRODUCER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM demo_farms farm_seed
JOIN demo_users user_seed ON user_seed.user_key = farm_seed.producer_user_key;

INSERT INTO farm_users (farm_id, user_id, role, created_at, updated_at)
SELECT farm_seed.farm_id, user_seed.user_id, 'ACCOUNTANT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM demo_farms farm_seed
JOIN demo_users user_seed ON user_seed.user_key = 'contador';

INSERT INTO farm_users (farm_id, user_id, role, created_at, updated_at)
SELECT farm_seed.farm_id, user_seed.user_id, 'EMPLOYEE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM demo_farms farm_seed
JOIN demo_users user_seed ON user_seed.user_key = 'vale_colaborador'
WHERE farm_seed.farm_key = 'vale';

CREATE TEMP TABLE demo_categories (
    farm_key TEXT NOT NULL,
    category_name TEXT NOT NULL,
    transaction_type TEXT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (farm_key, category_name, transaction_type)
) ON COMMIT DROP;

INSERT INTO financial_categories (
    name, type, color, icon, farm_id, status, created_at, updated_at
)
SELECT
    category.name,
    category.transaction_type,
    category.color,
    category.icon,
    farm_seed.farm_id,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM demo_farms farm_seed
CROSS JOIN (
    VALUES
        ('Fertilizantes', 'EXPENSE', '#B45309', 'sprout'),
        ('Sementes e mudas', 'EXPENSE', '#15803D', 'leaf'),
        ('Defensivos agrícolas', 'EXPENSE', '#7C3AED', 'shield-check'),
        ('Combustíveis', 'EXPENSE', '#DC2626', 'fuel'),
        ('Mão de obra', 'EXPENSE', '#2563EB', 'users'),
        ('Manutenção de máquinas', 'EXPENSE', '#475569', 'wrench'),
        ('Serviços terceirizados', 'EXPENSE', '#0F766E', 'briefcase-business'),
        ('Energia e irrigação', 'EXPENSE', '#CA8A04', 'zap'),
        ('Transporte', 'EXPENSE', '#0369A1', 'truck'),
        ('Despesas operacionais', 'EXPENSE', '#6B7280', 'receipt-text'),
        ('Venda da produção', 'INCOME', '#16A34A', 'circle-dollar-sign'),
        ('Venda de lote complementar', 'INCOME', '#0E7490', 'hand-coins')
) AS category(name, transaction_type, color, icon);

INSERT INTO demo_categories (farm_key, category_name, transaction_type, category_id)
SELECT farm_seed.farm_key, category.name, category.type, category.id
FROM financial_categories category
JOIN demo_farms farm_seed ON farm_seed.farm_id = category.farm_id;

CREATE TEMP TABLE demo_activities (
    farm_key TEXT NOT NULL,
    activity_name TEXT NOT NULL,
    activity_id BIGINT NOT NULL,
    PRIMARY KEY (farm_key, activity_name)
) ON COMMIT DROP;

INSERT INTO production_activities (farm_id, name, description, status, created_at, updated_at)
SELECT farm_seed.farm_id, activity.name, activity.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM demo_farms farm_seed
JOIN (
    VALUES
        ('boa', 'Café', 'Cultivo de café arábica com foco em qualidade e estabilidade produtiva.'),
        ('santa', 'Soja', 'Produção de soja em área de sequeiro com evolução gradual de escala.'),
        ('santa', 'Milho Safrinha', 'Milho de segunda safra integrado ao calendário da soja.'),
        ('miguel', 'Feijão', 'Produção de feijão com atenção a custos de manejo e qualidade.'),
        ('vale', 'Feijão', 'Feijão em talhões selecionados para diversificação da receita.'),
        ('vale', 'Milho Safrinha', 'Milho de segunda safra como complemento da operação diversificada.')
) AS activity(farm_key, name, description) ON activity.farm_key = farm_seed.farm_key;

INSERT INTO demo_activities (farm_key, activity_name, activity_id)
SELECT farm_seed.farm_key, activity.name, activity.id
FROM production_activities activity
JOIN demo_farms farm_seed ON farm_seed.farm_id = activity.farm_id;

CREATE TEMP TABLE demo_seasons (
    season_key TEXT PRIMARY KEY,
    farm_key TEXT NOT NULL,
    activity_name TEXT NOT NULL,
    season_name TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    area_hectares NUMERIC(12, 2) NOT NULL,
    season_status TEXT NOT NULL,
    variance_group INTEGER NOT NULL,
    harvest_season_id BIGINT
) ON COMMIT DROP;

INSERT INTO demo_seasons (
    season_key, farm_key, activity_name, season_name, start_date, end_date,
    area_hectares, season_status, variance_group
)
VALUES
    ('boa_2020', 'boa', 'Café', 'Café 2020/2021', DATE '2020-01-10', DATE '2020-12-20', 64.00, 'FINISHED', 1),
    ('boa_2021', 'boa', 'Café', 'Café 2021/2022', DATE '2021-01-12', DATE '2021-12-18', 65.00, 'FINISHED', 2),
    ('boa_2022', 'boa', 'Café', 'Café 2022/2023', DATE '2022-01-11', DATE '2022-12-19', 66.00, 'FINISHED', 3),
    ('boa_2023', 'boa', 'Café', 'Café 2023/2024', DATE '2023-01-09', DATE '2023-12-20', 67.00, 'FINISHED', 4),
    ('boa_2024', 'boa', 'Café', 'Café 2024/2025', DATE '2024-01-10', DATE '2024-12-18', 68.00, 'FINISHED', 5),
    ('boa_2025', 'boa', 'Café', 'Café 2025/2026', DATE '2025-01-13', DATE '2025-12-19', 68.00, 'FINISHED', 6),
    ('boa_2026', 'boa', 'Café', 'Café 2026/2027', DATE '2026-01-12', DATE '2026-12-18', 68.00, 'IN_PROGRESS', 7),
    ('boa_2027', 'boa', 'Café', 'Café 2027/2028', DATE '2027-01-11', DATE '2027-12-18', 68.00, 'PLANNED', 8),
    ('boa_2028', 'boa', 'Café', 'Café 2028/2029', DATE '2028-01-10', DATE '2028-12-19', 68.00, 'PLANNED', 9),
    ('santa_soja_2020', 'santa', 'Soja', 'Soja 2020/2021', DATE '2020-09-15', DATE '2021-03-12', 190.00, 'FINISHED', 1),
    ('santa_soja_2021', 'santa', 'Soja', 'Soja 2021/2022', DATE '2021-09-14', DATE '2022-03-14', 198.00, 'FINISHED', 2),
    ('santa_soja_2022', 'santa', 'Soja', 'Soja 2022/2023', DATE '2022-09-16', DATE '2023-03-13', 205.00, 'FINISHED', 3),
    ('santa_soja_2023', 'santa', 'Soja', 'Soja 2023/2024', DATE '2023-09-15', DATE '2024-03-11', 212.00, 'FINISHED', 4),
    ('santa_soja_2024', 'santa', 'Soja', 'Soja 2024/2025', DATE '2024-09-13', DATE '2025-03-12', 220.00, 'FINISHED', 5),
    ('santa_soja_2025', 'santa', 'Soja', 'Soja 2025/2026', DATE '2025-09-15', DATE '2026-03-13', 228.00, 'FINISHED', 6),
    ('santa_soja_2026', 'santa', 'Soja', 'Soja 2026/2027', DATE '2026-08-20', DATE '2027-03-12', 235.00, 'IN_PROGRESS', 7),
    ('santa_soja_2027', 'santa', 'Soja', 'Soja 2027/2028', DATE '2027-09-15', DATE '2028-03-12', 245.00, 'PLANNED', 8),
    ('santa_milho_2023', 'santa', 'Milho Safrinha', 'Milho Safrinha 2023', DATE '2023-03-20', DATE '2023-08-25', 175.00, 'FINISHED', 2),
    ('santa_milho_2024', 'santa', 'Milho Safrinha', 'Milho Safrinha 2024', DATE '2024-03-18', DATE '2024-08-23', 185.00, 'FINISHED', 4),
    ('santa_milho_2025', 'santa', 'Milho Safrinha', 'Milho Safrinha 2025', DATE '2025-03-19', DATE '2025-08-25', 195.00, 'FINISHED', 6),
    ('santa_milho_2026', 'santa', 'Milho Safrinha', 'Milho Safrinha 2026', DATE '2026-03-18', DATE '2026-10-20', 205.00, 'IN_PROGRESS', 8),
    ('miguel_2020', 'miguel', 'Feijão', 'Feijão 2020', DATE '2020-02-10', DATE '2020-07-20', 96.00, 'FINISHED', 2),
    ('miguel_2021', 'miguel', 'Feijão', 'Feijão 2021', DATE '2021-02-09', DATE '2021-07-21', 98.00, 'FINISHED', 4),
    ('miguel_2022', 'miguel', 'Feijão', 'Feijão 2022', DATE '2022-02-11', DATE '2022-07-22', 102.00, 'FINISHED', 6),
    ('miguel_2023', 'miguel', 'Feijão', 'Feijão 2023', DATE '2023-02-10', DATE '2023-07-21', 104.00, 'FINISHED', 1),
    ('miguel_2024', 'miguel', 'Feijão', 'Feijão 2024', DATE '2024-02-08', DATE '2024-07-19', 108.00, 'FINISHED', 3),
    ('miguel_2025', 'miguel', 'Feijão', 'Feijão 2025', DATE '2025-02-10', DATE '2025-07-22', 110.00, 'FINISHED', 5),
    ('miguel_2026', 'miguel', 'Feijão', 'Feijão 2026', DATE '2026-02-09', DATE '2026-10-18', 112.00, 'IN_PROGRESS', 7),
    ('miguel_2027', 'miguel', 'Feijão', 'Feijão 2027', DATE '2027-02-10', DATE '2027-07-22', 114.00, 'PLANNED', 9),
    ('miguel_2028', 'miguel', 'Feijão', 'Feijão 2028', DATE '2028-02-09', DATE '2028-07-21', 118.00, 'PLANNED', 2),
    ('vale_feijao_2020', 'vale', 'Feijão', 'Feijão 2020', DATE '2020-02-12', DATE '2020-07-24', 145.00, 'FINISHED', 1),
    ('vale_feijao_2021', 'vale', 'Feijão', 'Feijão 2021', DATE '2021-02-11', DATE '2021-07-23', 150.00, 'FINISHED', 2),
    ('vale_feijao_2022', 'vale', 'Feijão', 'Feijão 2022', DATE '2022-02-10', DATE '2022-07-22', 158.00, 'FINISHED', 3),
    ('vale_feijao_2023', 'vale', 'Feijão', 'Feijão 2023', DATE '2023-02-13', DATE '2023-07-24', 165.00, 'FINISHED', 4),
    ('vale_feijao_2024', 'vale', 'Feijão', 'Feijão 2024', DATE '2024-02-12', DATE '2024-07-23', 172.00, 'FINISHED', 5),
    ('vale_feijao_2025', 'vale', 'Feijão', 'Feijão 2025', DATE '2025-02-11', DATE '2025-07-22', 178.00, 'FINISHED', 6),
    ('vale_feijao_2026', 'vale', 'Feijão', 'Feijão 2026', DATE '2026-02-12', DATE '2026-10-18', 182.00, 'IN_PROGRESS', 7),
    ('vale_feijao_2027', 'vale', 'Feijão', 'Feijão 2027', DATE '2027-02-11', DATE '2027-07-23', 185.00, 'PLANNED', 8),
    ('vale_feijao_2028', 'vale', 'Feijão', 'Feijão 2028', DATE '2028-02-10', DATE '2028-07-22', 190.00, 'PLANNED', 9),
    ('vale_milho_2026', 'vale', 'Milho Safrinha', 'Milho Safrinha 2026', DATE '2026-03-17', DATE '2026-10-25', 160.00, 'IN_PROGRESS', 4);

INSERT INTO harvest_seasons (
    farm_id, production_activity_id, name, description, start_date, end_date,
    expected_revenue, expected_cost, area_hectares, status, created_at, updated_at
)
SELECT
    farm_seed.farm_id,
    activity.activity_id,
    season.season_name,
    'Planejamento financeiro detalhado para ' || lower(season.season_name) || '.',
    season.start_date,
    season.end_date,
    0,
    0,
    season.area_hectares,
    season.season_status,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM demo_seasons season
JOIN demo_farms farm_seed ON farm_seed.farm_key = season.farm_key
JOIN demo_activities activity
    ON activity.farm_key = season.farm_key
    AND activity.activity_name = season.activity_name;

UPDATE demo_seasons season
SET harvest_season_id = harvest.id
FROM harvest_seasons harvest
JOIN demo_farms farm_seed ON farm_seed.farm_id = harvest.farm_id
WHERE harvest.name = season.season_name
  AND farm_seed.farm_key = season.farm_key;

CREATE TEMP TABLE demo_plans AS
SELECT
    season.harvest_season_id,
    season.farm_key,
    season.variance_group,
    season.start_date,
    season.season_status,
    CASE season.activity_name
        WHEN 'Café' THEN 11200
        WHEN 'Soja' THEN 3450
        WHEN 'Milho Safrinha' THEN 3050
        WHEN 'Feijão' THEN 4350
    END * season.area_hectares * (1 + ((EXTRACT(YEAR FROM season.start_date) - 2020) * 0.052)) AS planned_cost,
    CASE season.activity_name
        WHEN 'Café' THEN 19400
        WHEN 'Soja' THEN 6400
        WHEN 'Milho Safrinha' THEN 5450
        WHEN 'Feijão' THEN 7450
    END * season.area_hectares * (1 + ((EXTRACT(YEAR FROM season.start_date) - 2020) * 0.038)
        + CASE season.variance_group % 4 WHEN 0 THEN -0.055 WHEN 1 THEN 0.035 WHEN 2 THEN 0.075 ELSE -0.020 END) AS planned_revenue
FROM demo_seasons season;

INSERT INTO harvest_season_budget_items (
    harvest_season_id, category_id, type, description, planned_amount, created_at, updated_at
)
SELECT
    plan.harvest_season_id,
    category.category_id,
    item.transaction_type,
    item.description,
    ROUND((CASE item.transaction_type WHEN 'EXPENSE' THEN plan.planned_cost ELSE plan.planned_revenue END) * item.share, 2),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM demo_plans plan
JOIN (
    VALUES
        ('Fertilizantes', 'EXPENSE', 'Adubação de base', 0.13),
        ('Fertilizantes', 'EXPENSE', 'Adubação de cobertura', 0.09),
        ('Sementes e mudas', 'EXPENSE', 'Sementes, mudas e tratamento inicial', 0.14),
        ('Defensivos agrícolas', 'EXPENSE', 'Manejo preventivo e aplicações', 0.16),
        ('Combustíveis', 'EXPENSE', 'Diesel para preparo e plantio', 0.05),
        ('Combustíveis', 'EXPENSE', 'Diesel para aplicações e colheita', 0.05),
        ('Mão de obra', 'EXPENSE', 'Equipe de plantio e manejo', 0.09),
        ('Mão de obra', 'EXPENSE', 'Equipe de colheita e pós-colheita', 0.08),
        ('Manutenção de máquinas', 'EXPENSE', 'Manutenção preventiva do maquinário', 0.08),
        ('Serviços terceirizados', 'EXPENSE', 'Serviços agrícolas especializados', 0.05),
        ('Energia e irrigação', 'EXPENSE', 'Energia e irrigação da safra', 0.03),
        ('Transporte', 'EXPENSE', 'Frete e transporte da produção', 0.05),
        ('Venda da produção', 'INCOME', 'Venda principal da produção', 0.82),
        ('Venda de lote complementar', 'INCOME', 'Venda de lote complementar', 0.18)
) AS item(category_name, transaction_type, description, share) ON true
JOIN demo_categories category
    ON category.farm_key = plan.farm_key
    AND category.category_name = item.category_name
    AND category.transaction_type = item.transaction_type;

UPDATE harvest_seasons season
SET
    expected_cost = totals.planned_cost,
    expected_revenue = totals.planned_revenue,
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT
        harvest_season_id,
        COALESCE(SUM(planned_amount) FILTER (WHERE type = 'EXPENSE'), 0) AS planned_cost,
        COALESCE(SUM(planned_amount) FILTER (WHERE type = 'INCOME'), 0) AS planned_revenue
    FROM harvest_season_budget_items
    GROUP BY harvest_season_id
) totals
WHERE totals.harvest_season_id = season.id;

CREATE TEMP TABLE demo_planned_categories AS
SELECT
    season.season_key,
    season.harvest_season_id,
    season.farm_key,
    season.start_date,
    season.season_status,
    season.variance_group,
    item.type,
    category.name AS category_name,
    category.id AS category_id,
    SUM(item.planned_amount) AS planned_amount
FROM demo_seasons season
JOIN harvest_season_budget_items item ON item.harvest_season_id = season.harvest_season_id
JOIN financial_categories category ON category.id = item.category_id
GROUP BY
    season.season_key, season.harvest_season_id, season.farm_key, season.start_date,
    season.season_status, season.variance_group, item.type, category.name, category.id;

-- Safras concluídas: receitas e despesas realizadas em datas sazonais, com variações previsíveis.
INSERT INTO financial_transactions (
    description, amount, type, status, payment_method, transaction_date, due_date, paid_at,
    notes, farm_id, category_id, harvest_season_id, created_by_user_id, updated_by_user_id,
    record_status, created_at, updated_at
)
SELECT
    CASE category.category_name
        WHEN 'Fertilizantes' THEN 'Compra de fertilizantes para adubação'
        WHEN 'Sementes e mudas' THEN 'Aquisição de sementes e mudas da safra'
        WHEN 'Defensivos agrícolas' THEN 'Defensivos para manejo da lavoura'
        WHEN 'Combustíveis' THEN 'Diesel para operações agrícolas'
        WHEN 'Mão de obra' THEN 'Equipe de campo e colheita'
        WHEN 'Manutenção de máquinas' THEN 'Manutenção preventiva do maquinário'
        WHEN 'Serviços terceirizados' THEN 'Serviço agrícola especializado'
        WHEN 'Energia e irrigação' THEN 'Energia e irrigação do período'
        WHEN 'Transporte' THEN 'Frete da produção comercializada'
        WHEN 'Venda da produção' THEN 'Venda principal da produção colhida'
        ELSE 'Venda de lote complementar da safra'
    END,
    ROUND(category.planned_amount * CASE
        WHEN category.type = 'EXPENSE' THEN CASE category.variance_group % 4
            WHEN 0 THEN 1.11 WHEN 1 THEN 0.94 WHEN 2 THEN 1.04 ELSE 0.98 END
        ELSE CASE category.variance_group % 4
            WHEN 0 THEN 0.93 WHEN 1 THEN 1.09 WHEN 2 THEN 1.04 ELSE 0.97 END
    END, 2),
    category.type,
    'PAID',
    CASE WHEN category.type = 'INCOME' THEN 'BANK_TRANSFER' ELSE 'PIX' END,
    CASE category.category_name
        WHEN 'Sementes e mudas' THEN category.start_date + 14
        WHEN 'Fertilizantes' THEN category.start_date + 42
        WHEN 'Defensivos agrícolas' THEN category.start_date + 77
        WHEN 'Combustíveis' THEN category.start_date + 95
        WHEN 'Mão de obra' THEN category.start_date + 120
        WHEN 'Manutenção de máquinas' THEN category.start_date + 55
        WHEN 'Serviços terceirizados' THEN category.start_date + 105
        WHEN 'Energia e irrigação' THEN category.start_date + 132
        WHEN 'Transporte' THEN category.start_date + 165
        WHEN 'Venda da produção' THEN category.start_date + 175
        ELSE category.start_date + 190
    END,
    NULL,
    CASE category.category_name
        WHEN 'Sementes e mudas' THEN category.start_date + 16
        WHEN 'Fertilizantes' THEN category.start_date + 45
        WHEN 'Defensivos agrícolas' THEN category.start_date + 80
        WHEN 'Combustíveis' THEN category.start_date + 97
        WHEN 'Mão de obra' THEN category.start_date + 123
        WHEN 'Manutenção de máquinas' THEN category.start_date + 57
        WHEN 'Serviços terceirizados' THEN category.start_date + 108
        WHEN 'Energia e irrigação' THEN category.start_date + 134
        WHEN 'Transporte' THEN category.start_date + 168
        WHEN 'Venda da produção' THEN category.start_date + 178
        ELSE category.start_date + 193
    END,
    'Movimentação registrada pelo sistema web para composição do histórico da safra.',
    farm_seed.farm_id,
    category.category_id,
    category.harvest_season_id,
    producer.user_id,
    NULL,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM demo_planned_categories category
JOIN demo_farms farm_seed ON farm_seed.farm_key = category.farm_key
JOIN demo_users producer ON producer.user_key = farm_seed.producer_user_key
WHERE category.season_status = 'FINISHED';

-- Despesas não previstas em algumas safras históricas para demonstrar o estado "Não planejado".
INSERT INTO financial_transactions (
    description, amount, type, status, payment_method, transaction_date, due_date, paid_at,
    notes, farm_id, category_id, harvest_season_id, created_by_user_id, updated_by_user_id,
    record_status, created_at, updated_at
)
SELECT
    'Reparo corretivo após desgaste operacional',
    ROUND(plan.planned_cost * 0.027, 2),
    'EXPENSE', 'PAID', 'BANK_TRANSFER',
    season.start_date + 145, NULL, season.start_date + 147,
    'Despesa não prevista no orçamento inicial da safra.',
    farm_seed.farm_id, category.category_id, season.harvest_season_id, producer.user_id, NULL,
    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM demo_seasons season
JOIN demo_plans plan ON plan.harvest_season_id = season.harvest_season_id
JOIN demo_farms farm_seed ON farm_seed.farm_key = season.farm_key
JOIN demo_users producer ON producer.user_key = farm_seed.producer_user_key
JOIN demo_categories category
    ON category.farm_key = season.farm_key
    AND category.category_name = 'Despesas operacionais'
    AND category.transaction_type = 'EXPENSE'
WHERE season.season_status = 'FINISHED'
  AND season.variance_group IN (3, 6);

-- Safras de 2026: execução parcial realizada e compromissos futuros abertos.
INSERT INTO financial_transactions (
    description, amount, type, status, payment_method, transaction_date, due_date, paid_at,
    notes, farm_id, category_id, harvest_season_id, created_by_user_id, updated_by_user_id,
    record_status, created_at, updated_at
)
SELECT
    CASE category.category_name
        WHEN 'Fertilizantes' THEN 'Fertilizante aplicado no manejo atual'
        WHEN 'Sementes e mudas' THEN 'Sementes e mudas utilizadas no plantio'
        WHEN 'Defensivos agrícolas' THEN 'Aplicação de defensivos no manejo'
        WHEN 'Combustíveis' THEN 'Diesel para operações já realizadas'
        WHEN 'Mão de obra' THEN 'Equipe de campo do período'
        WHEN 'Manutenção de máquinas' THEN 'Revisão preventiva antes da colheita'
        WHEN 'Venda da produção' THEN 'Venda parcial da produção disponível'
        ELSE 'Recebimento parcial de lote comercializado'
    END,
    ROUND(category.planned_amount * CASE
        WHEN category.type = 'EXPENSE' THEN 0.47
        ELSE 0.34
    END, 2),
    category.type, 'PAID',
    CASE WHEN category.type = 'INCOME' THEN 'BANK_TRANSFER' ELSE 'PIX' END,
    category.start_date + CASE category.category_name
        WHEN 'Sementes e mudas' THEN 12
        WHEN 'Fertilizantes' THEN 38
        WHEN 'Defensivos agrícolas' THEN 74
        WHEN 'Combustíveis' THEN 92
        WHEN 'Mão de obra' THEN 118
        WHEN 'Manutenção de máquinas' THEN 54
        WHEN 'Venda da produção' THEN 172
        ELSE 187
    END,
    NULL,
    category.start_date + CASE category.category_name
        WHEN 'Sementes e mudas' THEN 14
        WHEN 'Fertilizantes' THEN 40
        WHEN 'Defensivos agrícolas' THEN 76
        WHEN 'Combustíveis' THEN 94
        WHEN 'Mão de obra' THEN 120
        WHEN 'Manutenção de máquinas' THEN 56
        WHEN 'Venda da produção' THEN 174
        ELSE 189
    END,
    'Execução parcial da safra em andamento.',
    farm_seed.farm_id, category.category_id, category.harvest_season_id, producer.user_id, NULL,
    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM demo_planned_categories category
JOIN demo_farms farm_seed ON farm_seed.farm_key = category.farm_key
JOIN demo_users producer ON producer.user_key = farm_seed.producer_user_key
WHERE category.season_status = 'IN_PROGRESS'
  AND category.category_name IN (
      'Sementes e mudas', 'Fertilizantes', 'Defensivos agrícolas', 'Combustíveis',
      'Mão de obra', 'Manutenção de máquinas', 'Venda da produção', 'Venda de lote complementar'
  )
  AND category.start_date + CASE category.category_name
      WHEN 'Sementes e mudas' THEN 14
      WHEN 'Fertilizantes' THEN 40
      WHEN 'Defensivos agrícolas' THEN 76
      WHEN 'Combustíveis' THEN 94
      WHEN 'Mão de obra' THEN 120
      WHEN 'Manutenção de máquinas' THEN 56
      WHEN 'Venda da produção' THEN 174
      ELSE 189
  END <= DATE '2026-09-07';

INSERT INTO financial_transactions (
    description, amount, type, status, payment_method, transaction_date, due_date, paid_at,
    notes, farm_id, category_id, harvest_season_id, created_by_user_id, updated_by_user_id,
    record_status, created_at, updated_at
)
SELECT
    CASE category.category_name
        WHEN 'Fertilizantes' THEN 'Compra programada de fertilizante para cobertura'
        WHEN 'Transporte' THEN 'Frete previsto para escoamento da produção'
        ELSE 'Recebimento previsto de venda contratada'
    END,
    ROUND(category.planned_amount * CASE WHEN category.type = 'INCOME' THEN 0.52 ELSE 0.45 END, 2),
    category.type, 'PENDING', 'BOLETO', DATE '2026-09-05',
    CASE category.category_name
        WHEN 'Fertilizantes' THEN DATE '2026-09-18'
        WHEN 'Transporte' THEN DATE '2026-10-05'
        ELSE DATE '2026-10-20'
    END,
    NULL,
    'Compromisso da safra em andamento para composição da agenda financeira.',
    farm_seed.farm_id, category.category_id, category.harvest_season_id, producer.user_id, NULL,
    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM demo_planned_categories category
JOIN demo_farms farm_seed ON farm_seed.farm_key = category.farm_key
JOIN demo_users producer ON producer.user_key = farm_seed.producer_user_key
WHERE category.season_status = 'IN_PROGRESS'
  AND category.category_name IN ('Fertilizantes', 'Transporte', 'Venda da produção');

-- Uma pendência vencida, útil para alertas e filtros sem distorcer o cenário geral.
INSERT INTO financial_transactions (
    description, amount, type, status, payment_method, transaction_date, due_date, paid_at,
    notes, farm_id, category_id, harvest_season_id, created_by_user_id, updated_by_user_id,
    record_status, created_at, updated_at
)
SELECT
    'Manutenção emergencial do pulverizador', 6840.00, 'EXPENSE', 'OVERDUE', 'BOLETO',
    DATE '2026-08-12', DATE '2026-08-28', NULL,
    'Conta em aberto para demonstrar alertas e agenda vencida.',
    farm_seed.farm_id, category.category_id, season.harvest_season_id, producer.user_id, NULL,
    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM demo_seasons season
JOIN demo_farms farm_seed ON farm_seed.farm_key = season.farm_key
JOIN demo_users producer ON producer.user_key = farm_seed.producer_user_key
JOIN demo_categories category
    ON category.farm_key = season.farm_key
    AND category.category_name = 'Manutenção de máquinas'
    AND category.transaction_type = 'EXPENSE'
WHERE season.season_key = 'miguel_2026';

-- Lançamentos gerais, sem safra, para a visualização financeira consolidada e filtro "Sem safra".
INSERT INTO financial_transactions (
    description, amount, type, status, payment_method, transaction_date, due_date, paid_at,
    notes, farm_id, category_id, harvest_season_id, created_by_user_id, updated_by_user_id,
    record_status, created_at, updated_at
)
SELECT
    'Manutenção da infraestrutura da fazenda',
    3180.00 + (ROW_NUMBER() OVER (ORDER BY farm_seed.farm_key) * 470.00),
    'EXPENSE', 'PAID', 'PIX', DATE '2026-08-05', NULL, DATE '2026-08-06',
    'Despesa geral da propriedade sem vínculo a uma safra específica.',
    farm_seed.farm_id, category.category_id, NULL,
    CASE WHEN farm_seed.farm_key = 'vale' THEN collaborator.user_id ELSE producer.user_id END,
    NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM demo_farms farm_seed
JOIN demo_users producer ON producer.user_key = farm_seed.producer_user_key
JOIN demo_users collaborator ON collaborator.user_key = 'vale_colaborador'
JOIN demo_categories category
    ON category.farm_key = farm_seed.farm_key
    AND category.category_name = 'Despesas operacionais'
    AND category.transaction_type = 'EXPENSE';

DO $$
DECLARE
    farm_count INTEGER;
    future_paid_count INTEGER;
    planned_movement_count INTEGER;
    mismatched_category_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO farm_count FROM farms;

    SELECT COUNT(*) INTO future_paid_count
    FROM financial_transactions
    WHERE status = 'PAID'
      AND (transaction_date > DATE '2026-09-07' OR paid_at > DATE '2026-09-07');

    SELECT COUNT(*) INTO planned_movement_count
    FROM financial_transactions transaction
    JOIN harvest_seasons season ON season.id = transaction.harvest_season_id
    WHERE season.status = 'PLANNED';

    SELECT COUNT(*) INTO mismatched_category_count
    FROM financial_transactions transaction
    JOIN financial_categories category ON category.id = transaction.category_id
    WHERE transaction.category_id IS NOT NULL
      AND (transaction.farm_id <> category.farm_id OR transaction.type::TEXT <> category.type::TEXT);

    IF farm_count <> 4
        OR future_paid_count <> 0
        OR planned_movement_count <> 0
        OR mismatched_category_count <> 0 THEN
        RAISE EXCEPTION
            'Seed validation failed: farms=%, future_paid=%, planned_movements=%, mismatched_categories=%',
            farm_count,
            future_paid_count, planned_movement_count, mismatched_category_count;
    END IF;
END $$;

COMMIT;

-- Validações pós-seed para conferência no terminal/psql.
SELECT 'users' AS validation, COUNT(*)::TEXT AS value FROM users
UNION ALL
SELECT 'users_by_type', string_agg(user_type || ':' || total, ', ' ORDER BY user_type)
FROM (SELECT user_type, COUNT(*)::TEXT AS total FROM users GROUP BY user_type) totals
UNION ALL
SELECT 'farms', COUNT(*)::TEXT FROM farms
UNION ALL
SELECT 'harvests_by_status', string_agg(status || ':' || total, ', ' ORDER BY status)
FROM (SELECT status, COUNT(*)::TEXT AS total FROM harvest_seasons GROUP BY status) totals
UNION ALL
SELECT 'harvest_period', MIN(start_date)::TEXT || ' to ' || MAX(COALESCE(end_date, start_date))::TEXT FROM harvest_seasons
UNION ALL
SELECT 'budget_items', COUNT(*)::TEXT FROM harvest_season_budget_items
UNION ALL
SELECT 'transactions', COUNT(*)::TEXT FROM financial_transactions
UNION ALL
SELECT 'commitments_open', COUNT(*)::TEXT FROM financial_transactions WHERE status IN ('PENDING', 'OVERDUE')
UNION ALL
SELECT 'future_paid_transactions', COUNT(*)::TEXT FROM financial_transactions WHERE status = 'PAID' AND (transaction_date > DATE '2026-09-07' OR paid_at > DATE '2026-09-07')
UNION ALL
SELECT 'planned_harvest_movements', COUNT(*)::TEXT
FROM financial_transactions transaction JOIN harvest_seasons season ON season.id = transaction.harvest_season_id
WHERE season.status = 'PLANNED';

SELECT farm.name, COUNT(season.id) AS harvest_seasons
FROM farms farm
LEFT JOIN harvest_seasons season ON season.farm_id = farm.id
GROUP BY farm.id, farm.name
ORDER BY farm.name;

SELECT farm.name, transaction.type, COUNT(*) AS movements, SUM(transaction.amount) AS total_amount
FROM financial_transactions transaction
JOIN farms farm ON farm.id = transaction.farm_id
GROUP BY farm.name, transaction.type
ORDER BY farm.name, transaction.type;
