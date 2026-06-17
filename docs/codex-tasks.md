# Tarefas para Codex

## Objetivo

Definir a ordem de implementação do MVP do Gestão Direta API.

O Codex deve seguir este plano e não avançar para módulos futuros sem solicitação.

## Comando padrão

```bash
./mvnw test
```

Quando houver falha, corrigir antes de avançar.

## Regra geral

Para cada tarefa:
1. Ler AGENTS.md.
2. Ler docs/architecture.md.
3. Ler docs/modules.md.
4. Ler docs/api-rules.md.
5. Ler docs/database.md.
6. Implementar apenas o escopo solicitado.
7. Criar ou atualizar testes.
8. Executar testes.
9. Explicar alterações.
10. Não avançar para outro módulo sem solicitação.

---

# Tarefa 1 — Configuração base

## Escopo

- Ajustar application.yaml.
- Garantir ddl-auto validate.
- Garantir Flyway habilitado.
- Garantir SpringDoc disponível.
- Garantir Docker Compose Support funcionando.
- Criar estrutura inicial de pacotes.

## Não fazer

- Não implementar entidades de negócio ainda.
- Não implementar autenticação ainda.

## Testes

- Projeto deve compilar.
- Contexto Spring deve subir, se aplicável.

---

# Tarefa 2 — Módulo shared

## Criar

```text
BaseEntity
BusinessException
ResourceNotFoundException
UnauthorizedException
ForbiddenException
ValidationException
ErrorResponse
GlobalExceptionHandler
ApiResponse
PageResponse
PaginationParams
SecurityUtils
AppConstants
CorsConfig
OpenApiConfig
ApplicationConfig
```

## Regras

- BusinessException retorna 400.
- ResourceNotFoundException retorna 404.
- UnauthorizedException retorna 401.
- ForbiddenException retorna 403.
- ValidationException retorna 400.
- Erro inesperado retorna 500.
- Não expor stack trace.
- BaseEntity controla createdAt e updatedAt.

## Testes

- BaseEntity.
- GlobalExceptionHandler.
- ErrorResponse.
- Erro inesperado sem stack trace.

---

# Tarefa 3 — Módulo user

## Criar

```text
User
UserType
UserStatus
UserController
UserService
UserRepository
UserMapper
UserCreateRequest
UserUpdateRequest
UserResponse
UserStatusUpdateRequest
UserTypeUpdateRequest
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

- Somente ADMIN cria usuário.
- Somente ADMIN lista usuário.
- Somente ADMIN busca usuário por ID.
- Somente ADMIN altera status.
- Somente ADMIN altera tipo.
- Usuário autenticado acessa e edita o próprio perfil por /me.
- Campos editáveis em /me: name e document.
- Não retornar password.
- Criptografar senha.
- Usuário criado inicia ACTIVE.

## Migration

```text
V1__create_users_table.sql
```

## Testes

- Criar USER por ADMIN.
- Criar ADMIN por ADMIN.
- Impedir criação por USER.
- Impedir e-mail duplicado.
- Impedir retorno de senha.
- Testar /me.
- Testar status.
- Testar tipo.
- Testar usuário inexistente.

---

# Tarefa 4 — Módulo auth

## Criar

```text
AuthController
AuthService
LoginRequest
AuthResponse
AuthUserResponse
ChangePasswordRequest
JwtService
JwtAuthenticationFilter
SecurityConfig
CustomUserDetails
CustomUserDetailsService
AuthCookieService
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
- Apenas ACTIVE autentica.
- INACTIVE e BLOCKED não autenticam.
- Usar Auth0 Java JWT.
- Salvar JWT no cookie gd_session.
- Cookie HttpOnly.
- Cookie Secure em produção.
- JWT não retorna no body.
- Logout remove cookie.
- Session retorna usuário autenticado.
- Change password exige senha atual correta.
- Change password remove cookie.

## JWT claims

```text
sub: userId
email
userType
iat
exp
```

## Testes

- Login com usuário ativo.
- Bloquear INACTIVE.
- Bloquear BLOCKED.
- Bloquear senha incorreta.
- Gerar cookie gd_session.
- Não retornar JWT no body.
- Logout remove cookie.
- Session com cookie válido.
- Session sem cookie retorna 401.
- Troca de senha.
- Remover cookie após trocar senha.

