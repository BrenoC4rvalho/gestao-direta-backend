# Documentação da API Backend

## Visão geral

Esta documentação descreve a API backend do Gestão Direta, sistema de gestão financeira voltado ao agronegócio.

A stack encontrada no backend é Java 21, Spring Boot, Spring Security, JWT com Auth0 Java JWT, cookie HttpOnly, Spring Data JPA, PostgreSQL, Flyway, Bean Validation e SpringDoc OpenAPI.

A API é REST, não possui versionamento explícito e usa o context path `/api`. Portanto, as rotas documentadas já aparecem com o prefixo completo, por exemplo `/api/auth/login`.

Respostas paginadas usam o formato:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Não há controllers encontrados para Relatórios, Crédito Rural ou WhatsApp/Webhook no código atual.

## Autenticação

`POST /api/auth/login` é público. Todos os demais endpoints exigem autenticação, exceto Swagger/OpenAPI (`/api/swagger-ui/**`, `/api/swagger-ui.html` e `/api/v3/api-docs/**`).

O JWT é gravado no cookie `gd_session` e não é retornado no body da resposta. O cookie é `HttpOnly`, `path=/`, `SameSite=Lax` por padrão e `Secure=false` por padrão via configuração. Em produção, `Secure` deve ser habilitado por variável de ambiente.

O frontend deve enviar cookies nas chamadas autenticadas usando `credentials: "include"`.

JWT inválido, JWT ausente em rota protegida, JWT expirado ou usuário com status `INACTIVE` ou `BLOCKED` resulta em `401 Unauthorized`.

## Formatos comuns

### Paginação

Endpoints paginados aceitam os query params:

```json
{
  "page": 0,
  "size": 10,
  "sort": "id",
  "direction": "ASC"
}
```

Defaults:

- `page=0`
- `size=10`
- `sort=id`
- `direction=ASC`
- tamanho máximo normalizado para `100`

### Erro padrão

```json
{
  "timestamp": "2026-06-21T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/users",
  "details": ["email: must be a well-formed email address"]
}
```

### Enums

