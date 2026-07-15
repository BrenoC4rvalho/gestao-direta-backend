-- DADOS DE DEMONSTRAÇÃO
-- EXECUTAR SOMENTE EM AMBIENTE LOCAL/DEV
--
-- Credenciais de demonstração:
-- admin@gestaodireta.com      / Admin1234!
-- producer@gestaodireta.com   / User@1234
-- employee@gestaodireta.com   / User@1234
-- accountant@gestaodireta.com / User@1234

BEGIN;

-- Limpeza controlada dos dados operacionais. Não altera migrations nem estrutura.
DELETE FROM financial_transactions;
DELETE FROM harvest_seasons;
DELETE FROM production_activities;
DELETE FROM financial_categories;
DELETE FROM farm_users;
DELETE FROM farms;
DELETE FROM users
WHERE user_type <> 'ADMIN';

-- BCrypt gerado e validado com BCryptPasswordEncoder do Spring Security do projeto.
UPDATE users
SET name = 'Administrador',
    password = '$2a$10$R9lA5HOjxvJ8bF0C3C6aJON58MdcvPsrdY8Yb9GJS.y282928Z0P2',
    document = NULL,
    user_type = 'ADMIN',
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'admin@gestaodireta.com';

INSERT INTO users (
    name,
    email,
    password,
    document,
    user_type,
    status,
    created_at,
    updated_at
)
SELECT
    'Administrador',
    'admin@gestaodireta.com',
    '$2a$10$R9lA5HOjxvJ8bF0C3C6aJON58MdcvPsrdY8Yb9GJS.y282928Z0P2',
    NULL,
    'ADMIN',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'admin@gestaodireta.com'
);

