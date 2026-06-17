# Módulos do MVP

## Visão geral

Módulos:

```text
shared
user
auth
farm
financial
```

---

# 1. Módulo shared

## Objetivo

Centralizar recursos técnicos reutilizáveis.

## Estrutura sugerida

```text
shared/
├── audit/
│   └── BaseEntity
├── config/
│   ├── CorsConfig
│   ├── OpenApiConfig
│   └── ApplicationConfig
├── exception/
│   ├── BusinessException
│   ├── ResourceNotFoundException
│   ├── UnauthorizedException
│   ├── ForbiddenException
│   ├── ValidationException
│   ├── ErrorResponse
│   └── GlobalExceptionHandler
├── response/
│   ├── ApiResponse
│   └── PageResponse
├── pagination/
│   └── PaginationParams
├── security/
│   └── SecurityUtils
└── constant/
    └── AppConstants
```

## BaseEntity

| Campo | Obrigatório | Descrição |
|---|---:|---|
| createdAt | Sim | Data de criação |
| updatedAt | Não | Data da última atualização |

---

# 2. Módulo user

## Objetivo

Gerenciar dados globais do usuário.

## Tabela

```text
users
```

## Entidade

```text
User
```

## Campos

| Campo | Obrigatório | Descrição |
|---|---:|---|
| id | Sim | Identificador único |
| name | Sim | Nome completo |
| email | Sim | E-mail usado para login |
| password | Sim | Senha criptografada |
| document | Não | CPF ou CNPJ |
| userType | Sim | Tipo global do usuário |
| status | Sim | Status global da conta |
| createdAt | Sim | Data de criação |
| updatedAt | Não | Data da última atualização |

## Enums

```text
UserType: ADMIN, USER
UserStatus: ACTIVE, INACTIVE, BLOCKED
```

## Estrutura

```text
user/
├── controller/
│   └── UserController
├── dto/
│   ├── UserResponse
│   ├── UserUpdateRequest
│   ├── UserCreateRequest
│   ├── UserStatusUpdateRequest
│   └── UserTypeUpdateRequest
├── entity/
│   └── User
├── enumeration/
│   ├── UserType
│   └── UserStatus
├── mapper/
│   └── UserMapper
├── repository/
│   └── UserRepository
└── service/
    └── UserService
```

## Endpoints

```text
GET    /api/users/me
PUT    /api/users/me
GET    /api/users
GET    /api/users/{id}
POST   /api/users
PATCH  /api/users/{id}/status
PATCH  /api/users/{id}/type
```

## Regras

- Somente ADMIN cria usuários.
- Somente ADMIN lista usuários.
- Somente ADMIN busca usuário por ID.
- Somente ADMIN altera UserStatus.
- Somente ADMIN altera UserType.
- USER só acessa o próprio perfil em /api/users/me.
- Senha nunca retorna em response.
- Primeiro ADMIN deve ser criado por seed/migration ou manualmente.

---

# 3. Módulo auth

## Objetivo

Autenticar usuários existentes.

## Entidade própria

Não possui entidade própria no MVP.

Usa:

```text
User
```

## Estrutura

```text
auth/
├── controller/
│   └── AuthController
├── dto/
│   ├── LoginRequest
│   ├── AuthResponse
│   ├── AuthUserResponse
│   └── ChangePasswordRequest
├── service/
│   └── AuthService
├── security/
│   ├── JwtService
│   ├── JwtAuthenticationFilter
│   ├── SecurityConfig
│   ├── CustomUserDetails
│   └── CustomUserDetailsService
└── cookie/
    └── AuthCookieService
```

## Endpoints

```text
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/session
POST /api/auth/change-password
```

## Regras

- Não existe cadastro público.
- Login usa email e password.
- Apenas usuário ACTIVE pode autenticar.
- INACTIVE e BLOCKED não autenticam.
- JWT fica no cookie gd_session.
- JWT não retorna no body.
- Logout remove cookie.
- Troca de senha remove cookie e exige novo login.

---

# 4. Módulo farm

## Objetivo

Gerenciar fazendas e vínculos entre usuários e fazendas.

## Entidades

```text
Farm
FarmUser
```

## Farm

Tabela:

```text
farms
```

| Campo | Obrigatório | Descrição |
|---|---:|---|
| id | Sim | Identificador único |
| name | Sim | Nome da fazenda |
| document | Não | Documento da fazenda |
| city | Não | Cidade |
| state | Não | Estado |
| totalArea | Não | Área total |
| productionType | Não | Tipo de produção |
| status | Sim | Status da fazenda |
| createdAt | Sim | Data de criação |
| updatedAt | Não | Data da última atualização |

## FarmUser

Tabela:

```text
farm_users
```

| Campo | Obrigatório | Descrição |
|---|---:|---|
| id | Sim | Identificador único |
| farmId | Sim | Fazenda vinculada |
| userId | Sim | Usuário vinculado |
| role | Sim | Papel do usuário na fazenda |
| createdAt | Sim | Data de criação |
| updatedAt | Não | Data da última atualização |

## Enums

```text
FarmStatus: ACTIVE, INACTIVE
ProductionType: AGRICULTURE, LIVESTOCK, MIXED, OTHER
FarmUserRole: PRODUCER, EMPLOYEE, ACCOUNTANT, INACTIVE
```

