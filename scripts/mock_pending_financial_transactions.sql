-- MOCK DE PENDENCIAS FINANCEIRAS VIA TELEGRAM
-- Executar somente no ambiente local/de desenvolvimento.
-- O script nao altera schema e nao remove dados existentes.

BEGIN;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM messaging_accounts account
        JOIN user_contacts contact
            ON contact.id = account.user_contact_id
        JOIN users app_user
            ON app_user.id = contact.user_id
        JOIN messaging_conversations conversation
            ON conversation.messaging_account_id = account.id
        JOIN farms farm
            ON farm.id = conversation.farm_id
        JOIN farm_users farm_user
            ON farm_user.farm_id = farm.id
            AND farm_user.user_id = app_user.id
        WHERE account.channel = 'TELEGRAM'
            AND account.status = 'ACTIVE'
            AND contact.status = 'ACTIVE'
            AND app_user.status = 'ACTIVE'
            AND conversation.status = 'ACTIVE'
            AND farm.status = 'ACTIVE'
            AND farm_user.role IN ('PRODUCER', 'EMPLOYEE')
    ) THEN
        RAISE EXCEPTION
            'No active Telegram context with an eligible user and farm was found for pending financial mocks.';
    END IF;
END;
$$;

WITH mock_rows (
    mock_key,
    content,
    type,
    amount,
    transaction_date,
    description,
    category_name,
    missing_fields,
    payment_method,
    notes,
    confidence,
    received_at
) AS (
    VALUES
        (
            '01',
            'Gastei R$ 250,00 com combustivel hoje.',
            'EXPENSE',
            250.00,
            CURRENT_DATE,
            'Combustivel para veiculo da fazenda',
            'Combustível',
            NULL,
            'PIX',
            'Abastecimento do veiculo de apoio.',
            0.982,
            CURRENT_TIMESTAMP - INTERVAL '2 hours'
        ),
        (
            '02',
            'Comprei R$ 1.850,00 de fertilizante ontem.',
            'EXPENSE',
            1850.00,
            CURRENT_DATE - 1,
            'Compra de fertilizante para lavoura',
            'Fertilizantes',
            NULL,
            'BANK_TRANSFER',
            'Adubo para o plantio de tomate.',
            0.965,
            CURRENT_TIMESTAMP - INTERVAL '1 day'
        ),
        (
            '03',
            'Recebi R$ 7.500,00 pela venda de milho ha 2 dias.',
            'INCOME',
            7500.00,
            CURRENT_DATE - 2,
            'Venda de milho',
            'Venda de produção',
            NULL,
            'BANK_TRANSFER',
            'Pagamento parcial do lote entregue.',
            0.991,
            CURRENT_TIMESTAMP - INTERVAL '2 days'
        ),
        (
            '04',
            'Paguei R$ 320,00 pela manutencao do trator ha 3 dias.',
            'EXPENSE',
            320.00,
            CURRENT_DATE - 3,
            'Manutencao do trator',
            'Manutenção de máquinas',
            NULL,
            'CASH',
            'Troca de correia e revisao basica.',
            0.974,
            CURRENT_TIMESTAMP - INTERVAL '3 days'
        ),
        (
            '05',
            'Recebi R$ 3.200,00 pela venda de soja ha 5 dias.',
            'INCOME',
            3200.00,
            CURRENT_DATE - 5,
            'Venda de soja',
            'Venda de produção',
            NULL,
            'PIX',
            'Venda para cooperativa regional.',
            0.989,
            CURRENT_TIMESTAMP - INTERVAL '5 days'
        ),
        (
            '06',
            'Gastei R$ 780,00 com sementes ha 6 dias.',
            'EXPENSE',
            780.00,
            CURRENT_DATE - 6,
            'Compra de sementes',
            'Sementes',
            NULL,
            'CREDIT_CARD',
            'Sementes de tomate para novo talhao.',
            0.953,
            CURRENT_TIMESTAMP - INTERVAL '6 days'
        ),
        (
            '07',
            'Paguei R$ 450,00 de energia da propriedade ha 10 dias.',
            'EXPENSE',
            450.00,
            CURRENT_DATE - 10,
            'Conta de energia da propriedade',
            'Energia elétrica',
            NULL,
            'BOLETO',
            'Fatura mensal da unidade consumidora.',
            0.978,
            CURRENT_TIMESTAMP - INTERVAL '10 days'
        ),
        (
            '08',
            'Recebi R$ 4.500,00 pela venda de milho ha 12 dias.',
            'INCOME',
            4500.00,
            CURRENT_DATE - 12,
            'Venda de milho',
            'Venda de produção',
            NULL,
            'BANK_TRANSFER',
            'Venda de 90 sacas para cerealista local.',
            0.986,
            CURRENT_TIMESTAMP - INTERVAL '12 days'
        ),
        (
            '09',
            'Gastei R$ 630,00 com diesel para o trator ha 15 dias.',
            'EXPENSE',
            630.00,
            CURRENT_DATE - 15,
            'Diesel para o trator',
            'Combustível',
            NULL,
            'PIX',
            'Combustivel usado no preparo do solo.',
            0.979,
            CURRENT_TIMESTAMP - INTERVAL '15 days'
        ),
        (
            '10',
            'Paguei R$ 1.120,00 pelo transporte da colheita ha 18 dias.',
            'EXPENSE',
            1120.00,
            CURRENT_DATE - 18,
            'Frete da colheita',
            'Transporte',
            NULL,
            'BANK_TRANSFER',
            'Transporte ate o armazem da cooperativa.',
            0.944,
            CURRENT_TIMESTAMP - INTERVAL '18 days'
        ),
        (
            '11',
            'Recebi R$ 2.900,00 por servico de preparo de solo ha 22 dias.',
            'INCOME',
            2900.00,
            CURRENT_DATE - 22,
            'Servico de preparo de solo',
            'Prestação de serviços',
            NULL,
            'CASH',
            'Servico prestado para propriedade vizinha.',
            0.932,
            CURRENT_TIMESTAMP - INTERVAL '22 days'
        ),
        (
            '12',
            'Gastei 300 reais hoje.',
            'EXPENSE',
            300.00,
            CURRENT_DATE,
            'Gastei R$ 300,00.',
            NULL,
            'category',
            NULL,
            NULL,
            0.821,
            CURRENT_TIMESTAMP - INTERVAL '4 hours'
        ),
        (
            '13',
            'Recebi 2500 pela venda ontem.',
            'INCOME',
            2500.00,
            CURRENT_DATE - 1,
            'Recebi R$ 2.500,00 pela venda.',
            NULL,
            'category',
            NULL,
            NULL,
            0.846,
            CURRENT_TIMESTAMP - INTERVAL '1 day 3 hours'
        ),
        (
            '14',
            'Paguei 680 reais ontem.',
            'EXPENSE',
            680.00,
            CURRENT_DATE - 1,
            'Paguei R$ 680,00.',
            NULL,
            'category',
            NULL,
            NULL,
            0.804,
            CURRENT_TIMESTAMP - INTERVAL '1 day 5 hours'
        ),
        (
            '15',
            'Entrou 1600 reais hoje.',
            'INCOME',
            1600.00,
            CURRENT_DATE,
            'Entrou R$ 1.600,00.',
            NULL,
            'category',
            NULL,
            NULL,
            0.815,
            CURRENT_TIMESTAMP - INTERVAL '6 hours'
        )
),
target_context AS (
    SELECT
        account.id AS messaging_account_id,
        conversation.id AS messaging_conversation_id,
        farm.id AS farm_id,
        app_user.id AS requested_by_user_id,
        account.external_user_id,
        account.external_chat_id
    FROM messaging_accounts account
    JOIN user_contacts contact
        ON contact.id = account.user_contact_id
    JOIN users app_user
        ON app_user.id = contact.user_id
    JOIN messaging_conversations conversation
        ON conversation.messaging_account_id = account.id
    JOIN farms farm
        ON farm.id = conversation.farm_id
    JOIN farm_users farm_user
        ON farm_user.farm_id = farm.id
        AND farm_user.user_id = app_user.id
    WHERE account.channel = 'TELEGRAM'
        AND account.status = 'ACTIVE'
        AND contact.status = 'ACTIVE'
        AND app_user.status = 'ACTIVE'
        AND conversation.status = 'ACTIVE'
        AND farm.status = 'ACTIVE'
        AND farm_user.role IN ('PRODUCER', 'EMPLOYEE')
    ORDER BY conversation.last_interaction_at DESC, conversation.id DESC
    LIMIT 1
)
INSERT INTO messaging_messages (
    messaging_conversation_id,
    channel,
    provider_update_id,
    provider_message_id,
    external_user_id,
    external_chat_id,
    direction,
    message_type,
    content,
    status,
    received_at,
    processed_at,
    created_at,
    updated_at
)
SELECT
    context.messaging_conversation_id,
    'TELEGRAM',
    'gd-mock-pending-20260816-' || mock.mock_key,
    'gd-mock-message-20260816-' || mock.mock_key,
    context.external_user_id,
    context.external_chat_id,
    'INBOUND',
    'TEXT',
    mock.content,
    'PROCESSED',
    mock.received_at,
    mock.received_at,
    mock.received_at,
    mock.received_at
