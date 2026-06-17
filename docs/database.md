# Banco de Dados

## Banco principal

```text
PostgreSQL
```

## Migrations

```text
Flyway
```

Diretório:

```text
src/main/resources/db/migration
```

## JPA

Não usar:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

Usar:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

## Ordem inicial das migrations

```text
V1__create_users_table.sql
V2__create_farms_table.sql
V3__create_farm_users_table.sql
V4__create_financial_categories_table.sql
V5__create_financial_transactions_table.sql
V6__insert_default_financial_categories.sql
V7__insert_initial_admin_user.sql
```

## Convenções

- Tabelas e colunas em snake_case.
- Enums como VARCHAR.
- IDs como BIGSERIAL.
- Valores monetários como NUMERIC(15,2).
- Criar foreign keys.
- Criar unique constraints relevantes.
- Criar índices para filtros frequentes.
- Não apagar registros financeiros fisicamente.

---

# users

| Coluna | Tipo sugerido | Obrigatório | Observação |
|---|---|---:|---|
| id | BIGSERIAL | Sim | PK |
| name | VARCHAR(120) | Sim | Nome completo |
| email | VARCHAR(160) | Sim | Único |
| password | VARCHAR(255) | Sim | Senha criptografada |
| document | VARCHAR(20) | Não | CPF/CNPJ |
| user_type | VARCHAR(30) | Sim | ADMIN ou USER |
| status | VARCHAR(30) | Sim | ACTIVE, INACTIVE, BLOCKED |
| created_at | TIMESTAMP | Sim | Criação |
| updated_at | TIMESTAMP | Não | Atualização |

Constraints:
- PK users(id)
- UK users(email)

Índices:
- idx_users_email
- idx_users_status
- idx_users_user_type

---

# farms

| Coluna | Tipo sugerido | Obrigatório | Observação |
|---|---|---:|---|
| id | BIGSERIAL | Sim | PK |
| name | VARCHAR(120) | Sim | Nome |
| document | VARCHAR(30) | Não | Documento |
| city | VARCHAR(100) | Não | Cidade |
| state | VARCHAR(2) | Não | UF |
| total_area | NUMERIC(12,2) | Não | Área total |
| production_type | VARCHAR(40) | Não | AGRICULTURE, LIVESTOCK, MIXED, OTHER |
| status | VARCHAR(30) | Sim | ACTIVE ou INACTIVE |
| created_at | TIMESTAMP | Sim | Criação |
| updated_at | TIMESTAMP | Não | Atualização |

Constraints:
- PK farms(id)

Índices:
- idx_farms_status
- idx_farms_name

---

# farm_users

Representa vínculo entre usuário e fazenda.

| Coluna | Tipo sugerido | Obrigatório | Observação |
|---|---|---:|---|
| id | BIGSERIAL | Sim | PK |
| farm_id | BIGINT | Sim | FK farms |
| user_id | BIGINT | Sim | FK users |
| role | VARCHAR(30) | Sim | PRODUCER, EMPLOYEE, ACCOUNTANT, INACTIVE |
| created_at | TIMESTAMP | Sim | Criação |
| updated_at | TIMESTAMP | Não | Atualização |

Constraints:
- PK farm_users(id)
- FK farm_users(farm_id) -> farms(id)
- FK farm_users(user_id) -> users(id)
- UK farm_users(farm_id, user_id)

Índices:
- idx_farm_users_farm_id
- idx_farm_users_user_id
- idx_farm_users_role

---

# financial_categories

| Coluna | Tipo sugerido | Obrigatório | Observação |
|---|---|---:|---|
| id | BIGSERIAL | Sim | PK |
| name | VARCHAR(100) | Sim | Nome |
| type | VARCHAR(20) | Sim | INCOME ou EXPENSE |
| color | VARCHAR(20) | Não | Cor |
| icon | VARCHAR(60) | Não | Ícone |
| farm_id | BIGINT | Não | Nulo para categoria padrão |
| is_default | BOOLEAN | Sim | Categoria padrão |
| status | VARCHAR(30) | Sim | ACTIVE ou INACTIVE |
| created_at | TIMESTAMP | Sim | Criação |
| updated_at | TIMESTAMP | Não | Atualização |

Constraints:
- PK financial_categories(id)
- FK financial_categories(farm_id) -> farms(id)

Índices:
- idx_financial_categories_farm_id
- idx_financial_categories_type
- idx_financial_categories_status
- idx_financial_categories_is_default

Regras:
- Categoria padrão tem farm_id nulo.
- Categoria específica tem farm_id preenchido.
- Categoria INACTIVE não deve ser usada em novas transações.

---

# financial_transactions

| Coluna | Tipo sugerido | Obrigatório | Observação |
|---|---|---:|---|
| id | BIGSERIAL | Sim | PK |
| description | VARCHAR(160) | Sim | Descrição |
| amount | NUMERIC(15,2) | Sim | Valor |
| type | VARCHAR(20) | Sim | INCOME ou EXPENSE |
| status | VARCHAR(30) | Sim | PENDING, PAID, OVERDUE, CANCELED |
| payment_method | VARCHAR(30) | Não | Forma de pagamento |
| transaction_date | DATE | Sim | Data da movimentação |
| due_date | DATE | Não | Vencimento |
| paid_at | DATE | Não | Pagamento |
| notes | VARCHAR(500) | Não | Observações |
| farm_id | BIGINT | Sim | FK farms |
| category_id | BIGINT | Não | FK financial_categories |
| created_by_user_id | BIGINT | Sim | FK users |
| updated_by_user_id | BIGINT | Não | FK users |
| record_status | VARCHAR(30) | Sim | ACTIVE ou DELETED |
| created_at | TIMESTAMP | Sim | Criação |
| updated_at | TIMESTAMP | Não | Atualização |

Constraints:
- PK financial_transactions(id)
- FK financial_transactions(farm_id) -> farms(id)
- FK financial_transactions(category_id) -> financial_categories(id)
- FK financial_transactions(created_by_user_id) -> users(id)
- FK financial_transactions(updated_by_user_id) -> users(id)
- CHECK amount > 0

Índices:
- idx_financial_transactions_farm_id
- idx_financial_transactions_type
- idx_financial_transactions_status
- idx_financial_transactions_record_status
- idx_financial_transactions_transaction_date
- idx_financial_transactions_due_date
- idx_financial_transactions_category_id
- idx_financial_transactions_created_by_user_id

Regras:
- Toda transação pertence a uma fazenda.
- Toda transação registra quem cadastrou.
- created_by_user_id é obrigatório.
- updated_by_user_id é opcional.
- Remoção lógica usa record_status = DELETED.
- Cancelamento usa status = CANCELED.
- CANCELED não entra no resumo.
- DELETED não aparece por padrão.

---

# Seeds

## Primeiro ADMIN

Como não existe cadastro público, o primeiro ADMIN precisa ser criado por:
- migration/seed;
- inserção manual;
- runner apenas em profile local.

## Categorias padrão

Receitas:
- Venda de produção
- Serviço prestado
- Recebimento de contrato
- Outras receitas

Despesas:
- Insumos
- Frete
- Mão de obra
- Combustível
- Manutenção
- Financiamento
- Outras despesas
