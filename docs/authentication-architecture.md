# Arquitetura de Autenticação e Autorização

## Objetivo

Este documento define a arquitetura de autenticação e autorização da API backend do **Gestão Direta**.

A regra central é separar corretamente:

```text
Autenticação -> identifica quem é o usuário
Autorização global -> valida se é ADMIN ou USER
Autorização contextual -> valida o papel do usuário dentro da fazenda
Regra de negócio -> executa a ação solicitada
```

---

# 1. Modelo de segurança

A API deve usar:

```text
Spring Security
JWT
Cookie HttpOnly/Secure
@EnableMethodSecurity
@PreAuthorize
Autorização contextual por fazenda
```

Existem dois níveis de permissão:

```text
UserType
FarmUserRole
```

## UserType

Define o tipo global do usuário.

```text
ADMIN
USER
```

## FarmUserRole

Define o papel do usuário em uma fazenda específica.

```text
PRODUCER
EMPLOYEE
ACCOUNTANT
INACTIVE
```

---

# 2. Regra principal

O JWT deve responder apenas:

```text
Quem é o usuário?
Ele está autenticado?
Ele é ADMIN ou USER?
```

O JWT não deve responder:

```text
Ele é PRODUCER nessa fazenda?
Ele é EMPLOYEE nessa fazenda?
Ele pode alterar essa transação?
Ele pode acessar essa categoria?
```

Essas regras dependem do contexto da fazenda e devem ser consultadas no banco conforme o `farmId`, `transactionId` ou `categoryId`.

---

# 3. Authorities globais

No `Authentication`, carregar apenas:

```text
ROLE_ADMIN
ROLE_USER
```

Não criar authorities globais como:

```text
ROLE_PRODUCER
ROLE_EMPLOYEE
ROLE_ACCOUNTANT
```

Isso seria incorreto porque o mesmo usuário pode ter papéis diferentes em fazendas diferentes.

Exemplo:

```text
João pode ser PRODUCER na Fazenda A
João pode ser ACCOUNTANT na Fazenda B
João pode estar INACTIVE na Fazenda C
```

A regra correta é:

```text
ROLE_ADMIN / ROLE_USER ficam no Authentication
FarmUserRole é consultado no banco conforme a fazenda acessada
```

---

# 4. Responsabilidades

## JwtAuthenticationFilter

Responsável apenas por autenticação.

Deve:

```text
Ler o cookie gd_session
Extrair o JWT
Validar assinatura e expiração
Buscar o usuário
Verificar se o usuário está ACTIVE
Criar Authentication
Colocar Authentication no SecurityContext
```

Não deve:

```text
Validar permissão de fazenda
Validar permissão financeira
Consultar FarmUserRole para autorização
Decidir se pode criar transação
Decidir se pode editar fazenda
```

---

## SecurityConfig

Responsável por rotas públicas, rotas privadas e filtros.

Rotas públicas:

```text
POST /api/auth/login
/swagger-ui/**
/v3/api-docs/**
```

Rotas privadas:

```text
Todas as demais rotas
```

Regra:

```text
Rotas públicas -> permitAll
Rotas privadas -> authenticated
```

Não colocar regras detalhadas de fazenda ou financeiro no `SecurityConfig`, porque essas regras dependem de parâmetros como `farmId`, `transactionId` e `categoryId`.

---

## Method Security

Usar:

```text
@EnableMethodSecurity
@PreAuthorize
```

Exemplos:

```java
@PreAuthorize("hasRole('ADMIN')")
```

```java
@PreAuthorize("@farmAccess.canManageFarm(#farmId)")
```

```java
@PreAuthorize("@financialAccess.canCreateTransaction(#request.farmId)")
```

---

# 5. Estrutura recomendada

```text
auth/
├── security/
│   ├── JwtAuthenticationFilter
│   ├── JwtService
│   ├── SecurityConfig
│   ├── CustomUserDetails
│   └── CustomUserDetailsService
└── cookie/
    └── AuthCookieService

shared/
└── security/
    ├── CurrentUser
    └── SecurityUtils

farm/
└── security/
    └── FarmAccess

financial/
└── security/
    └── FinancialAccess
```

---

# 6. FarmAccess

O `FarmAccess` centraliza regras de autorização relacionadas à fazenda.