INSERT INTO users (
    name,
    email,
    password,
    document,
    user_type,
    status,
    created_at,
    updated_at
)
VALUES
    (
        'Produtor Demonstração',
        'producer@gestaodireta.com',
        '$2a$10$PKJR/.rEs5ETQ1hH8afuje4eKdNo7VvB8XzXZPY6NM8Y9yTktjZoq',
        NULL,
        'USER',
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'Funcionário Demonstração',
        'employee@gestaodireta.com',
        '$2a$10$PKJR/.rEs5ETQ1hH8afuje4eKdNo7VvB8XzXZPY6NM8Y9yTktjZoq',
        NULL,
        'USER',
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'Contador Demonstração',
        'accountant@gestaodireta.com',
        '$2a$10$PKJR/.rEs5ETQ1hH8afuje4eKdNo7VvB8XzXZPY6NM8Y9yTktjZoq',
        NULL,
        'USER',
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

INSERT INTO farms (
    name,
    document,
    city,
    state,
    total_area,
    production_type,
    status,
    created_at,
    updated_at
)
VALUES
    (
        'Fazenda Boa Sorte',
        '12.345.678/0001-10',
        'Uberaba',
        'MG',
        480.00,
        'AGRICULTURE',
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'Fazenda Santa Clara',
        '98.765.432/0001-55',
        'Patrocínio',
        'MG',
        325.00,
        'MIXED',
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

INSERT INTO farm_users (farm_id, user_id, role, created_at, updated_at)
SELECT
    farm.id,
    app_user.id,
    links.role,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (
    VALUES
        ('producer@gestaodireta.com', 'PRODUCER'),
        ('employee@gestaodireta.com', 'EMPLOYEE'),
        ('accountant@gestaodireta.com', 'ACCOUNTANT')
) AS links(email, role)
JOIN users app_user ON app_user.email = links.email
CROSS JOIN farms farm
WHERE farm.name IN ('Fazenda Boa Sorte', 'Fazenda Santa Clara');

INSERT INTO financial_categories (
    farm_id,
    name,
    type,
    color,
    icon,
    status,
    created_at,
    updated_at
)
SELECT
    farm.id,
    categories.name,
    categories.type,
    categories.color,
    categories.icon,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM farms farm
CROSS JOIN (
    VALUES
        ('Insumos', 'EXPENSE', '#C0392B', 'package'),
        ('Fertilizantes', 'EXPENSE', '#7D6608', 'sprout'),
        ('Defensivos agrícolas', 'EXPENSE', '#A04000', 'shield'),
        ('Sementes', 'EXPENSE', '#196F3D', 'wheat'),
        ('Mão de obra', 'EXPENSE', '#6C3483', 'users'),
        ('Combustível', 'EXPENSE', '#1F618D', 'fuel'),
        ('Manutenção de máquinas', 'EXPENSE', '#566573', 'wrench'),
        ('Energia elétrica', 'EXPENSE', '#D68910', 'zap'),
        ('Transporte', 'EXPENSE', '#2471A3', 'truck'),
        ('Impostos e taxas', 'EXPENSE', '#922B21', 'receipt'),
        ('Arrendamento', 'EXPENSE', '#784212', 'landmark'),
        ('Outras despesas', 'EXPENSE', '#5D6D7E', 'circle-minus'),
        ('Venda de produção', 'INCOME', '#1E8449', 'trending-up'),
        ('Venda de animais', 'INCOME', '#2874A6', 'beef'),
        ('Prestação de serviços', 'INCOME', '#7D3C98', 'briefcase'),
        ('Subsídios', 'INCOME', '#B7950B', 'hand-coins'),
        ('Outras receitas', 'INCOME', '#148F77', 'circle-plus')
) AS categories(name, type, color, icon)
WHERE farm.name IN ('Fazenda Boa Sorte', 'Fazenda Santa Clara');

INSERT INTO production_activities (
    farm_id,
    name,
    description,
    status,
    created_at,
    updated_at
)
SELECT
    farm.id,
    activities.name,
    activities.description,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM farms farm
JOIN (
    VALUES
        ('Fazenda Boa Sorte', 'Soja', 'Cultivo de soja em grãos.'),
        ('Fazenda Boa Sorte', 'Milho', 'Cultivo de milho em grãos.'),
        ('Fazenda Boa Sorte', 'Tomate', 'Produção de tomate para mercado in natura.'),
        ('Fazenda Boa Sorte', 'Café', 'Cultivo de café arábica.'),
        ('Fazenda Santa Clara', 'Café', 'Cultivo de café arábica.'),
        ('Fazenda Santa Clara', 'Feijão', 'Cultivo de feijão carioca.'),
        ('Fazenda Santa Clara', 'Laranja', 'Produção de laranja para mesa e indústria.'),
        ('Fazenda Santa Clara', 'Leite', 'Produção leiteira.' )
) AS activities(farm_name, name, description) ON activities.farm_name = farm.name;

INSERT INTO harvest_seasons (
    farm_id,
    production_activity_id,
    name,
    description,
    start_date,
    end_date,
    expected_revenue,
    expected_cost,
    area_hectares,
    status,
    created_at,
    updated_at
)
SELECT
    farm.id,
    activity.id,
    seasons.name,
    seasons.description,
    seasons.start_date,
    seasons.end_date,
    seasons.expected_revenue,
    seasons.expected_cost,
    seasons.area_hectares,
    seasons.status,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (
    VALUES
        (
            'Fazenda Boa Sorte', 'Soja', 'Soja 2023/2024',
            'Safra concluída com alta rentabilidade.',
            DATE '2023-09-01', DATE '2024-04-30', 345000.00, 225000.00, 180.00, 'FINISHED'
        ),
        (
            'Fazenda Boa Sorte', 'Milho', 'Milho 2024/2025',
            'Safra concluída com custos acima da receita realizada.',
            DATE '2024-08-01', DATE '2025-03-31', 165000.00, 202000.00, 130.00, 'FINISHED'
        ),
        (
            'Fazenda Boa Sorte', 'Tomate', 'Tomate 2025/2026',
            'Safra em andamento com custo realizado alto e receita contratada.',
            CURRENT_DATE - INTERVAL '120 days', CURRENT_DATE + INTERVAL '100 days',
            238000.00, 182000.00, 32.00, 'IN_PROGRESS'
        ),
        (
            'Fazenda Boa Sorte', 'Café', 'Café 2026/2027',
            'Safra planejada para renovação de área cafeeira.',
            CURRENT_DATE + INTERVAL '90 days', CURRENT_DATE + INTERVAL '450 days',
            195000.00, 128000.00, 48.00, 'PLANNED'
        ),
        (
            'Fazenda Santa Clara', 'Café', 'Café 2023/2024',
            'Safra concluída com lucro moderado.',
            DATE '2023-09-15', DATE '2024-07-15', 226000.00, 162000.00, 95.00, 'FINISHED'
        ),
        (
            'Fazenda Santa Clara', 'Feijão', 'Feijão 2024/2025',
            'Safra concluída com pequeno prejuízo.',
            DATE '2024-09-01', DATE '2025-02-28', 118000.00, 139000.00, 76.00, 'FINISHED'
        ),
        (
            'Fazenda Santa Clara', 'Laranja', 'Laranja 2025/2026',
            'Safra em andamento com receita futura contratada.',
            CURRENT_DATE - INTERVAL '150 days', CURRENT_DATE + INTERVAL '120 days',
            214000.00, 159000.00, 68.00, 'IN_PROGRESS'
        ),
        (
            'Fazenda Santa Clara', 'Leite', 'Leite 2026',
            'Planejamento de ampliação da produção leiteira.',
            CURRENT_DATE + INTERVAL '30 days', CURRENT_DATE + INTERVAL '365 days',
            156000.00, 101000.00, 40.00, 'PLANNED'
        )
) AS seasons(
    farm_name,
    activity_name,
    name,
    description,
    start_date,
    end_date,
    expected_revenue,
    expected_cost,
    area_hectares,
    status
)
JOIN farms farm ON farm.name = seasons.farm_name
JOIN production_activities activity
    ON activity.farm_id = farm.id
    AND activity.name = seasons.activity_name;

-- Dados de movimentação: description, amount, type, payment status, method,
-- transaction/due/paid dates, farm, category, safra e criador.
INSERT INTO financial_transactions (
    description,
    amount,
    type,
    status,
    payment_method,
    transaction_date,
    due_date,
    paid_at,
    notes,
    farm_id,
    category_id,
    harvest_season_id,
    created_by_user_id,
    updated_by_user_id,
    record_status,
    created_at,
    updated_at
)
SELECT
    data.description,
    data.amount,
    data.type,
    data.payment_status,
    data.payment_method,
    data.transaction_date,
    data.due_date,
    data.paid_at,
    data.notes,
    farm.id,
    category.id,
    season.id,
    creator.id,
    NULL,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (
    VALUES
        -- Fazenda Boa Sorte - Soja 2023/2024 (20 despesas, 10 receitas)
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Compra de sementes de soja', 18500.00, 'EXPENSE', 'PAID', 'PIX', DATE '2023-09-04', DATE '2023-09-10', DATE '2023-09-09', 'Sementes', 'producer@gestaodireta.com', 'Aquisição para plantio.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Compra de fertilizante de base', 22400.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2023-09-12', DATE '2023-09-25', DATE '2023-09-25', 'Fertilizantes', 'employee@gestaodireta.com', 'Adubação de plantio.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Aplicação de defensivos', 9600.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2023-10-03', DATE '2023-10-10', DATE '2023-10-10', 'Defensivos agrícolas', 'producer@gestaodireta.com', 'Controle preventivo de pragas.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Abastecimento do trator', 5700.00, 'EXPENSE', 'PAID', 'CREDIT_CARD', DATE '2023-10-08', DATE '2023-10-08', DATE '2023-10-08', 'Combustível', 'employee@gestaodireta.com', 'Diesel para preparo de solo.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Pagamento de mão de obra no plantio', 14800.00, 'EXPENSE', 'PAID', 'PIX', DATE '2023-10-15', DATE '2023-10-15', DATE '2023-10-15', 'Mão de obra', 'producer@gestaodireta.com', 'Equipe de plantio.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Manutenção da semeadora', 7300.00, 'EXPENSE', 'PAID', 'DEBIT_CARD', DATE '2023-10-20', DATE '2023-10-23', DATE '2023-10-23', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Troca de discos e revisão.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Pagamento de energia elétrica', 3100.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2023-11-05', DATE '2023-11-15', DATE '2023-11-15', 'Energia elétrica', 'admin@gestaodireta.com', 'Energia do galpão.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Controle de plantas daninhas', 8200.00, 'EXPENSE', 'PAID', 'PIX', DATE '2023-11-18', DATE '2023-11-22', DATE '2023-11-22', 'Defensivos agrícolas', 'producer@gestaodireta.com', 'Aplicação pós-emergente.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Seguro agrícola da lavoura', 6200.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2023-12-01', DATE '2023-12-05', DATE '2023-12-05', 'Impostos e taxas', 'admin@gestaodireta.com', 'Cobertura da safra.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Arrendamento da área de soja', 18200.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2023-12-10', DATE '2023-12-10', DATE '2023-12-10', 'Arrendamento', 'producer@gestaodireta.com', 'Parcela de arrendamento.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Revisão da colheitadeira', 9400.00, 'EXPENSE', 'PAID', 'CHECK', DATE '2024-01-12', DATE '2024-01-20', DATE '2024-01-20', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Preparação para colheita.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Compra de embalagens para amostras', 1800.00, 'EXPENSE', 'PAID', 'CASH', DATE '2024-01-25', DATE '2024-01-25', DATE '2024-01-25', 'Outras despesas', 'employee@gestaodireta.com', 'Material de classificação.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Frete interno de insumos', 2700.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-02-02', DATE '2024-02-05', DATE '2024-02-05', 'Transporte', 'producer@gestaodireta.com', 'Transporte até os talhões.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Imposto rural proporcional', 4300.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-02-12', DATE '2024-02-20', DATE '2024-02-20', 'Impostos e taxas', 'admin@gestaodireta.com', 'Tributo da área cultivada.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Serviço de pulverização', 6900.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2024-02-20', DATE '2024-02-25', DATE '2024-02-25', 'Outras despesas', 'producer@gestaodireta.com', 'Pulverização terceirizada.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Diárias da equipe de colheita', 11900.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-03-05', DATE '2024-03-05', DATE '2024-03-05', 'Mão de obra', 'employee@gestaodireta.com', 'Equipe temporária.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Frete da produção de soja', 12700.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2024-03-15', DATE '2024-03-20', DATE '2024-03-20', 'Transporte', 'producer@gestaodireta.com', 'Entrega na cooperativa.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Secagem e armazenagem de soja', 8600.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-03-25', DATE '2024-04-05', DATE '2024-04-05', 'Outras despesas', 'admin@gestaodireta.com', 'Serviço da cooperativa.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Compra de peças para carreta', 3500.00, 'EXPENSE', 'PAID', 'DEBIT_CARD', DATE '2024-04-02', DATE '2024-04-02', DATE '2024-04-02', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Reparo emergencial.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Análise de solo complementar', 1600.00, 'EXPENSE', 'CANCELED', 'OTHER', DATE '2024-04-10', DATE '2024-04-15', NULL, 'Outras despesas', 'admin@gestaodireta.com', 'Serviço cancelado pelo laboratório.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Venda de soja para cooperativa', 52000.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-03-08', DATE '2024-03-15', DATE '2024-03-15', 'Venda de produção', 'producer@gestaodireta.com', 'Primeiro lote comercializado.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Venda de soja lote 2', 46000.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-03-18', DATE '2024-03-25', DATE '2024-03-25', 'Venda de produção', 'producer@gestaodireta.com', 'Segundo lote comercializado.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Recebimento de cooperativa', 38500.00, 'INCOME', 'PAID', 'PIX', DATE '2024-03-29', DATE '2024-04-02', DATE '2024-04-02', 'Venda de produção', 'admin@gestaodireta.com', 'Liquidação de contrato.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Venda de soja lote 3', 41700.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-04-03', DATE '2024-04-10', DATE '2024-04-10', 'Venda de produção', 'producer@gestaodireta.com', 'Terceiro lote comercializado.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Venda de soja lote 4', 36500.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-04-10', DATE '2024-04-18', DATE '2024-04-18', 'Venda de produção', 'employee@gestaodireta.com', 'Venda para cerealista local.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Bonificação por qualidade da soja', 7800.00, 'INCOME', 'PAID', 'PIX', DATE '2024-04-15', DATE '2024-04-20', DATE '2024-04-20', 'Outras receitas', 'admin@gestaodireta.com', 'Prêmio de qualidade.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Venda de soja remanescente', 29800.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-04-21', DATE '2024-04-28', DATE '2024-04-28', 'Venda de produção', 'producer@gestaodireta.com', 'Fechamento da comercialização.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Crédito de ICMS da soja', 4300.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-04-22', DATE '2024-04-30', DATE '2024-04-30', 'Outras receitas', 'admin@gestaodireta.com', 'Compensação tributária.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Receita de serviço com trator', 5200.00, 'INCOME', 'PAID', 'PIX', DATE '2024-04-24', DATE '2024-04-24', DATE '2024-04-24', 'Prestação de serviços', 'employee@gestaodireta.com', 'Apoio a propriedade vizinha.'),
        ('Fazenda Boa Sorte', 'Soja 2023/2024', 'Venda de palhada de soja', 3600.00, 'INCOME', 'CANCELED', 'OTHER', DATE '2024-04-28', DATE '2024-05-02', NULL, 'Outras receitas', 'producer@gestaodireta.com', 'Negociação cancelada.'),

        -- Fazenda Boa Sorte - Milho 2024/2025 (20 despesas, 4 receitas)
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Compra de sementes de milho', 24800.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-08-03', DATE '2024-08-10', DATE '2024-08-10', 'Sementes', 'producer@gestaodireta.com', 'Híbrido para segunda safra.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Compra de fertilizante nitrogenado', 29100.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-08-12', DATE '2024-08-25', DATE '2024-08-25', 'Fertilizantes', 'employee@gestaodireta.com', 'Cobertura nitrogenada.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Aplicação de herbicida no milho', 7800.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2024-08-20', DATE '2024-08-25', DATE '2024-08-25', 'Defensivos agrícolas', 'producer@gestaodireta.com', 'Controle de invasoras.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Abastecimento do trator no plantio', 6100.00, 'EXPENSE', 'PAID', 'CREDIT_CARD', DATE '2024-09-02', DATE '2024-09-02', DATE '2024-09-02', 'Combustível', 'employee@gestaodireta.com', 'Diesel para plantio.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Mão de obra de plantio do milho', 12400.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-09-08', DATE '2024-09-08', DATE '2024-09-08', 'Mão de obra', 'producer@gestaodireta.com', 'Equipe de plantio.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Manutenção do pulverizador', 6800.00, 'EXPENSE', 'PAID', 'DEBIT_CARD', DATE '2024-09-18', DATE '2024-09-20', DATE '2024-09-20', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Troca de bicos.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Pagamento de energia da irrigação', 4700.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-10-05', DATE '2024-10-15', DATE '2024-10-15', 'Energia elétrica', 'admin@gestaodireta.com', 'Energia para bombeamento.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Aplicação de inseticida', 8900.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-10-15', DATE '2024-10-18', DATE '2024-10-18', 'Defensivos agrícolas', 'producer@gestaodireta.com', 'Controle de lagartas.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Arrendamento da área de milho', 15600.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2024-10-30', DATE '2024-10-30', DATE '2024-10-30', 'Arrendamento', 'producer@gestaodireta.com', 'Parcela de arrendamento.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Imposto rural da área de milho', 3600.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-11-10', DATE '2024-11-20', DATE '2024-11-20', 'Impostos e taxas', 'admin@gestaodireta.com', 'Tributo proporcional.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Frete de fertilizante', 3900.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-11-18', DATE '2024-11-20', DATE '2024-11-20', 'Transporte', 'employee@gestaodireta.com', 'Entrega de insumos.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Revisão da colheitadeira de milho', 11200.00, 'EXPENSE', 'PAID', 'CHECK', DATE '2024-12-03', DATE '2024-12-10', DATE '2024-12-10', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Revisão pré-colheita.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Diárias para capina manual', 7800.00, 'EXPENSE', 'PAID', 'CASH', DATE '2024-12-18', DATE '2024-12-18', DATE '2024-12-18', 'Mão de obra', 'producer@gestaodireta.com', 'Controle manual de invasoras.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Compra de embalagens para amostras', 1300.00, 'EXPENSE', 'PAID', 'DEBIT_CARD', DATE '2025-01-08', DATE '2025-01-08', DATE '2025-01-08', 'Outras despesas', 'employee@gestaodireta.com', 'Material de classificação.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Frete da produção de milho', 10800.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2025-02-02', DATE '2025-02-10', DATE '2025-02-10', 'Transporte', 'producer@gestaodireta.com', 'Entrega ao armazém.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Secagem de grãos de milho', 6500.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2025-02-10', DATE '2025-02-18', DATE '2025-02-18', 'Outras despesas', 'admin@gestaodireta.com', 'Desconto de umidade.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Manutenção da carreta graneleira', 5100.00, 'EXPENSE', 'PAID', 'PIX', DATE '2025-02-18', DATE '2025-02-20', DATE '2025-02-20', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Troca de rolamentos.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Taxa de classificação de grãos', 2200.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2025-02-25', DATE '2025-03-05', DATE '2025-03-05', 'Impostos e taxas', 'admin@gestaodireta.com', 'Classificação comercial.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Seguro da lavoura de milho', 4800.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2025-03-02', DATE '2025-03-10', DATE '2025-03-10', 'Impostos e taxas', 'producer@gestaodireta.com', 'Cobertura contratada.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Ajuste de aplicação agrícola', 2500.00, 'EXPENSE', 'PAID', 'OTHER', DATE '2025-03-10', DATE '2025-03-15', DATE '2025-03-15', 'Outras despesas', 'employee@gestaodireta.com', 'Calibração de equipamento.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Venda de milho para cooperativa', 44000.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2025-02-05', DATE '2025-02-12', DATE '2025-02-12', 'Venda de produção', 'producer@gestaodireta.com', 'Primeiro lote.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Venda de milho lote 2', 38000.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2025-02-20', DATE '2025-02-28', DATE '2025-02-28', 'Venda de produção', 'producer@gestaodireta.com', 'Segundo lote.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Recebimento de cooperativa pelo milho', 29000.00, 'INCOME', 'PAID', 'PIX', DATE '2025-03-01', DATE '2025-03-08', DATE '2025-03-08', 'Venda de produção', 'admin@gestaodireta.com', 'Liquidação de contrato.'),
        ('Fazenda Boa Sorte', 'Milho 2024/2025', 'Venda de palhada de milho', 18000.00, 'INCOME', 'PAID', 'CASH', DATE '2025-03-15', DATE '2025-03-15', DATE '2025-03-15', 'Outras receitas', 'employee@gestaodireta.com', 'Aproveitamento de resíduos.'),

        -- Fazenda Boa Sorte - Tomate 2025/2026 (20 passadas e 10 futuras)
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Compra de mudas de tomate', 18500.00, 'EXPENSE', 'PAID', 'PIX', CURRENT_DATE - INTERVAL '105 days', CURRENT_DATE - INTERVAL '100 days', CURRENT_DATE - INTERVAL '100 days', 'Sementes', 'producer@gestaodireta.com', 'Mudas para cultivo protegido.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Compra de fertilizante para tomate', 21400.00, 'EXPENSE', 'PAID', 'BOLETO', CURRENT_DATE - INTERVAL '95 days', CURRENT_DATE - INTERVAL '88 days', CURRENT_DATE - INTERVAL '88 days', 'Fertilizantes', 'employee@gestaodireta.com', 'Adubação inicial.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Instalação de irrigação por gotejo', 16200.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '88 days', CURRENT_DATE - INTERVAL '82 days', CURRENT_DATE - INTERVAL '82 days', 'Insumos', 'producer@gestaodireta.com', 'Ampliação da irrigação.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Aplicação de defensivos no tomate', 9800.00, 'EXPENSE', 'PAID', 'PIX', CURRENT_DATE - INTERVAL '72 days', CURRENT_DATE - INTERVAL '68 days', CURRENT_DATE - INTERVAL '68 days', 'Defensivos agrícolas', 'employee@gestaodireta.com', 'Controle fitossanitário.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Pagamento de mão de obra na colheita', 17600.00, 'EXPENSE', 'PAID', 'PIX', CURRENT_DATE - INTERVAL '55 days', CURRENT_DATE - INTERVAL '55 days', CURRENT_DATE - INTERVAL '55 days', 'Mão de obra', 'producer@gestaodireta.com', 'Colheita inicial.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Abastecimento do trator', 4200.00, 'EXPENSE', 'PAID', 'CREDIT_CARD', CURRENT_DATE - INTERVAL '45 days', CURRENT_DATE - INTERVAL '45 days', CURRENT_DATE - INTERVAL '45 days', 'Combustível', 'employee@gestaodireta.com', 'Preparo de área.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Manutenção da bomba de irrigação', 6800.00, 'EXPENSE', 'PAID', 'DEBIT_CARD', CURRENT_DATE - INTERVAL '35 days', CURRENT_DATE - INTERVAL '32 days', CURRENT_DATE - INTERVAL '32 days', 'Manutenção de máquinas', 'producer@gestaodireta.com', 'Reparo de bomba.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Pagamento de energia elétrica', 3900.00, 'EXPENSE', 'PAID', 'BOLETO', CURRENT_DATE - INTERVAL '20 days', CURRENT_DATE - INTERVAL '12 days', CURRENT_DATE - INTERVAL '12 days', 'Energia elétrica', 'admin@gestaodireta.com', 'Energia de irrigação.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Frete da primeira colheita de tomate', 4300.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '16 days', CURRENT_DATE - INTERVAL '10 days', CURRENT_DATE - INTERVAL '10 days', 'Transporte', 'employee@gestaodireta.com', 'Entrega da primeira colheita.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Venda inicial de tomate', 18000.00, 'INCOME', 'PAID', 'PIX', CURRENT_DATE - INTERVAL '18 days', CURRENT_DATE - INTERVAL '12 days', CURRENT_DATE - INTERVAL '12 days', 'Venda de produção', 'producer@gestaodireta.com', 'Primeira entrega ao atacado.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Venda de tomate para feira regional', 12500.00, 'INCOME', 'PAID', 'CASH', CURRENT_DATE - INTERVAL '8 days', CURRENT_DATE - INTERVAL '8 days', CURRENT_DATE - INTERVAL '8 days', 'Venda de produção', 'employee@gestaodireta.com', 'Venda direta.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Recebimento atrasado de tomate', 8400.00, 'INCOME', 'OVERDUE', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '20 days', CURRENT_DATE - INTERVAL '10 days', NULL, 'Venda de produção', 'admin@gestaodireta.com', 'Cooperativa ainda não liquidou.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Recebimento vencido do atacadista', 6700.00, 'INCOME', 'OVERDUE', 'BOLETO', CURRENT_DATE - INTERVAL '12 days', CURRENT_DATE - INTERVAL '3 days', NULL, 'Venda de produção', 'producer@gestaodireta.com', 'Título vencido.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Recebimento vencido de feira', 3900.00, 'INCOME', 'OVERDUE', 'PIX', CURRENT_DATE - INTERVAL '6 days', CURRENT_DATE - INTERVAL '1 day', NULL, 'Venda de produção', 'employee@gestaodireta.com', 'Cobrança em andamento.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Compra de embalagens em atraso', 4400.00, 'EXPENSE', 'OVERDUE', 'BOLETO', CURRENT_DATE - INTERVAL '16 days', CURRENT_DATE - INTERVAL '10 days', NULL, 'Insumos', 'employee@gestaodireta.com', 'Fornecedor aguarda pagamento.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Frete de tomate vencido', 5200.00, 'EXPENSE', 'OVERDUE', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '9 days', CURRENT_DATE - INTERVAL '3 days', NULL, 'Transporte', 'producer@gestaodireta.com', 'Frete pendente.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Conta de energia vencida', 3100.00, 'EXPENSE', 'OVERDUE', 'BOLETO', CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE - INTERVAL '1 day', NULL, 'Energia elétrica', 'admin@gestaodireta.com', 'Conta em atraso.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Mão de obra pendente da colheita', 7600.00, 'EXPENSE', 'OVERDUE', 'PIX', CURRENT_DATE - INTERVAL '4 days', CURRENT_DATE - INTERVAL '1 day', NULL, 'Mão de obra', 'producer@gestaodireta.com', 'Pagamento da equipe.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Parcela de arrendamento vencida', 6800.00, 'EXPENSE', 'OVERDUE', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '12 days', CURRENT_DATE - INTERVAL '3 days', NULL, 'Arrendamento', 'admin@gestaodireta.com', 'Parcela em atraso.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Análise laboratorial cancelada', 1200.00, 'EXPENSE', 'CANCELED', 'OTHER', CURRENT_DATE - INTERVAL '6 days', CURRENT_DATE - INTERVAL '2 days', NULL, 'Outras despesas', 'employee@gestaodireta.com', 'Serviço não realizado.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Venda futura de tomate para rede varejista', 28000.00, 'INCOME', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '3 days', NULL, 'Venda de produção', 'producer@gestaodireta.com', 'Contrato com rede varejista.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Recebimento da cooperativa de tomate', 24500.00, 'INCOME', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', NULL, 'Venda de produção', 'admin@gestaodireta.com', 'Liquidação prevista.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Venda de tomate para atacadista', 19500.00, 'INCOME', 'PENDING', 'PIX', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', NULL, 'Venda de produção', 'producer@gestaodireta.com', 'Pedido confirmado.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Receita de tomate para indústria', 22000.00, 'INCOME', 'PENDING', 'BOLETO', CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', NULL, 'Venda de produção', 'employee@gestaodireta.com', 'Contrato de processamento.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Subsídio para irrigação do tomate', 8500.00, 'INCOME', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '60 days', NULL, 'Subsídios', 'admin@gestaodireta.com', 'Repasse previsto.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Compra de caixas para tomate', 5100.00, 'EXPENSE', 'PENDING', 'BOLETO', CURRENT_DATE, CURRENT_DATE, NULL, 'Insumos', 'employee@gestaodireta.com', 'Vencimento hoje.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Aplicação programada de defensivos', 7400.00, 'EXPENSE', 'PENDING', 'PIX', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', NULL, 'Defensivos agrícolas', 'producer@gestaodireta.com', 'Aplicação programada.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Frete futuro de tomate', 6200.00, 'EXPENSE', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', NULL, 'Transporte', 'employee@gestaodireta.com', 'Entrega contratada.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Mão de obra programada', 9400.00, 'EXPENSE', 'PENDING', 'PIX', CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', NULL, 'Mão de obra', 'producer@gestaodireta.com', 'Equipe de colheita.'),
        ('Fazenda Boa Sorte', 'Tomate 2025/2026', 'Manutenção futura da estufa', 5300.00, 'EXPENSE', 'PENDING', 'DEBIT_CARD', CURRENT_DATE, CURRENT_DATE + INTERVAL '45 days', NULL, 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Revisão preventiva.'),

        -- Fazenda Boa Sorte - Café 2026/2027 (planejada)
        ('Fazenda Boa Sorte', 'Café 2026/2027', 'Preparação do solo para café', 12800.00, 'EXPENSE', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '45 days', NULL, 'Insumos', 'producer@gestaodireta.com', 'Preparo da nova área.'),
        ('Fazenda Boa Sorte', 'Café 2026/2027', 'Compra de mudas de café', 16400.00, 'EXPENSE', 'PENDING', 'BOLETO', CURRENT_DATE, CURRENT_DATE + INTERVAL '60 days', NULL, 'Sementes', 'employee@gestaodireta.com', 'Mudas certificadas.'),
        ('Fazenda Boa Sorte', 'Café 2026/2027', 'Previsão de venda futura de café', 58000.00, 'INCOME', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '60 days', NULL, 'Venda de produção', 'admin@gestaodireta.com', 'Contrato futuro de café.'),

        -- Fazenda Santa Clara - Café 2023/2024 (16 despesas, 8 receitas)
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Compra de mudas de café', 14200.00, 'EXPENSE', 'PAID', 'PIX', DATE '2023-09-20', DATE '2023-09-25', DATE '2023-09-25', 'Sementes', 'producer@gestaodireta.com', 'Reposição de lavoura.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Compra de fertilizante para café', 19800.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2023-10-02', DATE '2023-10-15', DATE '2023-10-15', 'Fertilizantes', 'employee@gestaodireta.com', 'Adubação da lavoura.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Aplicação de defensivos no café', 8200.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2023-10-18', DATE '2023-10-22', DATE '2023-10-22', 'Defensivos agrícolas', 'producer@gestaodireta.com', 'Controle preventivo.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Abastecimento do trator cafeeiro', 4300.00, 'EXPENSE', 'PAID', 'CREDIT_CARD', DATE '2023-11-03', DATE '2023-11-03', DATE '2023-11-03', 'Combustível', 'employee@gestaodireta.com', 'Diesel da operação.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Pagamento de mão de obra do café', 15400.00, 'EXPENSE', 'PAID', 'PIX', DATE '2023-11-18', DATE '2023-11-18', DATE '2023-11-18', 'Mão de obra', 'producer@gestaodireta.com', 'Tratos culturais.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Manutenção do secador de café', 7400.00, 'EXPENSE', 'PAID', 'DEBIT_CARD', DATE '2023-12-05', DATE '2023-12-08', DATE '2023-12-08', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Revisão do secador.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Pagamento de energia elétrica', 3700.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2023-12-18', DATE '2023-12-28', DATE '2023-12-28', 'Energia elétrica', 'admin@gestaodireta.com', 'Energia do beneficiamento.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Arrendamento da área cafeeira', 12600.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2024-01-05', DATE '2024-01-05', DATE '2024-01-05', 'Arrendamento', 'producer@gestaodireta.com', 'Parcela contratual.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Frete de fertilizantes', 2600.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-01-20', DATE '2024-01-22', DATE '2024-01-22', 'Transporte', 'employee@gestaodireta.com', 'Entrega de insumos.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Imposto rural proporcional', 3300.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-02-01', DATE '2024-02-10', DATE '2024-02-10', 'Impostos e taxas', 'admin@gestaodireta.com', 'Tributo da área.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Serviço de colheita mecanizada', 18600.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2024-05-08', DATE '2024-05-15', DATE '2024-05-15', 'Outras despesas', 'producer@gestaodireta.com', 'Colheita terceirizada.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Secagem e beneficiamento do café', 9400.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-05-22', DATE '2024-05-30', DATE '2024-05-30', 'Outras despesas', 'admin@gestaodireta.com', 'Beneficiamento de grãos.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Compra de sacarias para café', 2400.00, 'EXPENSE', 'PAID', 'CASH', DATE '2024-06-03', DATE '2024-06-03', DATE '2024-06-03', 'Insumos', 'employee@gestaodireta.com', 'Sacarias para armazenamento.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Frete da produção de café', 8700.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2024-06-18', DATE '2024-06-25', DATE '2024-06-25', 'Transporte', 'producer@gestaodireta.com', 'Entrega à cooperativa.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Manutenção da máquina de beneficiamento', 5100.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-06-28', DATE '2024-07-02', DATE '2024-07-02', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Ajuste mecânico.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Análise de qualidade do café', 1400.00, 'EXPENSE', 'PAID', 'OTHER', DATE '2024-07-05', DATE '2024-07-05', DATE '2024-07-05', 'Outras despesas', 'admin@gestaodireta.com', 'Classificação da bebida.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Venda de café para cooperativa', 48000.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-06-01', DATE '2024-06-10', DATE '2024-06-10', 'Venda de produção', 'producer@gestaodireta.com', 'Primeiro lote.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Venda de café lote 2', 42700.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-06-15', DATE '2024-06-25', DATE '2024-06-25', 'Venda de produção', 'producer@gestaodireta.com', 'Segundo lote.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Recebimento de cooperativa de café', 36500.00, 'INCOME', 'PAID', 'PIX', DATE '2024-06-30', DATE '2024-07-05', DATE '2024-07-05', 'Venda de produção', 'admin@gestaodireta.com', 'Liquidação do contrato.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Venda de café especial', 28500.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-07-02', DATE '2024-07-12', DATE '2024-07-12', 'Venda de produção', 'producer@gestaodireta.com', 'Lote de café especial.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Prêmio de qualidade do café', 6900.00, 'INCOME', 'PAID', 'PIX', DATE '2024-07-08', DATE '2024-07-15', DATE '2024-07-15', 'Outras receitas', 'admin@gestaodireta.com', 'Bonificação de qualidade.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Venda de café remanescente', 22300.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-07-10', DATE '2024-07-18', DATE '2024-07-18', 'Venda de produção', 'employee@gestaodireta.com', 'Fechamento de safra.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Serviço de secagem para vizinho', 4200.00, 'INCOME', 'PAID', 'CASH', DATE '2024-07-12', DATE '2024-07-12', DATE '2024-07-12', 'Prestação de serviços', 'producer@gestaodireta.com', 'Uso do secador.'),
        ('Fazenda Santa Clara', 'Café 2023/2024', 'Crédito tributário do café', 3100.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2024-07-14', DATE '2024-07-20', DATE '2024-07-20', 'Outras receitas', 'admin@gestaodireta.com', 'Compensação tributária.'),

        -- Fazenda Santa Clara - Feijão 2024/2025 (18 despesas, 4 receitas)
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Compra de sementes de feijão', 16800.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-09-03', DATE '2024-09-08', DATE '2024-09-08', 'Sementes', 'producer@gestaodireta.com', 'Sementes certificadas.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Compra de fertilizante para feijão', 18500.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-09-12', DATE '2024-09-25', DATE '2024-09-25', 'Fertilizantes', 'employee@gestaodireta.com', 'Adubação de base.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Aplicação de defensivos no feijão', 6900.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2024-09-28', DATE '2024-10-03', DATE '2024-10-03', 'Defensivos agrícolas', 'producer@gestaodireta.com', 'Controle de pragas.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Abastecimento do trator', 3900.00, 'EXPENSE', 'PAID', 'CREDIT_CARD', DATE '2024-10-03', DATE '2024-10-03', DATE '2024-10-03', 'Combustível', 'employee@gestaodireta.com', 'Diesel de plantio.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Mão de obra de plantio', 10800.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-10-10', DATE '2024-10-10', DATE '2024-10-10', 'Mão de obra', 'producer@gestaodireta.com', 'Equipe de plantio.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Manutenção da semeadora', 5200.00, 'EXPENSE', 'PAID', 'DEBIT_CARD', DATE '2024-10-20', DATE '2024-10-22', DATE '2024-10-22', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Revisão do equipamento.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Pagamento de energia da irrigação', 4200.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-11-05', DATE '2024-11-15', DATE '2024-11-15', 'Energia elétrica', 'admin@gestaodireta.com', 'Bombeamento de água.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Arrendamento da área de feijão', 11200.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2024-11-18', DATE '2024-11-18', DATE '2024-11-18', 'Arrendamento', 'producer@gestaodireta.com', 'Parcela contratual.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Frete de insumos', 2300.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-11-28', DATE '2024-12-01', DATE '2024-12-01', 'Transporte', 'employee@gestaodireta.com', 'Entrega de insumos.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Imposto rural da área de feijão', 2700.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2024-12-10', DATE '2024-12-20', DATE '2024-12-20', 'Impostos e taxas', 'admin@gestaodireta.com', 'Tributo proporcional.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Aplicação de fungicida', 6100.00, 'EXPENSE', 'PAID', 'PIX', DATE '2024-12-22', DATE '2024-12-28', DATE '2024-12-28', 'Defensivos agrícolas', 'producer@gestaodireta.com', 'Aplicação preventiva.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Diárias para colheita de feijão', 9800.00, 'EXPENSE', 'PAID', 'CASH', DATE '2025-01-22', DATE '2025-01-22', DATE '2025-01-22', 'Mão de obra', 'employee@gestaodireta.com', 'Equipe temporária.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Frete da produção de feijão', 7200.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', DATE '2025-02-05', DATE '2025-02-12', DATE '2025-02-12', 'Transporte', 'producer@gestaodireta.com', 'Entrega à cooperativa.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Secagem e limpeza do feijão', 4800.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2025-02-12', DATE '2025-02-20', DATE '2025-02-20', 'Outras despesas', 'admin@gestaodireta.com', 'Beneficiamento.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Compra de sacarias', 1900.00, 'EXPENSE', 'PAID', 'DEBIT_CARD', DATE '2025-02-18', DATE '2025-02-18', DATE '2025-02-18', 'Insumos', 'employee@gestaodireta.com', 'Sacarias para armazenamento.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Revisão da colheitadeira', 7600.00, 'EXPENSE', 'PAID', 'CHECK', DATE '2025-02-22', DATE '2025-02-28', DATE '2025-02-28', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Revisão pré-colheita.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Taxa de classificação de feijão', 1800.00, 'EXPENSE', 'PAID', 'BOLETO', DATE '2025-02-26', DATE '2025-03-05', DATE '2025-03-05', 'Impostos e taxas', 'admin@gestaodireta.com', 'Classificação comercial.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Serviço de pulverização', 4300.00, 'EXPENSE', 'PAID', 'OTHER', DATE '2025-03-01', DATE '2025-03-05', DATE '2025-03-05', 'Outras despesas', 'producer@gestaodireta.com', 'Pulverização terceirizada.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Venda de feijão para cooperativa', 37000.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2025-02-08', DATE '2025-02-15', DATE '2025-02-15', 'Venda de produção', 'producer@gestaodireta.com', 'Primeiro lote.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Venda de feijão lote 2', 31500.00, 'INCOME', 'PAID', 'BANK_TRANSFER', DATE '2025-02-20', DATE '2025-02-28', DATE '2025-02-28', 'Venda de produção', 'producer@gestaodireta.com', 'Segundo lote.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Recebimento de cooperativa pelo feijão', 22500.00, 'INCOME', 'PAID', 'PIX', DATE '2025-03-02', DATE '2025-03-08', DATE '2025-03-08', 'Venda de produção', 'admin@gestaodireta.com', 'Liquidação de contrato.'),
        ('Fazenda Santa Clara', 'Feijão 2024/2025', 'Venda de palhada de feijão', 4200.00, 'INCOME', 'PAID', 'CASH', DATE '2025-03-10', DATE '2025-03-10', DATE '2025-03-10', 'Outras receitas', 'employee@gestaodireta.com', 'Aproveitamento de resíduos.'),

        -- Fazenda Santa Clara - Laranja 2025/2026 (18 passadas e 10 futuras)
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Compra de fertilizante para laranja', 17800.00, 'EXPENSE', 'PAID', 'BOLETO', CURRENT_DATE - INTERVAL '120 days', CURRENT_DATE - INTERVAL '113 days', CURRENT_DATE - INTERVAL '113 days', 'Fertilizantes', 'producer@gestaodireta.com', 'Adubação do pomar.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Aplicação de defensivos no pomar', 9200.00, 'EXPENSE', 'PAID', 'PIX', CURRENT_DATE - INTERVAL '100 days', CURRENT_DATE - INTERVAL '96 days', CURRENT_DATE - INTERVAL '96 days', 'Defensivos agrícolas', 'employee@gestaodireta.com', 'Controle de pragas.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Poda e manejo do pomar', 14800.00, 'EXPENSE', 'PAID', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '82 days', CURRENT_DATE - INTERVAL '75 days', CURRENT_DATE - INTERVAL '75 days', 'Outras despesas', 'producer@gestaodireta.com', 'Manejo de produção.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Abastecimento de equipamentos', 3800.00, 'EXPENSE', 'PAID', 'CREDIT_CARD', CURRENT_DATE - INTERVAL '65 days', CURRENT_DATE - INTERVAL '65 days', CURRENT_DATE - INTERVAL '65 days', 'Combustível', 'employee@gestaodireta.com', 'Combustível para tratores.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Pagamento de mão de obra no pomar', 16900.00, 'EXPENSE', 'PAID', 'PIX', CURRENT_DATE - INTERVAL '52 days', CURRENT_DATE - INTERVAL '52 days', CURRENT_DATE - INTERVAL '52 days', 'Mão de obra', 'producer@gestaodireta.com', 'Tratos culturais.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Manutenção do pulverizador', 5700.00, 'EXPENSE', 'PAID', 'DEBIT_CARD', CURRENT_DATE - INTERVAL '40 days', CURRENT_DATE - INTERVAL '37 days', CURRENT_DATE - INTERVAL '37 days', 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Revisão preventiva.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Pagamento de energia elétrica', 4100.00, 'EXPENSE', 'PAID', 'BOLETO', CURRENT_DATE - INTERVAL '26 days', CURRENT_DATE - INTERVAL '18 days', CURRENT_DATE - INTERVAL '18 days', 'Energia elétrica', 'admin@gestaodireta.com', 'Energia de irrigação.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Venda inicial de laranja', 16500.00, 'INCOME', 'PAID', 'PIX', CURRENT_DATE - INTERVAL '18 days', CURRENT_DATE - INTERVAL '12 days', CURRENT_DATE - INTERVAL '12 days', 'Venda de produção', 'producer@gestaodireta.com', 'Entrega ao atacado.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Venda de laranja para indústria', 13200.00, 'INCOME', 'PAID', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '9 days', CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE - INTERVAL '5 days', 'Venda de produção', 'employee@gestaodireta.com', 'Primeira remessa.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Recebimento vencido de laranja', 7600.00, 'INCOME', 'OVERDUE', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '18 days', CURRENT_DATE - INTERVAL '10 days', NULL, 'Venda de produção', 'admin@gestaodireta.com', 'Indústria ainda não liquidou.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Recebimento atrasado da cooperativa', 5400.00, 'INCOME', 'OVERDUE', 'BOLETO', CURRENT_DATE - INTERVAL '8 days', CURRENT_DATE - INTERVAL '3 days', NULL, 'Venda de produção', 'producer@gestaodireta.com', 'Título vencido.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Recebimento vencido da feira', 3200.00, 'INCOME', 'OVERDUE', 'PIX', CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE - INTERVAL '1 day', NULL, 'Venda de produção', 'employee@gestaodireta.com', 'Cobrança pendente.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Compra de embalagens vencida', 3900.00, 'EXPENSE', 'OVERDUE', 'BOLETO', CURRENT_DATE - INTERVAL '16 days', CURRENT_DATE - INTERVAL '10 days', NULL, 'Insumos', 'employee@gestaodireta.com', 'Fornecedor aguarda pagamento.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Frete de laranja vencido', 4700.00, 'EXPENSE', 'OVERDUE', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '8 days', CURRENT_DATE - INTERVAL '3 days', NULL, 'Transporte', 'producer@gestaodireta.com', 'Frete pendente.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Conta de energia vencida', 2900.00, 'EXPENSE', 'OVERDUE', 'BOLETO', CURRENT_DATE - INTERVAL '4 days', CURRENT_DATE - INTERVAL '1 day', NULL, 'Energia elétrica', 'admin@gestaodireta.com', 'Conta em atraso.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Mão de obra vencida da colheita', 7100.00, 'EXPENSE', 'OVERDUE', 'PIX', CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE - INTERVAL '1 day', NULL, 'Mão de obra', 'producer@gestaodireta.com', 'Pagamento da equipe.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Parcela de arrendamento vencida', 5900.00, 'EXPENSE', 'OVERDUE', 'BANK_TRANSFER', CURRENT_DATE - INTERVAL '10 days', CURRENT_DATE - INTERVAL '3 days', NULL, 'Arrendamento', 'admin@gestaodireta.com', 'Parcela em atraso.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Análise de resíduos cancelada', 1100.00, 'EXPENSE', 'CANCELED', 'OTHER', CURRENT_DATE - INTERVAL '7 days', CURRENT_DATE - INTERVAL '2 days', NULL, 'Outras despesas', 'employee@gestaodireta.com', 'Serviço cancelado.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Venda futura de laranja para indústria', 26500.00, 'INCOME', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '3 days', NULL, 'Venda de produção', 'producer@gestaodireta.com', 'Contrato para indústria de suco.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Recebimento da cooperativa de laranja', 21800.00, 'INCOME', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', NULL, 'Venda de produção', 'admin@gestaodireta.com', 'Liquidação prevista.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Venda de laranja para atacadista', 17400.00, 'INCOME', 'PENDING', 'PIX', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', NULL, 'Venda de produção', 'producer@gestaodireta.com', 'Pedido confirmado.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Receita de laranja contratada', 19600.00, 'INCOME', 'PENDING', 'BOLETO', CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', NULL, 'Venda de produção', 'employee@gestaodireta.com', 'Contrato de fornecimento.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Subsídio para irrigação do pomar', 7200.00, 'INCOME', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '60 days', NULL, 'Subsídios', 'admin@gestaodireta.com', 'Repasse previsto.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Compra de caixas para laranja', 4600.00, 'EXPENSE', 'PENDING', 'BOLETO', CURRENT_DATE, CURRENT_DATE, NULL, 'Insumos', 'employee@gestaodireta.com', 'Vencimento hoje.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Aplicação programada de defensivos', 6800.00, 'EXPENSE', 'PENDING', 'PIX', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', NULL, 'Defensivos agrícolas', 'producer@gestaodireta.com', 'Aplicação programada.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Frete futuro de laranja', 5700.00, 'EXPENSE', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', NULL, 'Transporte', 'employee@gestaodireta.com', 'Entrega contratada.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Mão de obra programada', 8600.00, 'EXPENSE', 'PENDING', 'PIX', CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', NULL, 'Mão de obra', 'producer@gestaodireta.com', 'Equipe de colheita.'),
        ('Fazenda Santa Clara', 'Laranja 2025/2026', 'Manutenção futura do pulverizador', 4800.00, 'EXPENSE', 'PENDING', 'DEBIT_CARD', CURRENT_DATE, CURRENT_DATE + INTERVAL '45 days', NULL, 'Manutenção de máquinas', 'employee@gestaodireta.com', 'Revisão preventiva.'),

        -- Fazenda Santa Clara - Leite 2026 (planejada)
        ('Fazenda Santa Clara', 'Leite 2026', 'Reforma da ordenha', 11800.00, 'EXPENSE', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '45 days', NULL, 'Manutenção de máquinas', 'producer@gestaodireta.com', 'Preparação da estrutura.'),
        ('Fazenda Santa Clara', 'Leite 2026', 'Compra de suplementos para rebanho', 9700.00, 'EXPENSE', 'PENDING', 'BOLETO', CURRENT_DATE, CURRENT_DATE + INTERVAL '60 days', NULL, 'Insumos', 'employee@gestaodireta.com', 'Primeiro lote de suplementos.'),
        ('Fazenda Santa Clara', 'Leite 2026', 'Previsão de venda futura de leite', 42000.00, 'INCOME', 'PENDING', 'BANK_TRANSFER', CURRENT_DATE, CURRENT_DATE + INTERVAL '60 days', NULL, 'Venda de produção', 'admin@gestaodireta.com', 'Contrato com laticínio.')
) AS data(
    farm_name,
    season_name,
    description,
    amount,
    type,
    payment_status,
    payment_method,
    transaction_date,
    due_date,
    paid_at,
    category_name,
    creator_email,
    notes
)
JOIN farms farm ON farm.name = data.farm_name
JOIN harvest_seasons season
    ON season.farm_id = farm.id
    AND season.name = data.season_name
JOIN financial_categories category
    ON category.farm_id = farm.id
    AND category.name = data.category_name
    AND category.type = data.type
JOIN users creator ON creator.email = data.creator_email;

-- Validações obrigatórias de consistência.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM production_activities
        WHERE farm_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Demo data invalid: production activity without farm.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM financial_categories
        WHERE farm_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Demo data invalid: financial category without farm.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM harvest_seasons season
        JOIN production_activities activity ON activity.id = season.production_activity_id
        WHERE season.farm_id <> activity.farm_id
    ) THEN
        RAISE EXCEPTION 'Demo data invalid: harvest season linked to another farm activity.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM financial_transactions transaction
        JOIN financial_categories category ON category.id = transaction.category_id
        WHERE transaction.farm_id <> category.farm_id
    ) THEN
        RAISE EXCEPTION 'Demo data invalid: transaction linked to another farm category.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM financial_transactions transaction
        JOIN harvest_seasons season ON season.id = transaction.harvest_season_id
        WHERE transaction.harvest_season_id IS NOT NULL
          AND transaction.farm_id <> season.farm_id
    ) THEN
        RAISE EXCEPTION 'Demo data invalid: transaction linked to another farm harvest season.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM financial_transactions
        WHERE (status = 'PAID' AND paid_at IS NULL)
           OR (status <> 'PAID' AND paid_at IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'Demo data invalid: payment status and paid_at are inconsistent.';
    END IF;
END $$;

-- Relatório final de consistência: todos os contadores de violações devem ser zero.
SELECT COUNT(*) AS production_activities_without_farm
FROM production_activities
WHERE farm_id IS NULL;

SELECT COUNT(*) AS financial_categories_without_farm
FROM financial_categories
WHERE farm_id IS NULL;

SELECT season.id AS harvest_season_with_foreign_activity
FROM harvest_seasons season
JOIN production_activities activity ON activity.id = season.production_activity_id
WHERE season.farm_id <> activity.farm_id;

SELECT transaction.id AS transaction_with_foreign_category
FROM financial_transactions transaction
JOIN financial_categories category ON category.id = transaction.category_id
WHERE transaction.farm_id <> category.farm_id;

SELECT transaction.id AS transaction_with_foreign_harvest_season
FROM financial_transactions transaction
JOIN harvest_seasons season ON season.id = transaction.harvest_season_id
WHERE transaction.harvest_season_id IS NOT NULL
  AND transaction.farm_id <> season.farm_id;

SELECT
    farm.name AS farm,
    season.name AS harvest_season,
    COUNT(transaction.id) AS transactions,
    COUNT(*) FILTER (WHERE transaction.type = 'INCOME') AS incomes,
    COUNT(*) FILTER (WHERE transaction.type = 'EXPENSE') AS expenses,
    COUNT(*) FILTER (WHERE transaction.status = 'PAID') AS paid,
    COUNT(*) FILTER (WHERE transaction.status = 'PENDING') AS pending,
    COUNT(*) FILTER (WHERE transaction.status = 'OVERDUE') AS overdue,
    COUNT(*) FILTER (WHERE transaction.status = 'CANCELED') AS canceled
FROM harvest_seasons season
JOIN farms farm ON farm.id = season.farm_id
LEFT JOIN financial_transactions transaction ON transaction.harvest_season_id = season.id
GROUP BY farm.name, season.name
ORDER BY farm.name, season.name;

SELECT
    farm.name AS farm,
    COUNT(transaction.id) AS total_transactions,
    COUNT(*) FILTER (
        WHERE transaction.type = 'INCOME'
          AND transaction.status IN ('PENDING', 'OVERDUE')
    ) AS expected_income_count,
    COUNT(*) FILTER (
        WHERE transaction.type = 'EXPENSE'
          AND transaction.status IN ('PENDING', 'OVERDUE')
    ) AS expected_expense_count
FROM farms farm
LEFT JOIN financial_transactions transaction ON transaction.farm_id = farm.id
GROUP BY farm.name
ORDER BY farm.name;

COMMIT;