---

# Tarefa 5 — Módulo farm

## Criar

```text
Farm
FarmUser
FarmStatus
ProductionType
FarmUserRole
FarmController
FarmUserController
FarmService
FarmUserService
FarmRepository
FarmUserRepository
FarmMapper
FarmUserMapper
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

## Regras

- ADMIN cria fazenda.
- Fazenda inicia ACTIVE.
- ADMIN acessa todas.
- USER acessa apenas fazendas com vínculo ativo.
- INACTIVE não acessa.
- ADMIN vincula qualquer USER.
- PRODUCER vincula apenas EMPLOYEE e ACCOUNTANT.
- PRODUCER não vincula PRODUCER.
- PRODUCER não vincula ADMIN.
- Remover vínculo altera role para INACTIVE.
- Não excluir vínculo fisicamente.
- Não permitir vínculo duplicado.
- Uma fazenda deve ter pelo menos um PRODUCER ativo.

## Migrations

```text
V2__create_farms_table.sql
V3__create_farm_users_table.sql
```

## Testes

- ADMIN cria fazenda.
- USER não cria fazenda.
- ADMIN lista todas.
- USER lista vinculadas.
- INACTIVE não lista.
- PRODUCER edita fazenda.
- EMPLOYEE não edita.
- ACCOUNTANT não edita.
- ADMIN vincula usuário.
- PRODUCER vincula EMPLOYEE.
- PRODUCER vincula ACCOUNTANT.
- PRODUCER não vincula PRODUCER.
- PRODUCER não vincula ADMIN.
- Remoção altera role para INACTIVE.

---

# Tarefa 6 — Módulo financial

## Criar

```text
FinancialTransaction
FinancialCategory
TransactionType
PaymentStatus
PaymentMethod
FinancialRecordStatus
FinancialCategoryStatus
FinancialTransactionController
FinancialCategoryController
FinancialSummaryController
FinancialTransactionService
FinancialCategoryService
FinancialSummaryService
FinancialTransactionRepository
FinancialCategoryRepository
FinancialTransactionMapper
FinancialCategoryMapper
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
- Toda transação registra createdByUser automaticamente.
- Alterações registram updatedByUser.
- Valor deve ser maior que zero.
- Descrição é obrigatória.
- Data da transação é obrigatória.
- DELETE é lógico com recordStatus DELETED.
- DELETED não aparece por padrão.
- CANCELED não entra no resumo.
- ADMIN gerencia qualquer fazenda.
- PRODUCER gerencia onde é PRODUCER.
- EMPLOYEE cria, edita e exclui onde é EMPLOYEE.
- ACCOUNTANT apenas visualiza.
- INACTIVE não acessa dados financeiros.
- Categoria INCOME só pode ser usada em transação INCOME.
- Categoria EXPENSE só pode ser usada em transação EXPENSE.

## Migrations

```text
V4__create_financial_categories_table.sql
V5__create_financial_transactions_table.sql
V6__insert_default_financial_categories.sql
```

## Testes

- Criar transação como ADMIN.
- Criar como PRODUCER.
- Criar como EMPLOYEE.
- Bloquear ACCOUNTANT.
- Bloquear INACTIVE.
- Registrar createdByUser.
- Registrar updatedByUser.
- Impedir valor inválido.
- Impedir descrição vazia.
- Excluir logicamente.
- Não retornar DELETED por padrão.
- Marcar como paga.
- Cancelar.
- Criar categoria.
- Impedir categoria incompatível.
- Calcular resumo.
- Listar contas a vencer.

---

# Tarefa 7 — Revisão geral

## Verificar

- Permissões entre ADMIN, USER, PRODUCER, EMPLOYEE, ACCOUNTANT e INACTIVE.
- Login com cookie.
- CORS com credentials.
- Migrations completas.
- Testes passando.
- Swagger acessível.
- Nenhum endpoint retorna senha.
- Nenhum endpoint retorna JWT no body.
- Transações registram usuário autenticado.
- Exclusões lógicas funcionando.

## Executar

```bash
./mvnw test
```