## Métodos sugeridos

```text
canViewFarm(farmId)
canManageFarm(farmId)
canCreateFarm()
canChangeFarmStatus()
canManageFarmUsers(farmId)
canChangeFarmUserRole(farmId, targetUserId, newRole)
canRemoveFarmUser(farmId, targetUserId)
```

## Regras por papel

### ADMIN

```text
Pode acessar qualquer fazenda
Pode criar fazenda
Pode editar qualquer fazenda
Pode inativar qualquer fazenda
Pode gerenciar usuários em qualquer fazenda
```

### PRODUCER

Na fazenda onde é `PRODUCER`:

```text
Pode visualizar a fazenda
Pode editar a fazenda
Pode gerenciar usuários da fazenda
Pode adicionar EMPLOYEE
Pode adicionar ACCOUNTANT
Pode remover/inativar EMPLOYEE
Pode remover/inativar ACCOUNTANT
Não pode adicionar ADMIN
Não pode adicionar outro PRODUCER
Não pode alterar outro PRODUCER
```

### EMPLOYEE

Na fazenda onde é `EMPLOYEE`:

```text
Pode visualizar a fazenda
Não pode editar dados principais da fazenda
Não pode gerenciar usuários
```

### ACCOUNTANT

Na fazenda onde é `ACCOUNTANT`:

```text
Pode visualizar a fazenda
Não pode editar dados principais da fazenda
Não pode gerenciar usuários
```

### INACTIVE

Na fazenda onde está `INACTIVE`:

```text
Não pode visualizar a fazenda
Não pode editar a fazenda
Não pode gerenciar usuários
```

---

# 7. FinancialAccess

O `FinancialAccess` centraliza regras financeiras.

## Métodos sugeridos

```text
canViewFinancialData(farmId)
canCreateTransaction(farmId)
canViewTransaction(transactionId)
canUpdateTransaction(transactionId)
canDeleteTransaction(transactionId)
canPayTransaction(transactionId)
canCancelTransaction(transactionId)
canManageCategory(farmId)
canManageCategoryByCategoryId(categoryId)
```

## Regras por papel

### ADMIN

```text
Pode gerenciar dados financeiros de qualquer fazenda
Pode criar transações
Pode editar transações
Pode excluir logicamente transações
Pode marcar transações como pagas
Pode cancelar transações
Pode criar categorias
Pode editar categorias
Pode inativar categorias
Pode visualizar resumos
```

### PRODUCER

Na fazenda onde é `PRODUCER`:

```text
Pode visualizar dados financeiros
Pode criar transações
Pode editar transações
Pode excluir logicamente transações
Pode marcar transações como pagas
Pode cancelar transações
Pode criar categorias
Pode editar categorias da fazenda
Pode inativar categorias da fazenda
Pode visualizar resumos
```

### EMPLOYEE

Na fazenda onde é `EMPLOYEE`:

```text
Pode visualizar dados financeiros
Pode criar transações
Pode editar transações
Pode excluir logicamente transações
Pode marcar transações como pagas
Pode cancelar transações
Pode visualizar categorias
Pode visualizar resumos
```

Não pode:

```text
Criar categorias
Editar categorias
Inativar categorias
```

### ACCOUNTANT

Na fazenda onde é `ACCOUNTANT`:

```text
Pode visualizar transações
Pode visualizar categorias
Pode visualizar resumos
Pode visualizar contas a vencer
```

Não pode:

```text
Criar transações
Editar transações
Excluir transações
Marcar transações como pagas
Cancelar transações
Criar categorias
Editar categorias
Inativar categorias
```

### INACTIVE

Na fazenda onde está `INACTIVE`:

```text
Não pode acessar dados financeiros
Não pode visualizar transações
Não pode visualizar resumos
Não pode visualizar categorias específicas da fazenda
```

---

# 8. Aplicação nos controllers

## UserController

Endpoints administrativos:

```java
@PreAuthorize("hasRole('ADMIN')")
```

Aplicar em:

```text
GET /api/users
GET /api/users/{id}
POST /api/users
PATCH /api/users/{id}/status
PATCH /api/users/{id}/type
```

Endpoints de próprio perfil:

```text
GET /api/users/me
PUT /api/users/me
```

Exigem usuário autenticado.

---