FROM mock_rows mock
CROSS JOIN target_context context
ON CONFLICT (channel, provider_update_id) DO NOTHING;

WITH mock_rows (
    mock_key,
    type,
    amount,
    transaction_date,
    description,
    category_name,
    missing_fields,
    payment_method,
    notes,
    confidence,
    received_at
) AS (
    VALUES
        ('01', 'EXPENSE', 250.00, CURRENT_DATE, 'Combustivel para veiculo da fazenda', 'Combustível', NULL, 'PIX', 'Abastecimento do veiculo de apoio.', 0.982, CURRENT_TIMESTAMP - INTERVAL '2 hours'),
        ('02', 'EXPENSE', 1850.00, CURRENT_DATE - 1, 'Compra de fertilizante para lavoura', 'Fertilizantes', NULL, 'BANK_TRANSFER', 'Adubo para o plantio de tomate.', 0.965, CURRENT_TIMESTAMP - INTERVAL '1 day'),
        ('03', 'INCOME', 7500.00, CURRENT_DATE - 2, 'Venda de milho', 'Venda de produção', NULL, 'BANK_TRANSFER', 'Pagamento parcial do lote entregue.', 0.991, CURRENT_TIMESTAMP - INTERVAL '2 days'),
        ('04', 'EXPENSE', 320.00, CURRENT_DATE - 3, 'Manutencao do trator', 'Manutenção de máquinas', NULL, 'CASH', 'Troca de correia e revisao basica.', 0.974, CURRENT_TIMESTAMP - INTERVAL '3 days'),
        ('05', 'INCOME', 3200.00, CURRENT_DATE - 5, 'Venda de soja', 'Venda de produção', NULL, 'PIX', 'Venda para cooperativa regional.', 0.989, CURRENT_TIMESTAMP - INTERVAL '5 days'),
        ('06', 'EXPENSE', 780.00, CURRENT_DATE - 6, 'Compra de sementes', 'Sementes', NULL, 'CREDIT_CARD', 'Sementes de tomate para novo talhao.', 0.953, CURRENT_TIMESTAMP - INTERVAL '6 days'),
        ('07', 'EXPENSE', 450.00, CURRENT_DATE - 10, 'Conta de energia da propriedade', 'Energia elétrica', NULL, 'BOLETO', 'Fatura mensal da unidade consumidora.', 0.978, CURRENT_TIMESTAMP - INTERVAL '10 days'),
        ('08', 'INCOME', 4500.00, CURRENT_DATE - 12, 'Venda de milho', 'Venda de produção', NULL, 'BANK_TRANSFER', 'Venda de 90 sacas para cerealista local.', 0.986, CURRENT_TIMESTAMP - INTERVAL '12 days'),
        ('09', 'EXPENSE', 630.00, CURRENT_DATE - 15, 'Diesel para o trator', 'Combustível', NULL, 'PIX', 'Combustivel usado no preparo do solo.', 0.979, CURRENT_TIMESTAMP - INTERVAL '15 days'),
        ('10', 'EXPENSE', 1120.00, CURRENT_DATE - 18, 'Frete da colheita', 'Transporte', NULL, 'BANK_TRANSFER', 'Transporte ate o armazem da cooperativa.', 0.944, CURRENT_TIMESTAMP - INTERVAL '18 days'),
        ('11', 'INCOME', 2900.00, CURRENT_DATE - 22, 'Servico de preparo de solo', 'Prestação de serviços', NULL, 'CASH', 'Servico prestado para propriedade vizinha.', 0.932, CURRENT_TIMESTAMP - INTERVAL '22 days'),
        ('12', 'EXPENSE', 300.00, CURRENT_DATE, 'Gastei R$ 300,00.', NULL, 'category', NULL, NULL, 0.821, CURRENT_TIMESTAMP - INTERVAL '4 hours'),
        ('13', 'INCOME', 2500.00, CURRENT_DATE - 1, 'Recebi R$ 2.500,00 pela venda.', NULL, 'category', NULL, NULL, 0.846, CURRENT_TIMESTAMP - INTERVAL '1 day 3 hours'),
        ('14', 'EXPENSE', 680.00, CURRENT_DATE - 1, 'Paguei R$ 680,00.', NULL, 'category', NULL, NULL, 0.804, CURRENT_TIMESTAMP - INTERVAL '1 day 5 hours'),
        ('15', 'INCOME', 1600.00, CURRENT_DATE, 'Entrou R$ 1.600,00.', NULL, 'category', NULL, NULL, 0.815, CURRENT_TIMESTAMP - INTERVAL '6 hours')
),
target_context AS (
    SELECT
        account.id AS messaging_account_id,
        conversation.id AS messaging_conversation_id,
        farm.id AS farm_id,
        app_user.id AS requested_by_user_id
    FROM messaging_accounts account
    JOIN user_contacts contact
        ON contact.id = account.user_contact_id
    JOIN users app_user
        ON app_user.id = contact.user_id
    JOIN messaging_conversations conversation
        ON conversation.messaging_account_id = account.id
    JOIN farms farm
        ON farm.id = conversation.farm_id
    JOIN farm_users farm_user
        ON farm_user.farm_id = farm.id
        AND farm_user.user_id = app_user.id
    WHERE account.channel = 'TELEGRAM'
        AND account.status = 'ACTIVE'
        AND contact.status = 'ACTIVE'
        AND app_user.status = 'ACTIVE'
        AND conversation.status = 'ACTIVE'
        AND farm.status = 'ACTIVE'
        AND farm_user.role IN ('PRODUCER', 'EMPLOYEE')
    ORDER BY conversation.last_interaction_at DESC, conversation.id DESC
    LIMIT 1
)
INSERT INTO pending_financial_transactions (
    farm_id,
    requested_by_user_id,
    messaging_account_id,
    messaging_conversation_id,
    source_message_id,
    source_channel,
    type,
    amount,
    transaction_date,
    description,
    missing_fields,
    suggested_category_id,
    raw_category_name,
    payment_method,
    notes,
    status,
    confidence,
    ai_model,
    ai_processed_at,
    created_at,
    updated_at
)
SELECT
    context.farm_id,
    context.requested_by_user_id,
    context.messaging_account_id,
    context.messaging_conversation_id,
    message.id,
    'TELEGRAM',
    mock.type,
    mock.amount,
    mock.transaction_date,
    mock.description,
    mock.missing_fields,
    category.id,
    NULL,
    mock.payment_method,
    mock.notes,
    'PENDING_REVIEW',
    mock.confidence,
    'mock-telegram-fixture',
    mock.received_at,
    mock.received_at,
    mock.received_at
FROM mock_rows mock
CROSS JOIN target_context context
JOIN messaging_messages message
    ON message.channel = 'TELEGRAM'
    AND message.provider_update_id = 'gd-mock-pending-20260816-' || mock.mock_key
LEFT JOIN financial_categories category
    ON category.farm_id = context.farm_id
    AND lower(trim(category.name)) = lower(trim(mock.category_name))
    AND category.type = mock.type
    AND category.status = 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM pending_financial_transactions pending
    WHERE pending.source_message_id = message.id
);

COMMIT;