- `UserType`: `ADMIN`, `USER`
- `UserStatus`: `ACTIVE`, `INACTIVE`, `BLOCKED`
- `FarmStatus`: `ACTIVE`, `INACTIVE`
- `FarmUserRole`: `PRODUCER`, `EMPLOYEE`, `ACCOUNTANT`, `INACTIVE`
- `ProductionType`: `AGRICULTURE`, `LIVESTOCK`, `MIXED`, `OTHER`
- `ProductionActivityStatus`: `ACTIVE`, `INACTIVE`
- `HarvestSeasonStatus`: `PLANNED`, `IN_PROGRESS`, `FINISHED`, `INACTIVE`
- `TransactionType`: `INCOME`, `EXPENSE`
- `PaymentStatus`: `PENDING`, `PAID`, `OVERDUE`, `CANCELED`
- `PaymentMethod`: `PIX`, `CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `BANK_TRANSFER`, `BOLETO`, `CHECK`, `OTHER`
- `FinancialRecordStatus`: `ACTIVE`, `DELETED`
- `FinancialCategoryStatus`: `ACTIVE`, `INACTIVE`
- `FinancialAgendaStatusFilter`: `ALL`, `PENDING`, `OVERDUE`
- `FinancialAgendaTypeFilter`: `ALL`, `RECEIVABLE`, `PAYABLE`
- `FinancialAgendaStatus`: `PENDING`, `OVERDUE`
- `FinancialAgendaType`: `RECEIVABLE`, `PAYABLE`

### Regras importantes

- `ADMIN` administra usuários, fazendas e categorias financeiras por fazenda.
- `USER` depende de vínculo ativo com fazenda.
- `FarmUserRole.INACTIVE` não permite acesso à fazenda.
- `GET /api/farms/{farmId}/access` retorna as permissões calculadas do usuário autenticado para a fazenda selecionada.
- `PRODUCER` gerencia fazenda, vínculos de usuários e categorias da fazenda.
- `EMPLOYEE` pode gerenciar movimentações financeiras.
- `ACCOUNTANT` pode consultar dados financeiros, mas não gerenciar.
- `ADMIN` gerencia atividades produtivas globais.
- `PRODUCER` gerencia safras apenas em fazenda ativa onde possui vínculo ativo.
- `EMPLOYEE` e `ACCOUNTANT` apenas consultam safras.
- Categorias financeiras sempre pertencem a uma fazenda; `ADMIN` ou `PRODUCER` podem gerenciá-las conforme permissão da fazenda.
- Categoria não default exige `farmId`.
- Fazenda inativa não pode receber nova safra.
- Atividade produtiva inativa não pode ser usada em nova safra.
- Transações exigem fazenda ativa.
- Transações registram o usuário autenticado como criador no backend.
- `createdByUserId` e `updatedByUserId` não são aceitos no request.
- Exclusão de fazenda é lógica por status `INACTIVE`.
- Exclusão de atividade produtiva é lógica por status `INACTIVE`.
- Exclusão de safra é lógica por status `INACTIVE`.
- Exclusão de transação é lógica por `recordStatus=DELETED`.
- Exclusão de categoria é lógica por status `INACTIVE`.

## Jobs agendados

### Atualização de movimentações vencidas

O backend executa diariamente um job para marcar contas vencidas.

- Cron: `0 0 0 * * *`
- Timezone: `America/Sao_Paulo`
- Regra de vencimento: `dueDate < hoje`
- Registros atualizados: movimentações `EXPENSE`, `PENDING`, `ACTIVE`, com `dueDate` preenchido e anterior à data atual.
- Registros não atualizados: `INCOME`, `PAID`, `CANCELED`, `OVERDUE`, `DELETED`, `dueDate=null`, `dueDate=hoje` ou `dueDate>hoje`.

## Endpoints

## Messaging / Telegram

`POST /api/webhooks/messaging/telegram` is public only at the Spring Security layer and requires `X-Telegram-Bot-Api-Secret-Token`. A valid private text update creates a PENDING account and an INBOUND message. Repeated Telegram `update_id` values are idempotent; unsupported updates return 200 and are ignored.

All `/api/messaging/**` endpoints require ADMIN: `POST /api/messaging/telegram/messages` sends `{ "messagingAccountId": 10, "content": "Mensagem" }` only to ACTIVE Telegram accounts; `PATCH /api/messaging/accounts/{id}/status` updates account status; `GET /api/messaging/accounts`, `GET /api/messaging/messages`, `GET /api/messaging/conversations`, `GET /api/messaging/conversations/{id}`, and `GET /api/messaging/telegram/status` provide administrative queries and diagnosis.

`GET /api/messaging/messages` uses the standard pagination (`page`, `size`, `sort`, `direction`) and accepts optional `messagingConversationId`, `messageDirection` (`INBOUND` or `OUTBOUND`) and message `status`. Filters are combined with AND, for example: `/api/messaging/messages?messagingConversationId=10&messageDirection=INBOUND&status=PROCESSED&page=0&size=20`. Each item includes `messagingConversationId`; raw webhook payloads, tokens and credentials are never returned.

Changing an ACTIVE messaging account to INACTIVE or BLOCKED cancels its ACTIVE and WAITING_FARM_SELECTION conversations, clearing their selected farm and step while preserving messages. Reactivating an account never reopens historical conversations. The administrative PENDING-to-ACTIVE transition remains forbidden.

Telegram linking has independent limits per verification code and per Telegram account. An account accepts at most five invalid link attempts in a 15-minute window and is temporarily blocked for 15 minutes after reaching that limit. The response during this block is generic and a successful link clears the account attempt state.

Configure `TELEGRAM_ENABLED`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, and optional `TELEGRAM_API_BASE_URL`. Create the bot with BotFather, expose HTTPS, register the webhook with its secret, send a first text, create a link code in Gestão Direta, and send `/vincular CODIGO`. Credentials and raw payloads are never returned.


## IA

### POST /api/ai/transactions/parse

Interpreta um texto livre em pt-BR e retorna uma sugestao estruturada de movimentacao financeira. Este endpoint nao salva movimentacao no banco, nao cria `FinancialTransaction`, nao altera categoria, safra ou fazenda e nao deve ser usado como confirmacao de lancamento.

**Permissao:** `ADMIN` pode usar em fazenda ativa. `PRODUCER` e `EMPLOYEE` podem usar na fazenda ativa onde possuem vinculo ativo. `ACCOUNTANT`, vinculo `INACTIVE`, usuario sem vinculo, usuario `INACTIVE` ou `BLOCKED` nao podem usar.

Request:

```json
{
  "farmId": 19,
  "text": "paguei 250 reais de adubo para a safra de milho ontem no pix"
}
```

Validacoes:

- `farmId` e obrigatorio.
- `text` e obrigatorio, nao pode ser vazio e aceita no maximo 2000 caracteres.

Response `200 OK`:

```json
{
  "farmId": 19,
  "type": "EXPENSE",
  "amount": 250.00,
  "description": "Adubo",
  "transactionDate": "2026-07-06",
  "dueDate": null,
  "paymentStatus": "PAID",
  "paymentMethod": "PIX",
  "categoryName": "Insumos",
  "harvestSeasonName": "Milho",
  "confidence": 0.87,
  "missingFields": [],
  "warnings": []
}
```

Erros principais:

- `400 Bad Request` para body invalido.
- `401 Unauthorized` para usuario nao autenticado.
- `403 Forbidden` para usuario sem permissao na fazenda ou `ACCOUNTANT`.
- `422 Unprocessable Entity` quando a IA nao retorna JSON valido ou retorna campos/enums invalidos.
- `503 Service Unavailable` quando o modelo configurado nao esta disponivel no Ollama.

Configuracao do provider:

```properties
APP_AI_PROVIDER=ollama
APP_AI_OLLAMA_BASE_URL=http://localhost:11434
APP_AI_OLLAMA_MODEL=llama3.1:8b
```

Quando o backend roda dentro do Docker Compose, use `APP_AI_OLLAMA_BASE_URL=http://ollama:11434`. Quando o backend roda local fora do Docker, use `http://localhost:11434`. Para trocar de provider futuramente, implemente `AiTextGenerationClient` e selecione por `app.ai.provider`.

Comandos Ollama:

```bash
docker compose up -d ollama
docker exec -it gestao-direta-ollama ollama pull llama3.1:8b
docker exec -it gestao-direta-ollama ollama list
```

Modelo menor opcional:

```bash
docker exec -it gestao-direta-ollama ollama pull llama3.2:3b
```

Para usar outro modelo, altere a configuracao:

```properties
app.ai.ollama.model=llama3.2:3b
```

ou a variavel de ambiente:

```properties
APP_AI_OLLAMA_MODEL=llama3.2:3b
```

Quando o modelo configurado nao foi baixado, a API retorna `503 Service Unavailable` com mensagem amigavel e detalhe do modelo configurado.

Execucao local com Spring:

```bash
docker compose up -d postgres ollama
./mvnw spring-boot:run
```

## Observabilidade

### `GET /api/system/status`

Endpoint publico de status simplificado da aplicacao. Sempre retorna HTTP 200 e nao expoe URL, usuario ou senha do banco, variaveis de ambiente ou detalhes de excecao.

Campos principais:

- `status`: `UP` quando a aplicacao e o banco estao disponiveis, ou `DEGRADED` quando a aplicacao responde, mas o banco esta indisponivel.
- `application`: nome da aplicacao.
- `profile`: profiles ativos separados por virgula, ou `default` quando nenhum profile estiver ativo.
- `database`: `UP` quando o banco responde ao health check, ou `DOWN` quando nao responde.
- `uptimeSeconds`: tempo de execucao da aplicacao em segundos.
- `timestamp`: data e hora da resposta.

### Actuator

- `GET /api/actuator/health`: publico, expoe o health check da aplicacao.
- `GET /api/actuator/info`: publico, expoe informacoes basicas da aplicacao.
- `GET /api/actuator/metrics`: restrito a usuarios com papel global `ADMIN`.

Significados:

- `UP`: componente disponivel.
- `DEGRADED`: aplicacao responde, mas algum componente essencial esta indisponivel.
- `DOWN`: componente indisponivel.

### POST /api/auth/login

**Descrição:**
Autentica um usuário ativo e grava o JWT no cookie `gd_session`.

**Autenticação:** Não
**Permissão:** Público.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "email": "admin@gestaodireta.com",
  "password": "Strong@123"
}
```

**Campos obrigatórios:**
- `email`
- `password`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "user": {
    "id": 1,
    "name": "Admin",
    "email": "admin@gestaodireta.com",
    "document": "00000000000",
    "userType": "ADMIN",
    "status": "ACTIVE"
  }
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para body inválido.
- `401 Unauthorized` para credenciais inválidas ou usuário inativo/bloqueado.

**Observações de regra de negócio:**
- O JWT não é retornado no body.
- A resposta inclui `Set-Cookie` para `gd_session`.

### POST /api/auth/logout

**Descrição:**
Encerra a sessão removendo o cookie de autenticação.

**Autenticação:** Sim
**Permissão:** Usuário autenticado.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- Nenhum.

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{}
```

**Possíveis erros/status HTTP:**
- `204 No Content` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.

**Observações de regra de negócio:**
- A resposta expira o cookie `gd_session`.

### GET /api/auth/session

**Descrição:**
Retorna os dados do usuário autenticado.

**Autenticação:** Sim
**Permissão:** Usuário autenticado.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- Nenhum.

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "user": {
    "id": 1,
    "name": "Admin",
    "email": "admin@gestaodireta.com",
    "document": "00000000000",
    "userType": "ADMIN",
    "status": "ACTIVE"
  }
}
```

**Possíveis erros/status HTTP:**
- `401 Unauthorized` para cookie ausente, inválido, expirado ou usuário não ativo.

**Observações de regra de negócio:**
- A sessão só é válida para usuário com status `ACTIVE`.

### POST /api/auth/change-password

**Descrição:**
Altera a senha do usuário autenticado e expira a sessão atual.

**Autenticação:** Sim
**Permissão:** Usuário autenticado.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "currentPassword": "Strong@123",
  "newPassword": "NewStrong@123"
}
```

**Campos obrigatórios:**
- `currentPassword`
- `newPassword`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{}
```

**Possíveis erros/status HTTP:**
- `204 No Content` em caso de sucesso.
- `400 Bad Request` para senha nova fraca ou igual à atual.
- `401 Unauthorized` para senha atual inválida, cookie inválido ou usuário não ativo.

**Observações de regra de negócio:**
- `newPassword` deve ter pelo menos 8 caracteres, letra maiúscula, letra minúscula, número e caractere especial.
- A resposta expira o cookie `gd_session`; o usuário deve autenticar novamente.

### GET /api/users/me

**Descrição:**
Retorna o usuário autenticado.

**Autenticação:** Sim
**Permissão:** Usuário autenticado.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- Nenhum.

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "name": "Admin",
  "email": "admin@gestaodireta.com",
  "document": "00000000000",
  "userType": "ADMIN",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `404 Not Found` se o usuário autenticado não existir mais.

**Observações de regra de negócio:**
- A senha nunca é retornada.

### PUT /api/users/me

**Descrição:**
Atualiza nome e documento do usuário autenticado.

**Autenticação:** Sim
**Permissão:** Usuário autenticado.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "name": "Admin Updated",
  "document": "11111111111"
}
```

**Campos obrigatórios:**
- `name`

**Campos opcionais:**
- `document`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "name": "Admin Updated",
  "email": "admin@gestaodireta.com",
  "document": "11111111111",
  "userType": "ADMIN",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para body inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `404 Not Found` se o usuário autenticado não existir mais.

**Observações de regra de negócio:**
- Não permite alterar email, senha, tipo ou status por este endpoint.

### GET /api/users

**Descrição:**
Lista usuários com paginação.

**Autenticação:** Sim
**Permissão:** Apenas `ADMIN`.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "page": 0,
  "size": 10,
  "sort": "id",
  "direction": "ASC",
  "search": "maria",
  "userType": "USER",
  "status": "ACTIVE",
  "statuses": ["ACTIVE", "BLOCKED"]
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- Nenhum.

**Campos opcionais:**
- `page`
- `size`
- `sort`
- `direction`
- `search`: filtra por nome ou email, sem diferenciar maiúsculas/minúsculas.
- `userType`: `ADMIN` ou `USER`.
- `status`: `ACTIVE`, `INACTIVE` ou `BLOCKED`.
- `statuses`: aceita query params repetidos, por exemplo `statuses=ACTIVE&statuses=BLOCKED`. Quando informado com valores válidos, tem prioridade sobre `status`.

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 2,
      "name": "User",
      "email": "user@gestaodireta.com",
      "document": "22222222222",
      "userType": "USER",
      "status": "ACTIVE",
      "createdAt": "2026-06-21T10:00:00",
      "updatedAt": "2026-06-21T10:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para enum inválido em filtros.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem papel `ADMIN`.

**Observações de regra de negócio:**
- A senha nunca é retornada.

### GET /api/users/search-by-email

**Descrição:**
Busca um usuário pelo e-mail exato para auxiliar o vínculo de usuários a uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN` ou `PRODUCER` ativo em pelo menos uma fazenda ativa.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "email": "usuario@email.com"
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `email`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 5,
  "name": "João Silva",
  "email": "joao@email.com",
  "document": "12345678900",
  "userType": "USER",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `200 OK` se encontrou.
- `400 Bad Request` para email ausente, vazio ou inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão para pesquisar por e-mail.
- `404 Not Found` se o usuário não existir.

**Observações de regra de negócio:**
- A busca é por e-mail exato; não existe busca parcial, `contains`, `like` ou autocomplete aberto.
- `GET /api/users` continua restrito a `ADMIN`.
- A senha nunca é retornada.
- O endpoint de vínculo continua validando se o usuário encontrado pode ser vinculado à fazenda.

### GET /api/users/{id}

**Descrição:**
Busca um usuário por id.

**Autenticação:** Sim
**Permissão:** Apenas `ADMIN`.

**Path params:**
```json
{
  "id": 2
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 2,
  "name": "User",
  "email": "user@gestaodireta.com",
  "document": "22222222222",
  "userType": "USER",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem papel `ADMIN`.
- `404 Not Found` se o usuário não existir.

**Observações de regra de negócio:**
- A senha nunca é retornada.

### PUT /api/users/{id}

**Descrição:**
Atualiza dados básicos de um usuário. Endpoint administrativo.

**Autenticação:** Sim
**Permissão:** Apenas `ADMIN`.

**Path params:**
```json
{
  "id": 2
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "name": "João Silva",
  "document": "12345678900"
}
```

**Campos obrigatórios:**
- `id`
- `name`

**Campos opcionais:**
- `document`

**Resposta de sucesso:**
```json
{
  "id": 2,
  "name": "João Silva",
  "email": "joao@email.com",
  "document": "12345678900",
  "userType": "USER",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para dados inválidos.
- `401 Unauthorized` para usuário não autenticado.
- `403 Forbidden` para usuário autenticado sem papel `ADMIN`.
- `404 Not Found` se o usuário não existir.

**Observações de regra de negócio:**
- Este endpoint não altera e-mail, senha, status ou tipo do usuário.
- Status e tipo possuem endpoints próprios.
- Senha possui endpoint próprio de reset administrativo.
- `name` é normalizado com `trim`.
- `document` é normalizado com `trim`; valor vazio é salvo como `null`.

### PATCH /api/users/{id}/reset-password

**Descrição:**
Permite que um `ADMIN` defina uma nova senha temporária para outro usuário.

**Autenticação:** Sim
**Permissão:** Apenas `ADMIN`.

**Path params:**
```json
{
  "id": 2
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "newPassword": "NewPassword@123"
}
```

**Campos obrigatórios:**
- `id`
- `newPassword`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 2,
  "name": "João Silva",
  "email": "joao@email.com",
  "document": "12345678900",
  "userType": "USER",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para senha inválida ou tentativa de resetar a própria senha.
- `401 Unauthorized` para usuário não autenticado.
- `403 Forbidden` para usuário autenticado sem papel `ADMIN`.
- `404 Not Found` se o usuário não existir.

**Observações de regra de negócio:**
- A senha não é retornada na resposta.
- A senha é armazenada criptografada.
- `newPassword` deve ter pelo menos 8 caracteres, letra maiúscula, letra minúscula, número e caractere especial.
- O `ADMIN` não deve usar este endpoint para alterar a própria senha; para isso existe `POST /api/auth/change-password`.
- Este endpoint não cria token, não envia e-mail e não gera senha automática.

### POST /api/users

**Descrição:**
Cria usuário. Não existe cadastro público.

**Autenticação:** Sim
**Permissão:** `ADMIN` ou `PRODUCER` ativo em pelo menos uma fazenda ativa.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "name": "User",
  "email": "user@gestaodireta.com",
  "password": "Strong@123",
  "document": "22222222222",
  "userType": "USER"
}
```

**Campos obrigatórios:**
- `name`
- `email`
- `password`
- `userType`

**Campos opcionais:**
- `document`

**Resposta de sucesso:**
```json
{
  "id": 2,
  "name": "User",
  "email": "user@gestaodireta.com",
  "document": "22222222222",
  "userType": "USER",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `201 Created` em caso de sucesso.
- `400 Bad Request` para body inválido ou email já cadastrado.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou para `PRODUCER` tentando criar `ADMIN`.

**Observações de regra de negócio:**
- `ADMIN` pode criar usuários `ADMIN` e `USER`.
- `PRODUCER` ativo em pelo menos uma fazenda ativa pode criar apenas usuários `USER`.
- `PRODUCER` não pode criar usuário `ADMIN`.
- `EMPLOYEE` e `ACCOUNTANT` não podem criar usuários.
- Usuário sem vínculo ativo como `PRODUCER` em fazenda ativa não pode criar usuários.
- Novo usuário é criado com status `ACTIVE`.
- A senha é armazenada criptografada e nunca é retornada.

### PATCH /api/users/{id}/status

**Descrição:**
Altera o status de um usuário.

**Autenticação:** Sim
**Permissão:** Apenas `ADMIN`.

**Path params:**
```json
{
  "id": 2
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "status": "BLOCKED"
}
```

**Campos obrigatórios:**
- `id`
- `status`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 2,
  "name": "User",
  "email": "user@gestaodireta.com",
  "document": "22222222222",
  "userType": "USER",
  "status": "BLOCKED",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para body inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem papel `ADMIN`.
- `404 Not Found` se o usuário não existir.

**Observações de regra de negócio:**
- Usuários `INACTIVE` ou `BLOCKED` não acessam o sistema.

### PATCH /api/users/{id}/type

**Descrição:**
Altera o tipo global de um usuário.

**Autenticação:** Sim
**Permissão:** Apenas `ADMIN`.

**Path params:**
```json
{
  "id": 2
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "userType": "ADMIN"
}
```

**Campos obrigatórios:**
- `id`
- `userType`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 2,
  "name": "User",
  "email": "user@gestaodireta.com",
  "document": "22222222222",
  "userType": "ADMIN",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para body inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem papel `ADMIN`.
- `404 Not Found` se o usuário não existir.

**Observações de regra de negócio:**
- `UserType` é papel global; não substitui `FarmUserRole`.

### POST /api/farms

**Descrição:**
Cria uma fazenda.

**Autenticação:** Sim
**Permissão:** Apenas `ADMIN`.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "name": "Fazenda Boa Safra",
  "document": "12345678000199",
  "city": "Ribeirao Preto",
  "state": "SP",
  "totalArea": 1500.50,
  "productionType": "AGRICULTURE"
}
```

**Campos obrigatórios:**
- `name`

**Campos opcionais:**
- `document`
- `city`
- `state`
- `totalArea`
- `productionType`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "name": "Fazenda Boa Safra",
  "document": "12345678000199",
  "city": "Ribeirao Preto",
  "state": "SP",
  "totalArea": 1500.50,
  "productionType": "AGRICULTURE",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `201 Created` em caso de sucesso.
- `400 Bad Request` para body inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem papel `ADMIN`.

**Observações de regra de negócio:**
- A fazenda é criada com status `ACTIVE`.

### GET /api/farms

**Descrição:**
Lista fazendas com paginação.

**Autenticação:** Sim
**Permissão:** `ADMIN` lista todas; `USER` lista fazendas ativas com vínculo ativo.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "page": 0,
  "size": 10,
  "sort": "id",
  "direction": "ASC",
  "search": "boa safra",
  "document": "12345678000199",
  "productionType": "AGRICULTURE",
  "productionTypes": ["AGRICULTURE", "LIVESTOCK"],
  "status": "ACTIVE"
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- Nenhum.

**Campos opcionais:**
- `page`
- `size`
- `sort`
- `direction`
- `search`: filtra por nome da fazenda, sem diferenciar maiúsculas/minúsculas.
- `document`: filtra por documento, ignorando máscara e caracteres não numéricos.
- `productionType`: `AGRICULTURE`, `LIVESTOCK`, `MIXED` ou `OTHER`.
- `productionTypes`: aceita query params repetidos, por exemplo `productionTypes=AGRICULTURE&productionTypes=LIVESTOCK`. Quando informado com valores válidos, tem prioridade sobre `productionType`.
- `status`: `ACTIVE` ou `INACTIVE`.

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 1,
      "name": "Fazenda Boa Safra",
      "document": "12345678000199",
      "city": "Ribeirao Preto",
      "state": "SP",
      "totalArea": 1500.50,
      "productionType": "AGRICULTURE",
      "status": "ACTIVE",
      "createdAt": "2026-06-21T10:00:00",
      "updatedAt": "2026-06-21T10:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para enum inválido em filtros.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` se o usuário autenticado não estiver ativo.

**Observações de regra de negócio:**
- `ADMIN` não precisa de vínculo com fazenda.
- `ADMIN` lista todas as fazendas conforme os filtros enviados.
- `USER` lista apenas fazendas `ACTIVE` com vínculo ativo; `status=INACTIVE` não libera fazendas inativas para usuário comum.
- `USER` com vínculo `INACTIVE` não enxerga a fazenda.

### GET /api/farms/{id}

**Descrição:**
Busca uma fazenda por id.

**Autenticação:** Sim
**Permissão:** `ADMIN` ou usuário com vínculo ativo na fazenda ativa.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "name": "Fazenda Boa Safra",
  "document": "12345678000199",
  "city": "Ribeirao Preto",
  "state": "SP",
  "totalArea": 1500.50,
  "productionType": "AGRICULTURE",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso à fazenda.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- `USER` precisa de fazenda ativa e vínculo diferente de `INACTIVE`.

### GET /api/farms/{farmId}/access

**Descrição:**
Retorna o contexto de acesso do usuário autenticado para uma fazenda, incluindo a role do usuário na fazenda e as permissões calculadas para a interface.

**Autenticação:** Sim
**Permissão:** Usuário autenticado. A permissão detalhada é calculada conforme `UserType`, vínculo com a fazenda e status da fazenda.

**Path params:**
```json
{
  "farmId": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso — ADMIN:**
```json
{
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "userId": 1,
  "userType": "ADMIN",
  "role": null,
  "permissions": {
    "canViewFarm": true,
    "canEditFarm": true,
    "canChangeFarmStatus": true,
    "canManageFarmUsers": true,
    "canViewFinancial": true,
    "canManageTransactions": true,
    "canManageCategories": true,
    "canManageGlobalCategories": true,
    "canCreateFarm": true
  }
}
```

**Resposta de sucesso — PRODUCER:**
```json
{
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "userId": 2,
  "userType": "USER",
  "role": "PRODUCER",
  "permissions": {
    "canViewFarm": true,
    "canEditFarm": true,
    "canChangeFarmStatus": false,
    "canManageFarmUsers": true,
    "canViewFinancial": true,
    "canManageTransactions": true,
    "canManageCategories": true,
    "canManageGlobalCategories": false,
    "canCreateFarm": false
  }
}
```

**Resposta de sucesso — EMPLOYEE:**
```json
{
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "userId": 3,
  "userType": "USER",
  "role": "EMPLOYEE",
  "permissions": {
    "canViewFarm": true,
    "canEditFarm": false,
    "canChangeFarmStatus": false,
    "canManageFarmUsers": false,
    "canViewFinancial": true,
    "canManageTransactions": true,
    "canManageCategories": false,
    "canManageGlobalCategories": false,
    "canCreateFarm": false
  }
}
```

**Resposta de sucesso — ACCOUNTANT:**
```json
{
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "userId": 4,
  "userType": "USER",
  "role": "ACCOUNTANT",
  "permissions": {
    "canViewFarm": true,
    "canEditFarm": false,
    "canChangeFarmStatus": false,
    "canManageFarmUsers": false,
    "canViewFinancial": true,
    "canManageTransactions": false,
    "canManageCategories": false,
    "canManageGlobalCategories": false,
    "canCreateFarm": false
  }
}
```

**Possíveis erros/status HTTP:**
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem vínculo ativo, vínculo `INACTIVE`, usuário inválido ou fazenda inativa para usuário comum.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- `ADMIN` não depende de vínculo com fazenda, pode acessar fazendas ativas e inativas e recebe todas as permissões como `true`.
- Para `ADMIN`, o campo `role` retorna `null`.
- `USER` depende de vínculo ativo e não acessa fazenda `INACTIVE`; vínculo `INACTIVE` ou ausência de vínculo resulta em `403 Forbidden`.
- `PRODUCER` pode visualizar e editar a fazenda, gerenciar vínculos, visualizar financeiro, gerenciar movimentações e categorias da fazenda. Não pode alterar o status da fazenda, criar fazenda ou gerenciar categorias de outras fazendas.
- `EMPLOYEE` pode visualizar a fazenda e o financeiro e gerenciar movimentações. Não pode editar a fazenda, gerenciar vínculos ou categorias, nem alterar o status da fazenda.
- `ACCOUNTANT` pode visualizar a fazenda e os dados financeiros. Não pode editar a fazenda, gerenciar vínculos, movimentações ou categorias, nem alterar o status da fazenda.
- O endpoint melhora a UX do frontend, mas não substitui a autorização aplicada em cada endpoint protegido do backend.

### PUT /api/farms/{id}

**Descrição:**
Atualiza dados cadastrais de uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN` ou `PRODUCER` da fazenda ativa.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "name": "Fazenda Boa Safra Atualizada",
  "document": "12345678000199",
  "city": "Ribeirao Preto",
  "state": "SP",
  "totalArea": 1600.00,
  "productionType": "MIXED"
}
```

**Campos obrigatórios:**
- `id`
- `name`

**Campos opcionais:**
- `document`
- `city`
- `state`
- `totalArea`
- `productionType`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "name": "Fazenda Boa Safra Atualizada",
  "document": "12345678000199",
  "city": "Ribeirao Preto",
  "state": "SP",
  "totalArea": 1600.00,
  "productionType": "MIXED",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para body inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão de gestão da fazenda.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- `EMPLOYEE` e `ACCOUNTANT` não gerenciam cadastro de fazenda.

### PATCH /api/farms/{id}/status

**Descrição:**
Altera o status de uma fazenda.

**Autenticação:** Sim
**Permissão:** Apenas `ADMIN`.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "status": "INACTIVE"
}
```

**Campos obrigatórios:**
- `id`
- `status`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "name": "Fazenda Boa Safra",
  "document": "12345678000199",
  "city": "Ribeirao Preto",
  "state": "SP",
  "totalArea": 1500.50,
  "productionType": "AGRICULTURE",
  "status": "INACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para body inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem papel `ADMIN`.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- Fazenda `INACTIVE` não permite acesso operacional de `USER`.

### DELETE /api/farms/{id}

**Descrição:**
Inativa uma fazenda.

**Autenticação:** Sim
**Permissão:** Apenas `ADMIN`.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{}
```

**Possíveis erros/status HTTP:**
- `204 No Content` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem papel `ADMIN`.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- A exclusão é lógica: o status da fazenda passa para `INACTIVE`.

### POST /api/farms/{farmId}/users

**Descrição:**
Cria vínculo entre usuário e fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN` ou `PRODUCER` da fazenda ativa, com restrições para alvo e papel.

**Path params:**
```json
{
  "farmId": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "userId": 2,
  "role": "EMPLOYEE"
}
```

**Campos obrigatórios:**
- `farmId`
- `userId`
- `role`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "userId": 2,
  "userName": "User",
  "userEmail": "user@gestaodireta.com",
  "role": "EMPLOYEE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `201 Created` em caso de sucesso.
- `400 Bad Request` para body inválido, fazenda inativa, usuário já vinculado ou usuário alvo inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão.
- `404 Not Found` se fazenda ou usuário não existir.

**Observações de regra de negócio:**
- Apenas usuários com `UserType.USER` podem ser vinculados.
- `PRODUCER` só pode vincular usuários ativos como `EMPLOYEE` ou `ACCOUNTANT`.
- `ADMIN` pode criar vínculos sem estar vinculado à fazenda.

### GET /api/farms/{farmId}/users

**Descrição:**
Lista vínculos de usuários de uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN` ou `PRODUCER` da fazenda ativa.

**Path params:**
```json
{
  "farmId": 1
}
```

**Query params:**
```json
{
  "page": 0,
  "size": 10,
  "sort": "id",
  "direction": "ASC",
  "search": "user",
  "role": "EMPLOYEE",
  "roles": ["EMPLOYEE", "ACCOUNTANT"]
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- `page`
- `size`
- `sort`
- `direction`
- `search`: filtra por nome ou email do usuário vinculado, sem diferenciar maiúsculas/minúsculas.
- `role`: `PRODUCER`, `EMPLOYEE`, `ACCOUNTANT` ou `INACTIVE`.
- `roles`: aceita query params repetidos, por exemplo `roles=EMPLOYEE&roles=ACCOUNTANT`. Quando informado com valores válidos, tem prioridade sobre `role`.

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 1,
      "farmId": 1,
      "farmName": "Fazenda Boa Safra",
      "userId": 2,
      "userName": "User",
      "userEmail": "user@gestaodireta.com",
      "role": "EMPLOYEE",
      "createdAt": "2026-06-21T10:00:00",
      "updatedAt": "2026-06-21T10:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para enum inválido em filtros.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- Gerenciamento de vínculos é permitido a `ADMIN` e `PRODUCER`.

### PATCH /api/farms/{farmId}/users/{userId}/role

**Descrição:**
Altera o papel de um usuário dentro da fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN` ou `PRODUCER` da fazenda ativa, com restrições para alvo e novo papel.

**Path params:**
```json
{
  "farmId": 1,
  "userId": 2
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "role": "ACCOUNTANT"
}
```

**Campos obrigatórios:**
- `farmId`
- `userId`
- `role`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "userId": 2,
  "userName": "User",
  "userEmail": "user@gestaodireta.com",
  "role": "ACCOUNTANT",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para body inválido ou tentativa de remover o último produtor ativo.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão.
- `404 Not Found` se o vínculo não existir.

**Observações de regra de negócio:**
- `PRODUCER` não pode promover outro usuário para `PRODUCER`.
- `PRODUCER` só pode alterar vínculos de `EMPLOYEE` ou `ACCOUNTANT`.
- A fazenda deve manter pelo menos um produtor ativo.

### DELETE /api/farms/{farmId}/users/{userId}

**Descrição:**
Inativa o vínculo de um usuário com uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN` ou `PRODUCER` da fazenda ativa, com restrições para alvo.

**Path params:**
```json
{
  "farmId": 1,
  "userId": 2
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`
- `userId`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{}
```

**Possíveis erros/status HTTP:**
- `204 No Content` em caso de sucesso.
- `400 Bad Request` para tentativa de remover o último produtor ativo.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão.
- `404 Not Found` se o vínculo não existir.

**Observações de regra de negócio:**
- A exclusão é lógica: o papel do vínculo passa para `INACTIVE`.
- `PRODUCER` só pode remover vínculos de `EMPLOYEE` ou `ACCOUNTANT`.


### POST /api/harvest/production-activities

**Descrição:**
Cria uma atividade produtiva para uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN` para fazenda ativa; `PRODUCER` da fazenda ativa com vínculo ativo.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "farmId": 1,
  "name": "Soja",
  "description": "Cultivo de soja"
}
```

**Campos obrigatórios:**
- `farmId`
- `name`

**Campos opcionais:**
- `description`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "name": "Soja",
  "description": "Cultivo de soja",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `201 Created` em caso de sucesso.
- `400 Bad Request` para body inválido, `farmId` ausente, nome em branco ou nome duplicado na mesma fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou fazenda inativa.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- A atividade produtiva pertence obrigatoriamente a uma fazenda.
- O status inicial é `ACTIVE`.
- O nome é único por fazenda por comparação normalizada.
- O mesmo nome pode existir em fazendas diferentes.
- Fazenda inativa não pode receber nova atividade produtiva.

### GET /api/harvest/production-activities

**Descrição:**
Lista atividades produtivas de uma fazenda, com filtro opcional por status.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com vínculo ativo na fazenda ativa.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1,
  "status": "ACTIVE",
  "page": 0,
  "size": 10,
  "sort": "id",
  "direction": "ASC"
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- `status`
- `page`
- `size`
- `sort`
- `direction`

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 1,
      "farmId": 1,
      "farmName": "Fazenda Boa Safra",
      "name": "Soja",
      "description": "Cultivo de soja",
      "status": "ACTIVE",
      "createdAt": "2026-06-21T10:00:00",
      "updatedAt": "2026-06-21T10:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` para `farmId` ausente ou parâmetro `status` inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- `farmId` é obrigatório.
- A listagem sempre é restrita à fazenda informada.
- Sem `status`, retorna atividades `ACTIVE` e `INACTIVE` paginadas da fazenda.
- Com `status`, retorna apenas atividades da fazenda no status informado.

### GET /api/harvest/production-activities/active

**Descrição:**
Lista atividades produtivas ativas de uma fazenda para seleção em safras.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com vínculo ativo na fazenda ativa.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
[
  {
    "id": 1,
    "farmId": 1,
    "farmName": "Fazenda Boa Safra",
    "name": "Soja",
    "description": "Cultivo de soja",
    "status": "ACTIVE",
    "createdAt": "2026-06-21T10:00:00",
    "updatedAt": "2026-06-21T10:00:00"
  }
]
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` para `farmId` ausente.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem vínculo ativo na fazenda.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- Retorna apenas atividades `ACTIVE` da fazenda informada.
- A resposta não é paginada.
- Usuário com vínculo `INACTIVE` não tem acesso.

### GET /api/harvest/production-activities/summary

**Descrição:**
Retorna o resumo de atividades produtivas de uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com vínculo ativo na fazenda ativa.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "farmId": 1,
  "totalCount": 5,
  "activeCount": 4,
  "inactiveCount": 1,
  "inProgressCount": 2
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` para `farmId` ausente.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso à fazenda.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- `totalCount` conta todas as atividades produtivas da fazenda.
- `activeCount` conta atividades com status `ACTIVE`.
- `inactiveCount` conta atividades com status `INACTIVE`.
- `inProgressCount` conta atividades distintas com pelo menos uma safra `IN_PROGRESS` na mesma fazenda.

### GET /api/harvest/production-activities/{id}

**Descrição:**
Busca uma atividade produtiva por id.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com vínculo ativo na fazenda da atividade.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "name": "Soja",
  "description": "Cultivo de soja",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso à fazenda da atividade.
- `404 Not Found` se a atividade produtiva não existir.

**Observações de regra de negócio:**
- A autorização é calculada pela fazenda da atividade produtiva.

### PUT /api/harvest/production-activities/{id}

**Descrição:**
Atualiza nome e descrição de uma atividade produtiva.

**Autenticação:** Sim
**Permissão:** `ADMIN` para fazenda ativa; `PRODUCER` da fazenda ativa com vínculo ativo.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "name": "Soja verão",
  "description": "Cultivo de soja no verão"
}
```

**Campos obrigatórios:**
- `id`
- `name`

**Campos opcionais:**
- `description`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "name": "Soja verão",
  "description": "Cultivo de soja no verão",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T11:00:00"
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` para body inválido, nome em branco ou nome duplicado na mesma fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou fazenda inativa.
- `404 Not Found` se a atividade produtiva não existir.

**Observações de regra de negócio:**
- A atualização não altera o status.
- A atualização não permite trocar a fazenda da atividade produtiva.
- O nome continua único por fazenda.

### PATCH /api/harvest/production-activities/{id}/activate

**Descrição:**
Ativa uma atividade produtiva inativa.

**Autenticação:** Sim
**Permissão:** `ADMIN` para fazenda ativa; `PRODUCER` da fazenda ativa com vínculo ativo.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "name": "Soja",
  "description": "Cultivo de soja",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T11:00:00"
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou fazenda inativa.
- `404 Not Found` se a atividade produtiva não existir.

**Observações de regra de negócio:**
- Define `status=ACTIVE`.
- Fazenda inativa bloqueia alteração de status.

### DELETE /api/harvest/production-activities/{id}

**Descrição:**
Inativa uma atividade produtiva.

**Autenticação:** Sim
**Permissão:** `ADMIN` para fazenda ativa; `PRODUCER` da fazenda ativa com vínculo ativo.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{}
```

**Possíveis erros/status HTTP:**
- `204 No Content` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou fazenda inativa.
- `404 Not Found` se a atividade produtiva não existir.

**Observações de regra de negócio:**
- A exclusão é lógica: define `status=INACTIVE`.
- Não há delete físico.
- Atividade produtiva inativa não pode ser usada em nova safra.

### POST /api/harvest/seasons

**Descrição:**
Cria uma safra/ciclo produtivo vinculado a uma fazenda e a uma atividade produtiva.

**Autenticação:** Sim
**Permissão:** `ADMIN` para fazenda ativa; `PRODUCER` da fazenda ativa com vínculo ativo.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "farmId": 1,
  "productionActivityId": 1,
  "name": "Safra Soja 2026",
  "description": "Safra de verão",
  "startDate": "2026-01-01",
  "endDate": "2026-06-30",
  "expectedRevenue": 150000.00,
  "expectedCost": 90000.00,
  "areaHectares": 120.50
}
```

**Campos obrigatórios:**
- `farmId`
- `productionActivityId`
- `name`
- `startDate`

**Campos opcionais:**
- `description`
- `endDate`
- `expectedRevenue`
- `expectedCost`
- `areaHectares`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "productionActivityId": 1,
  "productionActivityName": "Soja",
  "name": "Safra Soja 2026",
  "description": "Safra de verão",
  "startDate": "2026-01-01",
  "endDate": "2026-06-30",
  "expectedRevenue": 150000.00,
  "expectedCost": 90000.00,
  "areaHectares": 120.50,
  "status": "PLANNED",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `201 Created` em caso de sucesso.
- `400 Bad Request` para body inválido, datas inválidas, valores negativos, nome duplicado na fazenda ou atividade produtiva inativa.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou fazenda inativa.
- `404 Not Found` se fazenda ou atividade produtiva não existir.

**Observações de regra de negócio:**
- A safra sempre pertence a uma fazenda.
- A safra sempre possui uma atividade produtiva.
- O status inicial é `PLANNED`.
- Fazenda inativa não pode receber nova safra, inclusive para `ADMIN`.
- Atividade produtiva inativa não pode ser usada em nova safra.
- A atividade produtiva deve pertencer à mesma fazenda da safra.
- `endDate` não pode ser anterior a `startDate`.
- `expectedRevenue`, `expectedCost` e `areaHectares` não podem ser negativos.
- `areaHectares=0` é aceito.
- Nome de safra é único por fazenda entre safras não inativas.

### GET /api/harvest/seasons

**Descrição:**
Lista safras de uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com vínculo ativo na fazenda ativa.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1,
  "statuses": ["PLANNED", "IN_PROGRESS"],
  "productionActivityId": 1,
  "productionActivityIds": [1, 2],
  "periodStart": "2026-01-01",
  "periodEnd": "2026-06-30",
  "includeInactive": false,
  "page": 0,
  "size": 10,
  "sort": "id",
  "direction": "ASC"
}
```

Exemplos:
```http
GET /api/harvest/seasons?farmId=1&statuses=PLANNED
GET /api/harvest/seasons?farmId=1&statuses=PLANNED,IN_PROGRESS
GET /api/harvest/seasons?farmId=1&productionActivityIds=1,2&periodStart=2026-01-01&periodEnd=2026-06-30
GET /api/harvest/seasons?farmId=1&statuses=PLANNED,IN_PROGRESS&productionActivityIds=1,2&periodStart=2026-01-01&periodEnd=2026-06-30
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- `statuses`
- `productionActivityId`
- `productionActivityIds`
- `periodStart`
- `periodEnd`
- `includeInactive`
- `page`
- `size`
- `sort`
- `direction`

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 1,
      "farmId": 1,
      "farmName": "Fazenda Boa Safra",
      "productionActivityId": 1,
      "productionActivityName": "Soja",
      "name": "Safra Soja 2026",
      "description": "Safra de verão",
      "startDate": "2026-01-01",
      "endDate": "2026-06-30",
      "expectedRevenue": 150000.00,
      "expectedCost": 90000.00,
      "areaHectares": 120.50,
      "status": "PLANNED",
      "createdAt": "2026-06-21T10:00:00",
      "updatedAt": "2026-06-21T10:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` para `farmId` ausente, parâmetro inválido ou `periodStart` posterior a `periodEnd`.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- `farmId` é obrigatório.
- `statuses` filtra por múltiplos status, por exemplo `statuses=PLANNED,IN_PROGRESS`.
- `productionActivityId` filtra por uma única atividade produtiva da fazenda informada.
- `productionActivityIds` filtra por múltiplas atividades produtivas da fazenda informada, por exemplo `productionActivityIds=1,2`.
- Quando `productionActivityId` e `productionActivityIds` são enviados juntos, `productionActivityIds` tem prioridade.
- `periodStart` e `periodEnd` usam formato `yyyy-MM-dd`.
- O filtro de período retorna safras que intersectam o período informado: `startDate <= periodEnd` e `endDate >= periodStart`; safras sem `endDate` intersectam qualquer período iniciado após o `startDate`.
- Se apenas `periodStart` for informado, retorna safras sem data final ou com `endDate` maior ou igual a `periodStart`.
- Se apenas `periodEnd` for informado, retorna safras com `startDate` menor ou igual a `periodEnd`.
- `periodStart` posterior a `periodEnd` retorna `400 Bad Request` com a mensagem `A data inicial do período não pode ser posterior à data final.`.
- Sem `statuses`, `includeInactive=false` é o default e oculta safras com status `INACTIVE`.
- Sem `statuses`, `includeInactive=true` inclui safras inativas.
- Com `statuses`, o filtro de status informado define quais status são retornados.
- Usuário com vínculo `INACTIVE` não acessa a listagem.

### GET /api/harvest/seasons/summary-list

**Descrição:**
Lista safras de uma fazenda com dados cadastrais e resumo financeiro agregado por safra.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com vínculo ativo na fazenda ativa.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1,
  "statuses": ["PLANNED", "IN_PROGRESS"],
  "productionActivityId": 1,
  "productionActivityIds": [1, 2],
  "periodStart": "2026-01-01",
  "periodEnd": "2026-06-30",
  "search": "soja",
  "page": 0,
  "size": 10,
  "sort": "id",
  "direction": "ASC"
}
```

Exemplos:
```http
GET /api/harvest/seasons/summary-list?farmId=1&statuses=PLANNED
GET /api/harvest/seasons/summary-list?farmId=1&statuses=PLANNED,IN_PROGRESS
GET /api/harvest/seasons/summary-list?farmId=1&productionActivityIds=1,2&periodStart=2026-01-01&periodEnd=2026-06-30
GET /api/harvest/seasons/summary-list?farmId=1&search=soja&statuses=PLANNED,IN_PROGRESS&productionActivityIds=1,2&periodStart=2026-01-01&periodEnd=2026-06-30
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- `statuses`
- `productionActivityId`
- `productionActivityIds`
- `periodStart`
- `periodEnd`
- `search`
- `page`
- `size`
- `sort`
- `direction`

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 1,
      "farmId": 1,
      "farmName": "Fazenda Boa Safra",
      "productionActivityId": 1,
      "productionActivityName": "Soja",
      "name": "Safra Soja 2026",
      "description": "Safra de verão",
      "startDate": "2026-01-01",
      "endDate": "2026-06-30",
      "expectedCost": 90000.00,
      "expectedRevenue": 150000.00,
      "expectedProfit": 60000.00,
      "areaHectares": 120.50,
      "status": "PLANNED",
      "realizedCost": 72500.00,
      "realizedRevenue": 150000.00,
      "realizedProfit": 77500.00,
      "pendingExpenses": 18000.00,
      "overdueExpenses": 6000.00,
      "pendingRevenue": 25000.00,
      "transactionCount": 6,
      "incomeCount": 3,
      "expenseCount": 3,
      "createdAt": "2026-06-21T10:00:00",
      "updatedAt": "2026-06-21T10:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` para `farmId` ausente, parâmetro inválido ou `periodStart` posterior a `periodEnd`.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- `farmId` é obrigatório.
- `statuses` filtra por múltiplos status, por exemplo `statuses=PLANNED,IN_PROGRESS`.
- `productionActivityId` filtra por uma única atividade produtiva da fazenda informada.
- `productionActivityIds` filtra por múltiplas atividades produtivas da fazenda informada, por exemplo `productionActivityIds=1,2`.
- Quando `productionActivityId` e `productionActivityIds` são enviados juntos, `productionActivityIds` tem prioridade.
- `periodStart` e `periodEnd` usam formato `yyyy-MM-dd`.
- O filtro de período retorna safras que intersectam o período informado: `startDate <= periodEnd` e `endDate >= periodStart`; safras sem `endDate` intersectam qualquer período iniciado após o `startDate`.
- Se apenas `periodStart` for informado, retorna safras sem data final ou com `endDate` maior ou igual a `periodStart`.
- Se apenas `periodEnd` for informado, retorna safras com `startDate` menor ou igual a `periodEnd`.
- `periodStart` posterior a `periodEnd` retorna `400 Bad Request` com a mensagem `A data inicial do período não pode ser posterior à data final.`.
- Sem `statuses`, safras com status `INACTIVE` não são retornadas.
- Com `statuses=INACTIVE`, somente safras inativas são retornadas.
- `search` é normalizado com `trim` e busca, sem diferenciar maiúsculas e minúsculas, por nome da safra, descrição da safra e nome da atividade produtiva.
- O retorno é paginado com o mesmo formato de `PageResponse`.
- `expectedProfit` é calculado como `expectedRevenue - expectedCost`.
- `realizedRevenue` soma receitas pagas vinculadas à safra.
- `realizedCost` soma despesas pagas vinculadas à safra.
- `realizedProfit` é calculado como `realizedRevenue - realizedCost`.
- `pendingExpenses` soma despesas pendentes.
- `overdueExpenses` soma despesas atrasadas.
- `pendingRevenue` soma receitas pendentes.
- `transactionCount`, `incomeCount` e `expenseCount` consideram somente movimentações válidas vinculadas à safra.
- Movimentações com `recordStatus=DELETED`, `status=CANCELED`, sem safra ou vinculadas a outra safra não entram nos totais nem nos contadores.
- Safras sem movimentações aparecem na lista com totais e contadores zerados.

### GET /api/harvest/seasons/dashboard

**Descrição:** Retorna, para os cards de Dashboard, no máximo três safras em andamento da fazenda.

**Autenticação e permissão:** ADMIN; PRODUCER, EMPLOYEE ou ACCOUNTANT com vínculo ativo na fazenda ativa.

**Query params:** `farmId` (obrigatório). Não aceita paginação ou filtros.

**Regras:** retorna exclusivamente safras `IN_PROGRESS`, ordenadas por `startDate DESC`, `createdAt DESC` e `id DESC`. `realized` considera somente movimentações ACTIVE e PAID vinculadas à mesma safra e fazenda. `projection` soma o realizado às movimentações PENDING e OVERDUE. `dueNext7Days` considera somente EXPENSE PENDING com vencimento entre hoje e hoje + 7 dias, inclusive. `overdue` considera somente EXPENSE OVERDUE ou PENDING com vencimento anterior a hoje. Movimentações canceladas, deletadas, sem vencimento nos alertas, de outra safra ou outra fazenda não entram.

**Resposta de sucesso:**
```json
[
  {
    "id": 25,
    "farmId": 8,
    "name": "Café 2026/2027",
    "status": "IN_PROGRESS",
    "productionActivityId": 25,
    "productionActivityName": "Café",
    "realized": { "cost": 40000.00, "revenue": 50000.00, "profit": 10000.00 },
    "projection": { "cost": 68000.00, "revenue": 132000.00, "profit": 64000.00 },
    "dueNext7Days": { "count": 2, "totalAmount": 12500.00 },
    "overdue": { "count": 1, "totalAmount": 4800.00 }
  }
]
```

**Possíveis erros/status HTTP:** `400` para `farmId` ausente; `401` sem autenticação válida; `403` sem acesso à fazenda; `404` para fazenda inexistente.

### GET /api/harvest/seasons/summary

**Descricao:** Retorna o resumo financeiro agregado das safras filtradas da fazenda.

**Autenticacao e permissao:** ADMIN; PRODUCER, EMPLOYEE ou ACCOUNTANT com vinculo ativo na fazenda ativa.

**Query params:** farmId (obrigatorio), statuses, productionActivityId, productionActivityIds, periodStart, periodEnd e search. Os filtros usam a mesma semantica de GET /api/harvest/seasons/summary-list, incluindo periodo por intersecao e busca por nome, descricao ou atividade.

**Contrato:** farmId, activeHarvestCount, planning, realized, projection e comparison (profitPerformanceAmount, profitPerformancePercentage, profitPerformanceStatus, costVarianceAmount, costVariancePercentage, costVarianceStatus).

**Calculos:** planejamento soma expectedCost e expectedRevenue; realizado soma somente PAID; projecao soma realizado com PENDING e OVERDUE. Desempenho do lucro e (projectedProfit - plannedProfit) / abs(plannedProfit) * 100. Desvio de custo e projectedCost - plannedCost, com percentual sobre plannedCost. Percentuais usam escala 2 e HALF_UP. Quando lucro ou custo planejado e zero, o percentual e null e o status correspondente e NOT_APPLICABLE.

### GET /api/harvest/seasons/{id}

**Descrição:**
Busca uma safra por id.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com vínculo ativo na fazenda ativa da safra.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "productionActivityId": 1,
  "productionActivityName": "Soja",
  "name": "Safra Soja 2026",
  "description": "Safra de verão",
  "startDate": "2026-01-01",
  "endDate": "2026-06-30",
  "expectedRevenue": 150000.00,
  "expectedCost": 90000.00,
  "areaHectares": 120.50,
  "status": "PLANNED",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão.
- `404 Not Found` se a safra não existir.

**Observações de regra de negócio:**
- A autorização é calculada a partir da fazenda da safra.

### GET /api/harvest/seasons/{id}/summary

**Descrição:**
Retorna o resumo financeiro consolidado de uma safra com base nas movimentações financeiras vinculadas.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com vínculo ativo na fazenda ativa da safra.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "harvestSeasonId": 1,
  "harvestSeasonName": "Safra Soja 2025/26",
  "productionActivityId": 1,
  "productionActivityName": "Soja",
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "expectedCost": 96500.00,
  "expectedRevenue": 210000.00,
  "expectedProfit": 113500.00,
  "realizedCost": 72500.00,
  "realizedRevenue": 150000.00,
  "realizedProfit": 77500.00,
  "pendingExpenses": 18000.00,
  "overdueExpenses": 6000.00,
  "pendingRevenue": 25000.00,
  "transactionCount": 42,
  "incomeCount": 8,
  "expenseCount": 34,
  "areaHectares": 120.00,
  "costPerHectare": 604.17,
  "revenuePerHectare": 1250.00,
  "profitPerHectare": 645.83
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão.
- `404 Not Found` se a safra não existir.

**Regras de cálculo:**
- Considera apenas movimentações com `harvestSeasonId` igual ao `id` da safra e `recordStatus=ACTIVE`.
- Movimentações com `status=CANCELED` não entram em valores nem contadores.
- Movimentações com `recordStatus=DELETED` são ignoradas.
- `realizedRevenue` soma `INCOME` com `PAID`.
- `realizedCost` soma `EXPENSE` com `PAID`.
- `realizedProfit = realizedRevenue - realizedCost`.
- `pendingExpenses` soma `EXPENSE` com `PENDING`.
- `overdueExpenses` soma `EXPENSE` com `OVERDUE`.
- `pendingRevenue` soma `INCOME` com `PENDING`.
- `transactionCount`, `incomeCount` e `expenseCount` contam apenas movimentações `PAID`, `PENDING` ou `OVERDUE` vinculadas à safra.
- `expectedCost`, `expectedRevenue` e `areaHectares` vêm da própria safra.
- `expectedProfit = expectedRevenue - expectedCost`, tratando valores nulos como zero.
- Indicadores por hectare usam valores realizados: `realizedCost / areaHectares`, `realizedRevenue / areaHectares` e `realizedProfit / areaHectares`.
- `costPerHectare`, `revenuePerHectare` e `profitPerHectare` retornam `null` quando `areaHectares` é nulo ou zero.

### PUT /api/harvest/seasons/{id}

**Descrição:**
Atualiza os dados de uma safra.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER` com vínculo ativo na fazenda ativa da safra.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "productionActivityId": 1,
  "name": "Safra Soja 2026 Atualizada",
  "description": "Safra atualizada",
  "startDate": "2026-01-01",
  "endDate": "2026-07-15",
  "expectedRevenue": 160000.00,
  "expectedCost": 95000.00,
  "areaHectares": 120.50
}
```

**Campos obrigatórios:**
- `id`
- `productionActivityId`
- `name`
- `startDate`

**Campos opcionais:**
- `description`
- `endDate`
- `expectedRevenue`
- `expectedCost`
- `areaHectares`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "productionActivityId": 1,
  "productionActivityName": "Soja",
  "name": "Safra Soja 2026 Atualizada",
  "description": "Safra atualizada",
  "startDate": "2026-01-01",
  "endDate": "2026-07-15",
  "expectedRevenue": 160000.00,
  "expectedCost": 95000.00,
  "areaHectares": 120.50,
  "status": "PLANNED",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T11:00:00"
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` para body inválido, datas inválidas, valores negativos, nome duplicado, safra inativa ou atividade produtiva inativa.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou quando a safra não for encontrada na checagem de autorização.
- `404 Not Found` se a atividade produtiva não existir após a autorização.

**Observações de regra de negócio:**
- Não permite alterar `farmId`.
- Safra com status `INACTIVE` não pode ser editada por esta operação.
- `EMPLOYEE` e `ACCOUNTANT` não podem editar safra.
- A atividade produtiva informada deve estar `ACTIVE`.

### PATCH /api/harvest/seasons/{id}/status

**Descrição:**
Altera o status de uma safra.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER` com vínculo ativo na fazenda ativa da safra.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "status": "IN_PROGRESS"
}
```

**Campos obrigatórios:**
- `id`
- `status`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "productionActivityId": 1,
  "productionActivityName": "Soja",
  "name": "Safra Soja 2026",
  "description": "Safra de verão",
  "startDate": "2026-01-01",
  "endDate": "2026-06-30",
  "expectedRevenue": 150000.00,
  "expectedCost": 90000.00,
  "areaHectares": 120.50,
  "status": "IN_PROGRESS",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T11:00:00"
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` para body inválido ou status inválido.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou quando a safra não for encontrada na checagem de autorização.

**Observações de regra de negócio:**
- Status aceitos: `PLANNED`, `IN_PROGRESS`, `FINISHED`, `INACTIVE`.
- Para status diferente de `INACTIVE`, a fazenda da safra deve estar ativa.

### PATCH /api/harvest/seasons/{id}/activate

**Descrição:**
Reativa uma safra inativa.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER` com vínculo ativo na fazenda ativa da safra.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "productionActivityId": 1,
  "productionActivityName": "Soja",
  "name": "Safra Soja 2026",
  "description": "Safra de verão",
  "startDate": "2026-01-01",
  "endDate": "2026-06-30",
  "expectedRevenue": 150000.00,
  "expectedCost": 90000.00,
  "areaHectares": 120.50,
  "status": "PLANNED",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T11:00:00"
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` se a fazenda da safra estiver inativa.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou quando a safra não for encontrada na checagem de autorização.

**Observações de regra de negócio:**
- Define `status=PLANNED`.
- A fazenda da safra deve estar ativa.

### DELETE /api/harvest/seasons/{id}

**Descrição:**
Inativa uma safra.

**Autenticação:** Sim
**Permissão:** `ADMIN`; `PRODUCER` com vínculo ativo na fazenda ativa da safra.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{}
```

**Possíveis erros/status HTTP:**
- `204 No Content` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou quando a safra não for encontrada na checagem de autorização.

**Observações de regra de negócio:**
- A exclusão é lógica: define `status=INACTIVE`.
- Não há delete físico.
- `EMPLOYEE`, `ACCOUNTANT`, vínculo `INACTIVE` e usuário sem vínculo não podem inativar safras.

### POST /api/financial/categories

**Descrição:**
Cria uma categoria financeira vinculada obrigatoriamente a uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN` para fazenda ativa ou `PRODUCER` com vínculo ativo na fazenda ativa.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "farmId": 1,
  "name": "Insumos",
  "type": "EXPENSE",
  "color": "#FF0000",
  "icon": "package"
}
```

**Campos obrigatórios:**
- `farmId`
- `name`
- `type`

**Campos opcionais:**
- `color`
- `icon`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "name": "Insumos",
  "type": "EXPENSE",
  "color": "#FF0000",
  "icon": "package",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `201 Created` em caso de sucesso.
- `400 Bad Request` para body inválido, `farmId` ausente, nome em branco ou duplicidade de `name + type` na mesma fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou fazenda inativa.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- Não existem categorias globais/default na API.
- O nome é salvo sem espaços no início/fim.
- A unicidade é por fazenda, nome normalizado (`lower(trim(name))`) e tipo.
- Categorias `ACTIVE` e `INACTIVE` bloqueiam novo cadastro duplicado na mesma fazenda.
- Fazendas diferentes podem ter categorias com o mesmo nome e tipo.
- A categoria é criada com status `ACTIVE`.

### GET /api/financial/categories

**Descrição:**
Lista categorias de uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1,
  "includeInactive": true,
  "page": 0,
  "size": 10,
  "sort": "id",
  "direction": "ASC"
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- `includeInactive`
- `page`
- `size`
- `sort`
- `direction`

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 1,
      "farmId": 1,
      "farmName": "Fazenda Boa Safra",
      "name": "Insumos",
      "type": "EXPENSE",
      "color": "#FF0000",
      "icon": "package",
      "status": "ACTIVE",
      "createdAt": "2026-06-21T10:00:00",
      "updatedAt": "2026-06-21T10:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para query params inválidos ou `farmId` ausente.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- Retorna apenas categorias da fazenda informada.
- Por padrão, retorna apenas categorias `ACTIVE`.
- Quando `includeInactive=true`, retorna categorias `ACTIVE` e `INACTIVE`.
- `EMPLOYEE` e `ACCOUNTANT` podem consultar, mas não gerenciar.

### GET /api/financial/categories/used-in-transactions

**Descrição:**
Lista as categorias da fazenda utilizadas em movimentações `ACTIVE`. Este endpoint deve ser usado para filtros de categoria na tela de movimentações.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- Nenhum.

**Exemplo:**
```http
GET /api/financial/categories/used-in-transactions?farmId=1
```

**Resposta de sucesso:**
```json
[
  {
    "id": 8,
    "farmId": 1,
    "farmName": "Fazenda Boa Safra",
    "name": "Combustível",
    "type": "EXPENSE",
    "color": "#FF0000",
    "icon": "fuel",
    "status": "INACTIVE",
    "createdAt": "2026-06-21T10:00:00",
    "updatedAt": "2026-06-21T10:00:00"
  }
]
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para query params inválidos ou `farmId` ausente.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- Retorna somente categorias da fazenda que possuem pelo menos uma movimentação `ACTIVE` na mesma fazenda.
- Inclui categorias `ACTIVE` e `INACTIVE`, preservando categorias inativas usadas historicamente por movimentações ativas.
- Não retorna categorias de outra fazenda nem categorias sem movimentações.
- Não considera movimentações deletadas logicamente (`recordStatus=DELETED`).
- Ordena por nome.

### GET /api/financial/categories/{id}

**Descrição:**
Busca categoria financeira por id.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda da categoria.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "name": "Insumos",
  "type": "EXPENSE",
  "color": "#FF0000",
  "icon": "package",
  "status": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda da categoria.
- `404 Not Found` se a categoria não existir.

### PUT /api/financial/categories/{id}

**Descrição:**
Atualiza dados editáveis da categoria. Não permite trocar a fazenda da categoria.

**Autenticação:** Sim
**Permissão:** `ADMIN` para fazenda ativa ou `PRODUCER` com vínculo ativo na fazenda ativa da categoria.

**Body esperado:**
```json
{
  "name": "Combustível",
  "type": "EXPENSE",
  "color": "#FF9900",
  "icon": "fuel"
}
```

**Campos obrigatórios:**
- `name`
- `type`

**Campos opcionais:**
- `color`
- `icon`

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `400 Bad Request` para body inválido, nome em branco ou duplicidade de `name + type` na mesma fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou fazenda inativa.
- `404 Not Found` se a categoria não existir.

### PATCH /api/financial/categories/{id}/activate

**Descrição:**
Ativa uma categoria financeira inativa.

**Autenticação:** Sim
**Permissão:** `ADMIN` para fazenda ativa ou `PRODUCER` com vínculo ativo na fazenda ativa da categoria.

**Possíveis erros/status HTTP:**
- `200 OK` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou fazenda inativa.
- `404 Not Found` se a categoria não existir.

### DELETE /api/financial/categories/{id}

**Descrição:**
Inativa uma categoria financeira por exclusão lógica.

**Autenticação:** Sim
**Permissão:** `ADMIN` para fazenda ativa ou `PRODUCER` com vínculo ativo na fazenda ativa da categoria.

**Possíveis erros/status HTTP:**
- `204 No Content` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão ou fazenda inativa.
- `404 Not Found` se a categoria não existir.

**Observações de regra de negócio:**
- A exclusão é lógica: define `status=INACTIVE`.
- `EMPLOYEE`, `ACCOUNTANT`, vínculo `INACTIVE` e usuário sem vínculo não gerenciam categorias.
- A rota `/api/financial/categories/global` foi removida.

### GET /api/farms/{farmId}/users/options

**Descrição:**
Lista usuários para o filtro **Criado por** da tela de movimentações financeiras. A resposta é enxuta e contém apenas usuários elegíveis para o filtro.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda.

**Path params:**
```json
{
  "farmId": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- Nenhum.

**Exemplo:**
```http
GET /api/farms/1/users/options
```

**Resposta de sucesso:**
```json
[
  {
    "id": 2,
    "name": "Maria Silva"
  },
  {
    "id": 5,
    "name": "Pedro Souza"
  }
]
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para path params inválidos.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda.
- `404 Not Found` se a fazenda não existir.

**Observações de regra de negócio:**
- Retorna usuários `ACTIVE` com vínculo ativo na fazenda (`PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT`), mesmo sem movimentações.
- Retorna usuários que criaram movimentação `ACTIVE` na fazenda, mesmo que hoje estejam `INACTIVE`, `BLOCKED` ou com vínculo `INACTIVE`.
- Não retorna usuários `INACTIVE` ou `BLOCKED` apenas por vínculo ativo.
- Não retorna vínculo `INACTIVE` sem movimentação ativa.
- Não considera movimentações deletadas logicamente (`recordStatus=DELETED`).
- Remove duplicidades e ordena por nome.
- Não retorna `email`, `document`, `userType`, `status` ou dados do vínculo com a fazenda.

### POST /api/financial/transactions

**Descrição:**
Cria uma movimentação financeira.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER` ou `EMPLOYEE` com acesso à fazenda.

**Path params:**
```json
{}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "description": "Compra de sementes",
  "amount": 2500.00,
  "type": "EXPENSE",
  "status": "PENDING",
  "paymentMethod": "PIX",
  "transactionDate": "2026-06-21",
  "dueDate": "2026-06-30",
  "paidAt": null,
  "notes": "Compra para safra",
  "farmId": 1,
  "categoryId": 1,
  "harvestSeasonId": 10
}
```

**Campos obrigatórios:**
- `description`
- `amount`
- `type`
- `transactionDate`
- `farmId`

**Campos opcionais:**
- `status`
- `paymentMethod`
- `dueDate`
- `paidAt`
- `notes`
- `categoryId`
- `harvestSeasonId`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "description": "Compra de sementes",
  "amount": 2500.00,
  "type": "EXPENSE",
  "status": "PENDING",
  "paymentMethod": "PIX",
  "transactionDate": "2026-06-21",
  "dueDate": "2026-06-30",
  "paidAt": null,
  "notes": "Compra para safra",
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "categoryId": 1,
  "categoryName": "Insumos",
  "harvestSeasonId": 10,
  "harvestSeasonName": "Safra Soja 2025/26",
  "createdByUserId": 2,
  "createdByUserName": "User",
  "updatedByUserId": null,
  "updatedByUserName": null,
  "recordStatus": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `201 Created` em caso de sucesso.
- `400 Bad Request` para body inválido, valor não positivo, fazenda inativa, categoria inativa, categoria de outra fazenda, tipo de categoria incompatível, safra inativa ou safra de outra fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão de gestão financeira.
- `404 Not Found` se fazenda, categoria, safra ou usuário autenticado não existir.

**Observações de regra de negócio:**
- Se `status` não for informado, o backend usa `PENDING`.
- `harvestSeasonId` é opcional; quando informado, a safra deve pertencer à mesma fazenda da movimentação.
- Não é permitido criar movimentação vinculada a uma safra inativa.
- `createdByUserId` é definido pelo backend a partir do usuário autenticado.
- `ACCOUNTANT` pode consultar dados financeiros, mas não criar movimentações. Quando `categoryId` é informado em criação ou edição, a categoria deve estar `ACTIVE`, ter o mesmo tipo da movimentação e pertencer à mesma fazenda.

### GET /api/financial/transactions

**Descrição:**
Lista movimentações financeiras ativas de uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1,
  "transactionDateStart": "2026-06-01",
  "transactionDateEnd": "2026-06-30",
  "paidAtStart": "2026-06-01",
  "paidAtEnd": "2026-06-30",
  "type": "EXPENSE",
  "categoryId": 1,
  "categoryIds": [1, 2],
  "harvestSeasonId": 10,
  "paymentStatus": "PENDING",
  "paymentStatuses": ["PENDING", "PAID"],
  "paymentMethod": "PIX",
  "paymentMethods": ["PIX", "CASH"],
  "recordStatus": "ACTIVE",
  "description": "soja",
  "createdByUserId": 2,
  "minAmount": 100.00,
  "maxAmount": 5000.00,
  "page": 0,
  "size": 10,
  "sort": "transactionDate",
  "direction": "DESC"
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- `page`
- `size`
- `sort`
- `direction`
- `transactionDateStart`
- `transactionDateEnd`
- `paidAtStart`
- `paidAtEnd`
- `type`
- `categoryId`
- `categoryIds`
- `harvestSeasonId`
- `paymentStatus`
- `paymentStatuses`
- `paymentMethod`
- `paymentMethods`
- `recordStatus`
- `description`
- `createdByUserId`
- `minAmount`
- `maxAmount`

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 1,
      "description": "Compra de sementes",
      "amount": 2500.00,
      "type": "EXPENSE",
      "status": "PENDING",
      "paymentMethod": "PIX",
      "transactionDate": "2026-06-21",
      "dueDate": "2026-06-30",
      "paidAt": null,
      "notes": "Compra para safra",
      "farmId": 1,
      "farmName": "Fazenda Boa Safra",
      "categoryId": 1,
      "categoryName": "Insumos",
      "harvestSeasonId": 10,
      "harvestSeasonName": "Safra Soja 2025/26",
      "createdByUserId": 2,
      "createdByUserName": "User",
      "updatedByUserId": null,
      "updatedByUserName": null,
      "recordStatus": "ACTIVE",
      "createdAt": "2026-06-21T10:00:00",
      "updatedAt": "2026-06-21T10:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para query params inválidos ou categorias de filtro que não pertencem à fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda.
- `404 Not Found` se a safra informada no filtro não existir.

**Observações de regra de negócio:**
- Por padrão, `recordStatus` é `ACTIVE` quando não informado.
- Se `recordStatus` for informado, a listagem filtra pelo valor enviado.
- Datas usam formato `YYYY-MM-DD`.
- Filtros de intervalo são inclusivos.
- `description` usa busca parcial e case-insensitive.
- `paymentStatus` filtra o campo `status` da movimentação.
- `paymentMethod` filtra a forma de pagamento da movimentação.
- `paymentStatuses`: aceita query params repetidos, por exemplo `paymentStatuses=PENDING&paymentStatuses=PAID`. Quando informado com valores válidos, tem prioridade sobre `paymentStatus`.
- `categoryId` filtra a categoria vinculada à movimentação; a categoria informada deve existir e pertencer à fazenda consultada.
- `harvestSeasonId` filtra movimentações vinculadas à safra informada; a safra deve existir e pertencer à fazenda consultada.
- Exemplo: `GET /api/financial/transactions?farmId=1&harvestSeasonId=10`.
- `categoryIds`: aceita query params repetidos, por exemplo `categoryIds=1&categoryIds=2`. Quando informado com valores válidos, tem prioridade sobre `categoryId`; todas as categorias devem pertencer à fazenda consultada.
- `paymentMethods`: aceita query params repetidos, por exemplo `paymentMethods=PIX&paymentMethods=CASH`. Quando informado com valores válidos, tem prioridade sobre `paymentMethod`.
- `ACCOUNTANT` pode consultar movimentações.

### GET /api/financial/transactions/{id}

**Descrição:**
Busca uma movimentação financeira por id.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda da movimentação.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "description": "Compra de sementes",
  "amount": 2500.00,
  "type": "EXPENSE",
  "status": "PENDING",
  "paymentMethod": "PIX",
  "transactionDate": "2026-06-21",
  "dueDate": "2026-06-30",
  "paidAt": null,
  "notes": "Compra para safra",
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "categoryId": 1,
  "categoryName": "Insumos",
  "harvestSeasonId": 10,
  "harvestSeasonName": "Safra Soja 2025/26",
  "createdByUserId": 2,
  "createdByUserName": "User",
  "updatedByUserId": null,
  "updatedByUserName": null,
  "recordStatus": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:00:00"
}
```

**Possíveis erros/status HTTP:**
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda da movimentação.
- `404 Not Found` se a movimentação não existir.

**Observações de regra de negócio:**
- A autorização usa a fazenda associada à movimentação.
- `harvestSeasonId` e `harvestSeasonName` são retornados como `null` quando a movimentação não tem safra vinculada.

### PUT /api/financial/transactions/{id}

**Descrição:**
Atualiza uma movimentação financeira.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER` ou `EMPLOYEE` com acesso à fazenda da movimentação.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "description": "Compra de sementes atualizada",
  "amount": 2600.00,
  "type": "EXPENSE",
  "status": "PENDING",
  "paymentMethod": "PIX",
  "transactionDate": "2026-06-21",
  "dueDate": "2026-06-30",
  "paidAt": null,
  "notes": "Valor corrigido",
  "categoryId": 1,
  "harvestSeasonId": 10
}
```

**Campos obrigatórios:**
- `id`
- `description`
- `amount`
- `type`
- `status`
- `transactionDate`

**Campos opcionais:**
- `paymentMethod`
- `dueDate`
- `paidAt`
- `notes`
- `categoryId`
- `harvestSeasonId`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "description": "Compra de sementes atualizada",
  "amount": 2600.00,
  "type": "EXPENSE",
  "status": "PENDING",
  "paymentMethod": "PIX",
  "transactionDate": "2026-06-21",
  "dueDate": "2026-06-30",
  "paidAt": null,
  "notes": "Valor corrigido",
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "categoryId": 1,
  "categoryName": "Insumos",
  "harvestSeasonId": 10,
  "harvestSeasonName": "Safra Soja 2025/26",
  "createdByUserId": 2,
  "createdByUserName": "User",
  "updatedByUserId": 2,
  "updatedByUserName": "User",
  "recordStatus": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para body inválido, valor não positivo, categoria inativa, categoria de outra fazenda, tipo de categoria incompatível, safra inativa ou safra de outra fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão de gestão financeira.
- `404 Not Found` se movimentação, categoria ou safra não existir.

**Observações de regra de negócio:**
- `farmId` não é aceito no request de atualização; a fazenda vem da movimentação existente.
- `updatedByUserId` é definido pelo backend a partir do usuário autenticado.

### DELETE /api/financial/transactions/{id}

**Descrição:**
Exclui logicamente uma movimentação financeira.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER` ou `EMPLOYEE` com acesso à fazenda da movimentação.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{}
```

**Possíveis erros/status HTTP:**
- `204 No Content` em caso de sucesso.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão de gestão financeira.
- `404 Not Found` se a movimentação não existir.

**Observações de regra de negócio:**
- A exclusão é lógica: `recordStatus` passa para `DELETED`.
- `updatedByUserId` é definido pelo backend.

### PATCH /api/financial/transactions/{id}/pay

**Descrição:**
Marca uma movimentação como paga.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER` ou `EMPLOYEE` com acesso à fazenda da movimentação.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{
  "paidAt": "2026-06-21",
  "paymentMethod": "PIX"
}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- `paidAt`
- `paymentMethod`

**Resposta de sucesso:**
```json
{
  "id": 1,
  "description": "Compra de sementes",
  "amount": 2500.00,
  "type": "EXPENSE",
  "status": "PAID",
  "paymentMethod": "PIX",
  "transactionDate": "2026-06-21",
  "dueDate": "2026-06-30",
  "paidAt": "2026-06-21",
  "notes": "Compra para safra",
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "categoryId": 1,
  "categoryName": "Insumos",
  "harvestSeasonId": 10,
  "harvestSeasonName": "Safra Soja 2025/26",
  "createdByUserId": 2,
  "createdByUserName": "User",
  "updatedByUserId": 2,
  "updatedByUserName": "User",
  "recordStatus": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão de gestão financeira.
- `404 Not Found` se a movimentação não existir.

**Observações de regra de negócio:**
- Se `paidAt` não for informado, o backend usa a data atual.
- O status passa para `PAID`.

### PATCH /api/financial/transactions/{id}/cancel

**Descrição:**
Cancela uma movimentação financeira.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER` ou `EMPLOYEE` com acesso à fazenda da movimentação.

**Path params:**
```json
{
  "id": 1
}
```

**Query params:**
```json
{}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `id`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "id": 1,
  "description": "Compra de sementes",
  "amount": 2500.00,
  "type": "EXPENSE",
  "status": "CANCELED",
  "paymentMethod": "PIX",
  "transactionDate": "2026-06-21",
  "dueDate": "2026-06-30",
  "paidAt": null,
  "notes": "Compra para safra",
  "farmId": 1,
  "farmName": "Fazenda Boa Safra",
  "categoryId": 1,
  "categoryName": "Insumos",
  "harvestSeasonId": 10,
  "harvestSeasonName": "Safra Soja 2025/26",
  "createdByUserId": 2,
  "createdByUserName": "User",
  "updatedByUserId": 2,
  "updatedByUserName": "User",
  "recordStatus": "ACTIVE",
  "createdAt": "2026-06-21T10:00:00",
  "updatedAt": "2026-06-21T10:30:00"
}
```

**Possíveis erros/status HTTP:**
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem permissão de gestão financeira.
- `404 Not Found` se a movimentação não existir.

**Observações de regra de negócio:**
- O status passa para `CANCELED`.
- Resumos financeiros ignoram movimentações canceladas nos totais por tipo.

### GET /api/financial/summary

**Descrição:**
Retorna os indicadores financeiros do dashboard para uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- Nenhum.

**Resposta de sucesso:**
```json
{
  "farmId": 1,
  "currentBalance": 700.00,
  "expectedIncome": 700.00,
  "expectedExpense": 1150.00,
  "projectedBalance": 250.00,
  "payableNext30Days": 150.00,
  "overdueExpenses": 100.00,
  "receivableNext30Days": 700.00,
  "cashFlowNext30Days": 450.00
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para query params inválidos ou categorias de filtro que não pertencem à fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda.

**Observações de regra de negócio:**
- Considera apenas movimentações da fazenda informada com `recordStatus=ACTIVE`.
- Movimentações `CANCELED` e `DELETED` são ignoradas nos cálculos.
- `currentBalance`: receitas pagas menos despesas pagas.
- `expectedIncome`: receitas pendentes ou atrasadas.
- `expectedExpense`: despesas pendentes ou atrasadas.
- `projectedBalance`: `currentBalance + expectedIncome - expectedExpense`.
- `payableNext30Days`: despesas pendentes com vencimento entre hoje e hoje mais 30 dias.
- `overdueExpenses`: despesas com status `OVERDUE`.
- `receivableNext30Days`: receitas atrasadas ou pendentes com vencimento entre hoje e hoje mais 30 dias.
- `cashFlowNext30Days`: `receivableNext30Days - payableNext30Days - overdueExpenses`.
- Despesas `PENDING` vencidas sao convertidas para `OVERDUE` pelo job diario de vencimentos; por isso `overdueExpenses` usa apenas status `OVERDUE`.
- `ACCOUNTANT` pode consultar resumo financeiro.


### GET /api/financial/cash-flow

**Descrição:** Retorna o fluxo de caixa projetado anual da fazenda, com saldo acumulado ao fim de cada mês.

**Autenticação e permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro ativo à fazenda.

**Query params:** `farmId` e `year` são obrigatórios. `year` deve ser maior que zero.

**Exemplo:** `GET /api/financial/cash-flow?farmId=8&year=2026`

**Resposta de sucesso:**
```json
{
  "farmId": 8,
  "year": 2026,
  "openingBalance": 20000.00,
  "closingBalance": 45000.00,
  "points": [{"month": 1, "label": "Jan", "income": 30000.00, "expense": 5000.00, "netFlow": 25000.00, "balance": 45000.00}]
}
```

**Regras de cálculo:** Sempre retorna 12 pontos, de `Jan` a `Dez`. O saldo inicial é a soma de receitas `PAID` menos despesas `PAID` anteriores ao ano, usando `paidAt` ou, em dados legados sem esse valor, `transactionDate`. Dentro do ano, `PAID` usa essa mesma data; `PENDING` e `OVERDUE` usam `dueDate`. Contas abertas sem vencimento não entram; contas abertas anteriores ao ano entram em janeiro. Apenas registros `ACTIVE` da fazenda, com status `PAID`, `PENDING` ou `OVERDUE`, são considerados. `netFlow = income - expense`; cada saldo mensal acumula o saldo anterior e `closingBalance` é o saldo de dezembro.

**Possíveis erros/status HTTP:** `400` quando `farmId` ou `year` não forem informados, ou quando `year` for igual ou menor que zero; `401` sem autenticação; `403` sem acesso à fazenda; `404` para fazenda inexistente.

### GET /api/financial/agenda/summary

**Descrição:**
Retorna os totais da agenda financeira da fazenda, considerando contas em aberto a receber e a pagar.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1,
  "status": "ALL",
  "type": "ALL",
  "periodDays": 30,
  "harvestSeasonIds": [10, 11]
}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- `status`: `ALL`, `PENDING` ou `OVERDUE`. Default: `ALL`.
- `type`: `ALL`, `RECEIVABLE` ou `PAYABLE`. Default: `ALL`.
- `periodDays`: deve ser maior que zero quando informado.
- `harvestSeasonIds`: aceita query params repetidos, por exemplo `harvestSeasonIds=10&harvestSeasonIds=11`.

**Resposta de sucesso:**
```json
{
  "farmId": 1,
  "overdueReceivable": {
    "count": 2,
    "totalAmount": 8500.00
  },
  "overduePayable": {
    "count": 3,
    "totalAmount": 4200.00
  },
  "pendingReceivable": {
    "count": 5,
    "totalAmount": 18000.00
  },
  "pendingPayable": {
    "count": 4,
    "totalAmount": 7600.00
  },
  "openReceivable": {
    "count": 7,
    "totalAmount": 26500.00
  },
  "openPayable": {
    "count": 7,
    "totalAmount": 11800.00
  }
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para query params inválidos, `periodDays <= 0` ou safra de outra fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda.
- `404 Not Found` se a fazenda ou alguma safra informada não existir.

**Observações de regra de negócio:**
- Considera apenas movimentações com `recordStatus=ACTIVE`, `status` em `PENDING` ou `OVERDUE`, `dueDate` preenchido e `farmId` igual ao informado.
- Ignora sempre movimentações `PAID`, `CANCELED`, `DELETED`, sem `dueDate` ou de outra fazenda.
- `RECEIVABLE` mapeia movimentações `INCOME`; `PAYABLE` mapeia movimentações `EXPENSE`.
- Conta vencida: `status=OVERDUE` ou `status=PENDING` com `dueDate` anterior à data atual do backend.
- Conta pendente futura: `status=PENDING` com `dueDate` maior ou igual à data atual do backend.
- `openReceivable` soma `overdueReceivable` e `pendingReceivable`.
- `openPayable` soma `overduePayable` e `pendingPayable`.
- Se `status=PENDING`, campos vencidos retornam zero. Se `status=OVERDUE`, campos pendentes retornam zero.
- Se `type=RECEIVABLE`, campos payable retornam zero. Se `type=PAYABLE`, campos receivable retornam zero.
- `periodDays` filtra contas futuras entre hoje e hoje mais o período. Em `status=ALL`, contas vencidas continuam entrando mesmo antes de hoje. Em `status=OVERDUE`, `periodDays` não altera as vencidas.
- Quando `harvestSeasonIds` é informado, todas as safras devem existir e pertencer à mesma fazenda consultada.
- O summary agrega no service a partir da mesma consulta base da listagem para manter as regras calculadas da agenda iguais nos dois endpoints.

### GET /api/financial/agenda

**Descrição:**
Lista de forma paginada as contas em aberto da agenda financeira da fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1,
  "status": "ALL",
  "type": "ALL",
  "periodDays": 30,
  "harvestSeasonIds": [10, 11],
  "page": 0,
  "size": 20
}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- `status`: `ALL`, `PENDING` ou `OVERDUE`. Default: `ALL`.
- `type`: `ALL`, `RECEIVABLE` ou `PAYABLE`. Default: `ALL`.
- `periodDays`: deve ser maior que zero quando informado.
- `harvestSeasonIds`: aceita query params repetidos, por exemplo `harvestSeasonIds=10&harvestSeasonIds=11`.
- `page`
- `size`

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 101,
      "farmId": 1,
      "description": "Parcela oficina trator",
      "agendaType": "PAYABLE",
      "transactionType": "EXPENSE",
      "agendaStatus": "OVERDUE",
      "paymentStatus": "PENDING",
      "amount": 1250.00,
      "dueDate": "2026-07-01",
      "daysOverdue": 6,
      "daysUntilDue": null,
      "categoryId": 4,
      "categoryName": "Manutenção",
      "harvestSeasonId": 12,
      "harvestSeasonName": "Safra Soja 2025/2026"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para query params inválidos, `periodDays <= 0` ou safra de outra fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda.
- `404 Not Found` se a fazenda ou alguma safra informada não existir.

**Observações de regra de negócio:**
- Usa a mesma base de filtros e regras do summary da agenda.
- `agendaType` é `RECEIVABLE` para `INCOME` e `PAYABLE` para `EXPENSE`.
- `agendaStatus` é calculado pela agenda e pode ser `OVERDUE` mesmo quando o `paymentStatus` original ainda é `PENDING`, caso `dueDate` seja anterior à data atual.
- `daysOverdue` é preenchido apenas quando `agendaStatus=OVERDUE`.
- `daysUntilDue` é preenchido apenas quando `agendaStatus=PENDING`.
- A ordenação é fixa por urgência: `dueDate ASC`. Contas vencidas aparecem primeiro e, depois, pendentes futuras da mais próxima para a mais distante.
- Sort customizado não é aceito nesta versão.
- `harvestSeasonIds` filtra movimentações vinculadas a qualquer uma das safras informadas, desde que todas pertençam à fazenda consultada.

### GET /api/financial/alerts

**Descrição:**
Retorna os principais alertas financeiros da fazenda selecionada para o Dashboard.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Resposta de sucesso:**
```json
{
  "farmId": 1,
  "overdueBills": [
    {
      "transactionId": 101,
      "description": "Boleto fornecedor AgroSul",
      "categoryName": "Insumos",
      "amount": 3200.00,
      "dueDate": "2026-07-02",
      "daysOverdue": 3
    },
    {
      "transactionId": 102,
      "description": "Parcela oficina trator",
      "categoryName": "Manutenção",
      "amount": 1250.00,
      "dueDate": "2026-07-04",
      "daysOverdue": 1
    }
  ],
  "dueToday": {
    "count": 2,
    "totalAmount": 1850.00
  },
  "dueNext7Days": {
    "count": 5,
    "totalAmount": 7400.00
  }
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para query params inválidos ou categorias de filtro que não pertencem à fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda.

**Observações de regra de negócio:**
- Considera apenas movimentações da fazenda informada com `recordStatus=ACTIVE` e `type=EXPENSE`.
- Ignora movimentações `PAID`, `CANCELED`, `DELETED`, `INCOME`, de outra fazenda ou sem `dueDate`.
- `overdueBills`: contas atrasadas com status `OVERDUE` ou status `PENDING` e `dueDate` anterior à data atual.
- `overdueBills` é ordenado por `dueDate ASC` e `amount DESC`, limitado a 20 itens.
- `daysOverdue`: diferença em dias entre `dueDate` e a data atual.
- `dueToday`: despesas `PENDING` com `dueDate` igual à data atual.
- `dueNext7Days`: despesas `PENDING` com `dueDate` maior que a data atual e menor ou igual à data atual mais 7 dias.
- `dueNext7Days` não inclui contas que vencem hoje.

### GET /api/financial/upcoming-bills

**Descrição:**
Lista contas a vencer de uma fazenda.

**Autenticação:** Sim
**Permissão:** `ADMIN`, `PRODUCER`, `EMPLOYEE` ou `ACCOUNTANT` com acesso financeiro à fazenda.

**Path params:**
```json
{}
```

**Query params:**
```json
{
  "farmId": 1,
  "page": 0,
  "size": 10,
  "sort": "id",
  "direction": "ASC"
}
```

**Body esperado:**
```json
{}
```

**Campos obrigatórios:**
- `farmId`

**Campos opcionais:**
- `page`
- `size`
- `sort`
- `direction`

**Resposta de sucesso:**
```json
{
  "content": [
    {
      "id": 1,
      "description": "Compra de sementes",
      "amount": 2500.00,
      "status": "PENDING",
      "dueDate": "2026-06-30",
      "farmId": 1,
      "categoryId": 1,
      "categoryName": "Insumos"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

**Possíveis erros/status HTTP:**
- `400 Bad Request` para query params inválidos ou categorias de filtro que não pertencem à fazenda.
- `401 Unauthorized` para cookie ausente, inválido ou expirado.
- `403 Forbidden` para usuário sem acesso financeiro à fazenda.

**Observações de regra de negócio:**
- Retorna apenas despesas (`EXPENSE`) com `recordStatus=ACTIVE`.
- Considera status `PENDING` e `OVERDUE`.
- Exige `dueDate` preenchido.


### GET /api/financial/reports

Retorna o relatório financeiro consolidado. Parâmetros obrigatórios: `farmId`, `startDate`, `endDate` e `basis` (`CASH` ou `ACCRUAL`); `harvestSeasonIds` e `categoryIds` são opcionais e aceitam valores repetidos ou CSV. ADMIN e vínculos ativos PRODUCER, EMPLOYEE e ACCOUNTANT podem consultar.

Em `ACCRUAL`, a data de referência é `transactionDate`. Em `CASH`, PAID usa `paidAt` (com fallback para `transactionDate`) e PENDING/OVERDUE usa `dueDate`. A resposta contém resumo, evolução mensal, categorias, safras, indicadores e `unallocated`. Valores abertos sem `dueDate` no regime de caixa ficam somente em `unallocated`. Margens retornam zero quando não há receita.

### GET /api/financial/reports/transactions

Recebe os mesmos filtros do consolidado e `page`, `size`, `sort` e `direction`. Retorna a paginação padrão com `referenceDate`, permitindo que o frontend use `periodStart` e `periodEnd` de um ponto mensal para exibir exatamente as movimentações daquele mês. Parâmetros inválidos retornam 400, acesso negado 403 e fazenda, categoria ou safra inexistente 404.