## FarmController

Criar fazenda:

```java
@PreAuthorize("hasRole('ADMIN')")
```

Buscar fazenda:

```java
@PreAuthorize("@farmAccess.canViewFarm(#id)")
```

Atualizar fazenda:

```java
@PreAuthorize("@farmAccess.canManageFarm(#id)")
```

Alterar status ou excluir fazenda:

```java
@PreAuthorize("hasRole('ADMIN')")
```

Listar fazendas:

```text
Apenas autenticado.
O service retorna:
ADMIN -> todas
USER -> apenas fazendas com vínculo ativo
```

---

## FarmUserController

Vincular usuário:

```java
@PreAuthorize("@farmAccess.canManageFarmUsers(#farmId)")
```

Listar usuários da fazenda:

```java
@PreAuthorize("@farmAccess.canManageFarmUsers(#farmId)")
```

Alterar papel:

```java
@PreAuthorize("@farmAccess.canChangeFarmUserRole(#farmId, #userId, #request.role)")
```

Remover/inativar usuário:

```java
@PreAuthorize("@farmAccess.canRemoveFarmUser(#farmId, #userId)")
```

---

## FinancialTransactionController

Criar transação:

```java
@PreAuthorize("@financialAccess.canCreateTransaction(#request.farmId)")
```

Listar transações por fazenda:

```java
@PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
```

Buscar transação:

```java
@PreAuthorize("@financialAccess.canViewTransaction(#id)")
```

Atualizar transação:

```java
@PreAuthorize("@financialAccess.canUpdateTransaction(#id)")
```

Excluir transação:

```java
@PreAuthorize("@financialAccess.canDeleteTransaction(#id)")
```

Marcar como paga:

```java
@PreAuthorize("@financialAccess.canPayTransaction(#id)")
```

Cancelar transação:

```java
@PreAuthorize("@financialAccess.canCancelTransaction(#id)")
```

---

## FinancialCategoryController

Criar categoria:

```java
@PreAuthorize("@financialAccess.canManageCategory(#request.farmId)")
```

Listar categorias:

```java
@PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
```

Atualizar categoria:

```java
@PreAuthorize("@financialAccess.canManageCategoryByCategoryId(#id)")
```

Excluir categoria:

```java
@PreAuthorize("@financialAccess.canManageCategoryByCategoryId(#id)")
```

---

## FinancialSummaryController

Resumo financeiro:

```java
@PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
```

Contas a vencer:

```java
@PreAuthorize("@financialAccess.canViewFinancialData(#farmId)")
```

---

# 9. Regras que permanecem nos services

Os services devem manter regras de negócio, não regras repetitivas de autorização.

Exemplos:

```text
Valor da transação deve ser maior que zero
Descrição é obrigatória
Transação CANCELED não pode ser paga
Categoria INCOME não pode ser usada em EXPENSE
Fazenda INACTIVE não pode receber transação
Não pode remover o último PRODUCER ativo da fazenda
Não pode criar vínculo duplicado
```

Regras que devem ir para Access classes:

```text
Quem pode acessar a fazenda
Quem pode editar a fazenda
Quem pode criar transação
Quem pode alterar papel do usuário na fazenda
Quem pode visualizar resumo financeiro
```

---

# 10. Fluxo de autenticação

## Login

```text
1. Cliente envia email e senha
2. AuthService valida credenciais
3. AuthService verifica UserStatus
4. JwtService gera JWT
5. AuthCookieService cria cookie gd_session
6. API retorna dados básicos do usuário sem token no body
```

## Requisição autenticada

```text
1. Cliente envia requisição com cookie gd_session
2. JwtAuthenticationFilter lê o cookie
3. JwtAuthenticationFilter valida JWT
4. Filtro busca usuário
5. Filtro cria Authentication com ROLE_ADMIN ou ROLE_USER
6. Controller recebe a requisição
7. @PreAuthorize valida permissão global ou contextual
8. Service executa regra de negócio
```

## Logout

```text
1. Cliente chama /api/auth/logout
2. AuthCookieService remove cookie gd_session
3. Próximas rotas privadas retornam 401
```

---

# 11. Fluxo de autorização contextual

Exemplo: atualizar transação.

