# Regras da API

## Base URL

```text
/api
```

## Autenticação

A autenticação usa JWT em cookie.

Cookie:

```text
gd_session
```

Regras:
- Cookie HttpOnly.
- Cookie Secure em produção.
- JWT não retorna no body.
- Rotas privadas exigem cookie válido.
- Frontend deve usar credentials/include.
- Backend deve permitir credentials no CORS.
- Não usar Allowed-Origin * com cookie.

## Status HTTP padrão

| Situação | HTTP Status |
|---|---:|
| Sucesso em busca/listagem | 200 |
| Criação | 201 |
| Exclusão lógica sem body | 204 |
| Erro de validação | 400 |
| Não autenticado | 401 |
| Sem permissão | 403 |
| Recurso não encontrado | 404 |
| Erro inesperado | 500 |

---

# Auth API

## Endpoints

```text
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/session
POST /api/auth/change-password
```

## Login

Permissão: público.

Regras:
- Aceita apenas email e password.
- Usuário precisa existir.
- Senha precisa estar correta.
- Usuário precisa estar ACTIVE.
- INACTIVE não autentica.
- BLOCKED não autentica.
- JWT deve ser salvo no cookie gd_session.
- JWT não deve retornar no body.
- Erro de credenciais deve ser genérico.

## Logout

Permissão: usuário autenticado.

Regras:
- Remover cookie gd_session.
- Após logout, rotas privadas devem retornar 401.

## Session

Permissão: usuário autenticado.

Regras:
- Retornar dados básicos do usuário autenticado.
- Não retornar senha.
- Cookie ausente retorna 401.
- JWT inválido retorna 401.
- JWT expirado retorna 401.
- Usuário INACTIVE retorna 401.
- Usuário BLOCKED retorna 401.

## Change password

Permissão: usuário autenticado.

Regras:
- Senha atual deve estar correta.
- Nova senha deve ser diferente da atual.
- Nova senha deve seguir padrão mínimo.
- Nova senha deve ser criptografada.
- Após alteração, remover cookie e exigir novo login.

---

# User API

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

- GET /api/users/me: usuário autenticado.
- PUT /api/users/me: usuário autenticado; edita apenas name e document.
- GET /api/users: somente ADMIN.
- GET /api/users/{id}: somente ADMIN.
- POST /api/users: somente ADMIN.
- PATCH /api/users/{id}/status: somente ADMIN.
- PATCH /api/users/{id}/type: somente ADMIN.
- Password nunca retorna.
- User comum acessa apenas o próprio perfil por /me.

---

# Farm API

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

- POST /api/farms: somente ADMIN.
- GET /api/farms: ADMIN lista todas; USER lista apenas fazendas com vínculo ativo.
- GET /api/farms/{id}: ADMIN acessa qualquer; USER acessa apenas com vínculo ativo.
- PUT /api/farms/{id}: ADMIN ou PRODUCER da fazenda.
- PATCH /api/farms/{id}/status: somente ADMIN.
- DELETE /api/farms/{id}: somente ADMIN; inativação lógica.
- POST /api/farms/{farmId}/users: ADMIN ou PRODUCER da fazenda.
- GET /api/farms/{farmId}/users: ADMIN ou PRODUCER da fazenda.
- PATCH role: ADMIN ou PRODUCER da fazenda.
- DELETE farm user: inativa vínculo com FarmUserRole.INACTIVE.
- PRODUCER pode vincular apenas EMPLOYEE e ACCOUNTANT.
- PRODUCER não pode vincular ADMIN.
- PRODUCER não pode vincular outro PRODUCER.
- Não permitir vínculo duplicado.

---

# Financial API

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

## Regras de transação

- POST transaction: ADMIN, PRODUCER ou EMPLOYEE.
- GET transactions: ADMIN, PRODUCER, EMPLOYEE ou ACCOUNTANT.
- GET transaction by ID: ADMIN, PRODUCER, EMPLOYEE ou ACCOUNTANT.
- PUT transaction: ADMIN, PRODUCER ou EMPLOYEE.
- DELETE transaction: ADMIN, PRODUCER ou EMPLOYEE; exclusão lógica.
- PATCH pay: ADMIN, PRODUCER ou EMPLOYEE.
- PATCH cancel: ADMIN, PRODUCER ou EMPLOYEE.
- ACCOUNTANT apenas visualiza.
- INACTIVE não acessa.
- Toda transação pertence a uma fazenda.
- Toda transação registra createdByUser automaticamente.
- updatedByUser deve ser preenchido em alterações.
- Transação DELETED não aparece por padrão.
- Transação CANCELED não entra no resumo.

## Regras de categoria

- POST category: ADMIN ou PRODUCER.
- GET categories: ADMIN, PRODUCER, EMPLOYEE ou ACCOUNTANT.
- PUT category: ADMIN ou PRODUCER.
- DELETE category: ADMIN ou PRODUCER; inativação lógica.
- Categoria padrão pode ser usada por todas as fazendas.
- Categoria específica pertence a uma fazenda.
- Categoria INCOME só pode ser usada em transação INCOME.
- Categoria EXPENSE só pode ser usada em transação EXPENSE.

## Regras de resumo

- GET /summary: ADMIN, PRODUCER, EMPLOYEE ou ACCOUNTANT.
- GET /upcoming-bills: ADMIN, PRODUCER, EMPLOYEE ou ACCOUNTANT.
- INACTIVE não consulta.
- CANCELED não entra no resumo.
- DELETED não entra no resumo.