## Estrutura

```text
farm/
├── controller/
│   ├── FarmController
│   └── FarmUserController
├── dto/
│   ├── FarmRequest
│   ├── FarmResponse
│   ├── FarmUpdateRequest
│   ├── FarmUserRequest
│   ├── FarmUserResponse
│   └── FarmUserRoleUpdateRequest
├── entity/
│   ├── Farm
│   └── FarmUser
├── enumeration/
│   ├── FarmStatus
│   ├── FarmUserRole
│   └── ProductionType
├── mapper/
│   ├── FarmMapper
│   └── FarmUserMapper
├── repository/
│   ├── FarmRepository
│   └── FarmUserRepository
└── service/
    ├── FarmService
    └── FarmUserService
```

## Endpoints

```text
POST   /api/farms
GET    /api/farms
GET    /api/farms/{id}
PUT    /api/farms/{id}
PATCH  /api/farms/{id}/status
DELETE /api/farms/{id}

POST   /api/farms/{farmId}/users
GET    /api/farms/{farmId}/users
PATCH  /api/farms/{farmId}/users/{userId}/role
DELETE /api/farms/{farmId}/users/{userId}
```

---

# 5. Módulo financial

## Objetivo

Gerenciar movimentações, categorias, resumo financeiro e contas a vencer.

## Entidades

```text
FinancialTransaction
FinancialCategory
```

## FinancialTransaction

Tabela:

```text
financial_transactions
```

| Campo | Obrigatório | Descrição |
|---|---:|---|
| id | Sim | Identificador único |
| description | Sim | Descrição da movimentação |
| amount | Sim | Valor da transação |
| type | Sim | Tipo da transação |
| status | Sim | Status de pagamento |
| paymentMethod | Não | Forma de pagamento |
| transactionDate | Sim | Data da movimentação |
| dueDate | Não | Data de vencimento |
| paidAt | Não | Data de pagamento |
| notes | Não | Observações |
| farmId | Sim | Fazenda da transação |
| categoryId | Não | Categoria financeira |
| createdByUserId | Sim | Usuário que cadastrou |
| updatedByUserId | Não | Último usuário que alterou |
| recordStatus | Sim | Status do registro |
| createdAt | Sim | Data de criação |
| updatedAt | Não | Data da última atualização |

## FinancialCategory

Tabela:

```text
financial_categories
```

| Campo | Obrigatório | Descrição |
|---|---:|---|
| id | Sim | Identificador único |
| name | Sim | Nome da categoria |
| type | Sim | Tipo da categoria |
| color | Não | Cor da interface |
| icon | Não | Ícone da interface |
| farmId | Não | Fazenda dona da categoria |
| isDefault | Sim | Indica se é categoria padrão |
| status | Sim | Status da categoria |
| createdAt | Sim | Data de criação |
| updatedAt | Não | Data da última atualização |

## Enums

```text
TransactionType: INCOME, EXPENSE
PaymentStatus: PENDING, PAID, OVERDUE, CANCELED
PaymentMethod: PIX, CASH, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, BOLETO, CHECK, OTHER
FinancialRecordStatus: ACTIVE, DELETED
FinancialCategoryStatus: ACTIVE, INACTIVE
```

## Estrutura

```text
financial/
├── controller/
│   ├── FinancialTransactionController
│   ├── FinancialCategoryController
│   └── FinancialSummaryController
├── dto/
│   ├── FinancialTransactionRequest
│   ├── FinancialTransactionUpdateRequest
│   ├── FinancialTransactionResponse
│   ├── FinancialCategoryRequest
│   ├── FinancialCategoryResponse
│   ├── FinancialSummaryResponse
│   ├── UpcomingBillResponse
│   └── PayTransactionRequest
├── entity/
│   ├── FinancialTransaction
│   └── FinancialCategory
├── enumeration/
│   ├── TransactionType
│   ├── PaymentStatus
│   ├── PaymentMethod
│   ├── FinancialRecordStatus
│   └── FinancialCategoryStatus
├── mapper/
│   ├── FinancialTransactionMapper
│   └── FinancialCategoryMapper
├── repository/
│   ├── FinancialTransactionRepository
│   └── FinancialCategoryRepository
└── service/
    ├── FinancialTransactionService
    ├── FinancialCategoryService
    └── FinancialSummaryService
```

## Endpoints

```text
POST   /api/financial/transactions
GET    /api/financial/transactions
GET    /api/financial/transactions/{id}
PUT    /api/financial/transactions/{id}
DELETE /api/financial/transactions/{id}
PATCH  /api/financial/transactions/{id}/pay
PATCH  /api/financial/transactions/{id}/cancel

POST   /api/financial/categories
GET    /api/financial/categories
GET    /api/financial/categories/{id}
PUT    /api/financial/categories/{id}
DELETE /api/financial/categories/{id}

GET    /api/financial/summary
GET    /api/financial/upcoming-bills
```

## Regras

- Toda transação pertence a uma fazenda.
- Toda transação registra automaticamente o usuário autenticado que cadastrou.
- createdByUserId não vem do frontend.
- updatedByUserId não vem do frontend.
- DELETE de transação é lógico usando FinancialRecordStatus.DELETED.
- ACCOUNTANT apenas visualiza.
- INACTIVE não acessa dados financeiros daquela fazenda.