```text
1. Usuário chama PUT /api/financial/transactions/{id}
2. JwtAuthenticationFilter autentica o usuário
3. @PreAuthorize chama financialAccess.canUpdateTransaction(id)
4. FinancialAccess busca a transação
5. FinancialAccess identifica a fazenda da transação
6. Se usuário for ADMIN, permite
7. Se usuário for USER, consulta FarmUserRole naquela fazenda
8. Permite se role for PRODUCER ou EMPLOYEE
9. Bloqueia se role for ACCOUNTANT ou INACTIVE
10. Service atualiza a transação e registra updatedByUser
```

---

# 12. Benefícios

## Separação correta

```text
JWT -> autenticação
ROLE_ADMIN/ROLE_USER -> autorização global
FarmUserRole -> autorização contextual
Service -> regra de negócio
```

## Evita erro de permissão global

Não existe `ROLE_PRODUCER` global.

Isso evita permitir acesso indevido quando o usuário é produtor em uma fazenda, mas contador em outra.

## Código mais limpo

Controllers ficam declarativos:

```java
@PreAuthorize("@financialAccess.canUpdateTransaction(#id)")
```

Services ficam focados em regra de negócio.

## Melhor testabilidade

É possível testar isoladamente:

```text
FarmAccess
FinancialAccess
SecurityConfig
JwtAuthenticationFilter
Services de negócio
Controllers protegidos
```

---

# 13. Testes obrigatórios

## Testes de autenticação

```text
Login com usuário ACTIVE deve autenticar
Login com INACTIVE deve retornar 401
Login com BLOCKED deve retornar 401
JWT inválido deve retornar 401
JWT expirado deve retornar 401
Cookie ausente deve retornar 401
Logout deve remover cookie
JWT não deve retornar no body
```

## Testes de autorização global

```text
ADMIN deve criar usuário
USER não deve criar usuário
ADMIN deve listar usuários
USER não deve listar usuários
ADMIN deve criar fazenda
USER não deve criar fazenda
```

## Testes de autorização por fazenda

```text
ADMIN deve acessar qualquer fazenda
PRODUCER deve acessar fazenda onde é PRODUCER
EMPLOYEE deve acessar fazenda onde é EMPLOYEE
ACCOUNTANT deve acessar fazenda onde é ACCOUNTANT
INACTIVE não deve acessar fazenda
USER sem vínculo não deve acessar fazenda
```

## Testes financeiros

```text
PRODUCER deve criar transação
EMPLOYEE deve criar transação
ACCOUNTANT não deve criar transação
INACTIVE não deve criar transação
PRODUCER deve editar transação
EMPLOYEE deve editar transação
ACCOUNTANT não deve editar transação
ACCOUNTANT deve visualizar resumo
INACTIVE não deve visualizar resumo
createdByUser deve ser preenchido automaticamente
updatedByUser deve ser preenchido automaticamente
```

## Testes de regressão

```text
Não deve existir cadastro público
JWT não deve retornar no body
Não deve existir ROLE_PRODUCER global
Não deve existir ROLE_EMPLOYEE global
Não deve existir ROLE_ACCOUNTANT global
Não deve aceitar createdByUserId no request
Não deve aceitar updatedByUserId no request
```

---

# 14. Regras para implementação com Codex

Ao refatorar a autorização:

```text
1. Não remover testes.
2. Não desabilitar testes.
3. Não enfraquecer asserts.
4. Não usar -DskipTests.
5. Criar plano antes de alterar.
6. Refatorar autenticação separada da autorização.
7. Remover roles de fazenda como authorities globais.
8. Criar FarmAccess.
9. Criar FinancialAccess.
10. Aplicar @PreAuthorize.
11. Rodar ./mvnw test.
```

---

# 15. Resumo final

A arquitetura final deve ser:

```text
SecurityConfig
-> define rotas públicas e privadas

JwtAuthenticationFilter
-> autentica pelo cookie gd_session

Authentication
-> contém ROLE_ADMIN ou ROLE_USER

@PreAuthorize
-> protege métodos

FarmAccess
-> valida permissões por fazenda

FinancialAccess
-> valida permissões financeiras

Services
-> executam regras de negócio
```

Regra mais importante:

```text
ADMIN é global.
USER depende de vínculo com fazenda.
Papéis de fazenda nunca devem ser authorities globais.
```
