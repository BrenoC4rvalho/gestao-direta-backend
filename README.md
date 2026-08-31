# Gestão Direta API

Backend do Gestão Direta, um sistema de gestão financeira voltado a pequenos produtores rurais. A API centraliza autenticação, administração de usuários e fazendas, lançamentos financeiros, safras, atividades produtivas, mensageria e integrações com Telegram e IA.

## Sobre o projeto

O backend expõe uma API REST com autenticação baseada em JWT armazenado em cookie HTTP-only. Ele oferece gestão financeira por fazenda, incluindo categorias, lançamentos, contas a pagar, alertas, agenda, relatórios e pendências financeiras geradas a partir de mensagens do Telegram.

Não há cadastro público: usuários são administrados por usuários com perfil `ADMIN`.

## Principais funcionalidades

- Autenticação, sessão, logout, troca e recuperação de senha.
- Administração de usuários, contatos e contas de mensageria vinculadas.
- Cadastro de fazendas e controle de acesso por vínculo e papel na fazenda.
- Categorias, movimentações, pagamentos, agenda, alertas, resumo, fluxo de caixa e relatórios financeiros.
- Exportação das movimentações filtradas em XLSX e PDF, sem depender da página atual da listagem.
- Safras e atividades produtivas vinculadas às fazendas.
- Recebimento e consulta de mensagens do Telegram.
- Extração de dados financeiros por IA, com criação de pendência para revisão, aprovação ou rejeição.

Consulte a interface Swagger para a relação completa e atualizada de endpoints.

## Tecnologias

| Tecnologia | Uso no projeto |
| --- | --- |
| Java 21 | Linguagem e runtime |
| Maven Wrapper / Maven 3.9.16 | Build e gerenciamento de dependências |
| Spring Boot 3.5.15 | Framework principal |
| Spring Web, Validation e Actuator | API HTTP, validação e observabilidade |
| Spring Security e Auth0 Java JWT 4.5.2 | Autenticação e autorização |
| Spring Data JPA / Hibernate | Persistência |
| PostgreSQL | Banco de dados |
| Flyway 11.7.2 | Versionamento do schema |
| SpringDoc OpenAPI 2.8.16 | OpenAPI e Swagger UI |
| Gemini e Ollama | Providers configuráveis para extração financeira por IA |
| Apache POI 5.5.1 | Geração de exportações XLSX |
| OpenPDF 3.0.3 | Geração de exportações PDF |
| Docker Compose | Serviços locais de PostgreSQL e Ollama |
| JUnit, Mockito, Spring Security Test e Testcontainers | Testes |
| Spotless 2.43.0 com google-java-format AOSP | Formatação Java |

## Arquitetura

O projeto é um monólito modular em camadas. Cada domínio concentra controllers, DTOs, entidades, repositories, services, mappers, enums e regras de acesso quando aplicável.

| Módulo | Responsabilidade |
| --- | --- |
| `ai` | Clientes de IA, parsing e extração de movimentações a partir de texto. |
| `auth` | Login, JWT, cookie de sessão, segurança e recuperação de senha. |
| `farm` | Fazendas, vínculos de usuários e permissões por fazenda. |
| `financial` | Categorias, lançamentos, pendências, agenda, alertas e relatórios financeiros. |
| `harvest` | Safras e atividades produtivas. |
| `messaging` | Contas, conversas, mensagens e integração Telegram. |
| `shared` | Configurações compartilhadas, exceções, auditoria, paginação e respostas. |
| `system` | Status da aplicação e logging de inicialização. |
| `user` | Usuários e seus contatos. |

## Estrutura do projeto

```text
src/
├── main/
│   ├── java/br/com/gestaodireta/
│   │   ├── ai/ auth/ farm/ financial/ harvest/
│   │   ├── messaging/ shared/ system/ user/
│   │   └── ApiApplication.java
│   └── resources/
│       ├── application.yaml
│       ├── application-test.yaml
│       └── db/migration/
└── test/java/br/com/gestaodireta/

scripts/
├── demo-data.sql
├── mock_pending_financial_transactions.sql
└── ollama-financial-extraction-diagnostic.sh
```

## Pré-requisitos

- Java 21.
- Docker e Docker Compose para usar o PostgreSQL definido no Compose e para os testes de integração com Testcontainers.
- Ollama, somente quando `APP_AI_PROVIDER=ollama` e o provider não estiver sendo executado no Compose.

Gemini é acessado por API e não exige Ollama instalado localmente.

O Maven Wrapper está versionado no repositório; não é necessário instalar Maven globalmente.

## Configuração do ambiente

Crie o arquivo local de ambiente a partir do modelo:

```bash
cp .env.example .env
```

Preencha, ao menos, a conexão PostgreSQL e um segredo JWT forte. O Spring Boot importa `.env` como arquivo de propriedades; o arquivo é ignorado pelo Git.

Exemplo seguro para desenvolvimento local:

```env
DB_URL=jdbc:postgresql://localhost:5432/gestaodireta
DB_USERNAME=gestaodireta
DB_PASSWORD=<senha-do-postgres>

APP_JWT_SECRET=<segredo-longo-e-aleatorio>
APP_JWT_EXPIRATION_MINUTES=120
APP_JWT_REMEMBER_ME_EXPIRATION_DAYS=15
APP_COOKIE_SECURE=false
APP_COOKIE_SAME_SITE=Lax
APP_CORS_ALLOWED_ORIGINS=http://localhost:4200

AI_FINANCIAL_EXTRACTION_ENABLED=false
AI_FINANCIAL_EXTRACTION_MODEL=
AI_FINANCIAL_EXTRACTION_TIMEOUT_SECONDS=20
AI_FINANCIAL_EXTRACTION_MINIMUM_CONFIDENCE=0.60
AI_FINANCIAL_EXTRACTION_DIAGNOSTIC_ONLY=false
APP_AI_PROVIDER=ollama
APP_AI_OLLAMA_BASE_URL=http://localhost:11434
APP_AI_OLLAMA_MODEL=llama3.2:3b
APP_AI_OLLAMA_FORMAT=json
APP_AI_OLLAMA_TEMPERATURE=0
APP_AI_GEMINI_API_KEY=
APP_AI_GEMINI_MODEL=gemini-3.1-flash-lite
AI_HEALTH_CHECK_ENABLED=true
AI_HEALTH_CHECK_TIMEOUT_SECONDS=5
AI_HEALTH_CHECK_CACHE_SECONDS=60

TELEGRAM_ENABLED=false
TELEGRAM_BOT_TOKEN=
TELEGRAM_WEBHOOK_SECRET=
TELEGRAM_API_BASE_URL=https://api.telegram.org
MESSAGING_CONVERSATION_EXPIRATION_HOURS=24
```

### Variáveis de ambiente

| Variável | Obrigatória | Descrição | Valor padrão |
| --- | --- | --- | --- |
| `DB_URL` | Sim | URL JDBC do PostgreSQL. | — |
| `DB_USERNAME` | Sim | Usuário do banco. | — |
| `DB_PASSWORD` | Sim | Senha do banco. | — |
| `APP_JWT_SECRET` | Produção | Segredo de assinatura do JWT. | Valor inseguro de desenvolvimento configurado na aplicação. |
| `APP_JWT_EXPIRATION_MINUTES` | Não | Duração da sessão padrão em minutos. | `120` |
| `APP_JWT_REMEMBER_ME_EXPIRATION_DAYS` | Não | Duração da sessão com “lembrar de mim” em dias. | `15` |
| `APP_COOKIE_SECURE` | Não | Exige HTTPS no cookie de sessão. | `false` |
| `APP_COOKIE_SAME_SITE` | Não | Política SameSite do cookie. | `Lax` |
| `APP_CORS_ALLOWED_ORIGINS` | Não | Origens permitidas pelo CORS; aceita lista configurada pelo Spring. | `http://localhost:4200` |
| `PASSWORD_RECOVERY_CODE_EXPIRATION_MINUTES` | Não | Expiração do código de recuperação. | `10` |
| `PASSWORD_RECOVERY_MAX_ATTEMPTS` | Não | Máximo de tentativas por código. | `5` |
| `PASSWORD_RECOVERY_REQUEST_WINDOW_MINUTES` | Não | Janela de limitação de solicitações. | `15` |
| `PASSWORD_RECOVERY_MAX_REQUESTS_PER_WINDOW` | Não | Máximo de solicitações por janela. | `3` |
| `AI_FINANCIAL_EXTRACTION_ENABLED` | Não | Habilita extração financeira por IA. | `false` |
| `AI_FINANCIAL_EXTRACTION_MODEL` | Não | Identificador registrado na pendência processada. | Vazio |
| `AI_FINANCIAL_EXTRACTION_TIMEOUT_SECONDS` | Não | Tempo limite da extração. | `20` |
| `AI_FINANCIAL_EXTRACTION_MINIMUM_CONFIDENCE` | Não | Confiança mínima aceita. | `0.60` |
| `AI_FINANCIAL_EXTRACTION_DIAGNOSTIC_ONLY` | Não | Registra diagnóstico sem criar pendência. | `false` |
| `APP_AI_PROVIDER` | Não | Provider de IA: `ollama` ou `gemini`. | `ollama` |
| `APP_AI_OLLAMA_BASE_URL` | Não | URL base do Ollama. | `http://localhost:11434` |
| `APP_AI_OLLAMA_MODEL` | Não | Modelo enviado ao Ollama. | `llama3.2:3b` |
| `APP_AI_OLLAMA_FORMAT` | Não | Formato solicitado ao Ollama. | `json` |
| `APP_AI_OLLAMA_TEMPERATURE` | Não | Temperatura enviada ao Ollama. | `0` |
| `APP_AI_GEMINI_API_KEY` | Sim, se Gemini estiver habilitado | Chave da Gemini API, usada somente pelo backend. | — |
| `APP_AI_GEMINI_MODEL` | Não | Modelo Gemini enviado à API. | `gemini-3.1-flash-lite` |
| `AI_HEALTH_CHECK_ENABLED` | Não | Habilita a verificação de conectividade do provider de IA. | `true` |
| `AI_HEALTH_CHECK_TIMEOUT_SECONDS` | Não | Timeout da verificação leve de saúde da IA. | `5` |
| `AI_HEALTH_CHECK_CACHE_SECONDS` | Não | TTL do resultado de saúde para evitar chamadas repetidas ao provider. | `60` |
| `TELEGRAM_ENABLED` | Não | Habilita a integração Telegram. | `false` |
| `TELEGRAM_BOT_TOKEN` | Sim, se Telegram estiver habilitado | Token do bot. | Vazio |
| `TELEGRAM_WEBHOOK_SECRET` | Sim, se Telegram estiver habilitado | Secret validado no webhook recebido. | Vazio |
| `TELEGRAM_API_BASE_URL` | Não | URL base da Bot API. | `https://api.telegram.org` |
| `MESSAGING_CONVERSATION_EXPIRATION_HOURS` | Não | Expiração das conversas de mensageria. | `24` |

Nunca versione `.env`, tokens ou segredos. Em produção, use `APP_JWT_SECRET` forte e `APP_COOKIE_SECURE=true`.

## Banco de dados e Docker

O banco é PostgreSQL. O arquivo `compose.yaml` disponibiliza o serviço `postgres` com PostgreSQL 16 Alpine, banco `gestaodireta` e porta `5432` publicada localmente. Ele também disponibiliza o serviço `ollama` na porta `11434`; não há container da aplicação backend no Compose atual.

Para iniciar os serviços locais:

```bash
docker compose up -d postgres ollama
```

Configure `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` em `.env` de acordo com o PostgreSQL utilizado. Para uma instância manual, crie o banco antes de iniciar a aplicação. O schema é criado e evoluído pelo Flyway.

## Flyway

As migrations ficam em `src/main/resources/db/migration/` e são executadas no startup com `spring.flyway.enabled=true`. O Hibernate está configurado com `ddl-auto=validate`: ele valida o schema, mas não deve alterá-lo.

Para mudanças estruturais, crie uma nova migration versionada nesse diretório. Não edite migrations já aplicadas em ambientes compartilhados, pois isso pode causar divergência de checksum.

O plugin Maven do Flyway usa `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` do ambiente.

## Inteligência artificial

O provider é escolhido por `APP_AI_PROVIDER`. A API aceita `gemini` e `ollama`; não há fallback automático entre eles. Independentemente do provider, a resposta estruturada passa por validações determinísticas de valor, tipo, descrição e confiança antes de poder criar uma pendência financeira para revisão.

### Gemini

Gemini é acessado por API. Configure a chave somente no ambiente do backend:

```env
APP_AI_PROVIDER=gemini
APP_AI_GEMINI_API_KEY=<sua-chave-da-api>
APP_AI_GEMINI_MODEL=gemini-3.1-flash-lite
```

A integração usa Structured Outputs com JSON Schema; a validação de domínio continua sendo executada no Java.

### Ollama

O provider padrão usa a API local do Ollama. Configure-o assim quando ele estiver disponível localmente ou no Compose:

```env
APP_AI_PROVIDER=ollama
APP_AI_OLLAMA_BASE_URL=http://localhost:11434
APP_AI_OLLAMA_MODEL=llama3.2:3b
APP_AI_OLLAMA_FORMAT=json
APP_AI_OLLAMA_TEMPERATURE=0
```

Com o serviço Docker iniciado, baixe o modelo padrão dentro do container:

```bash
docker exec -it gestao-direta-ollama ollama pull llama3.2:3b
```

Para executar Ollama fora do Docker, inicie-o conforme sua instalação e mantenha `APP_AI_OLLAMA_BASE_URL=http://localhost:11434`. Ative o processamento com `AI_FINANCIAL_EXTRACTION_ENABLED=true`.

O script abaixo é um diagnóstico manual de respostas do Ollama e requer `curl` e `jq`:

```bash
./scripts/ollama-financial-extraction-diagnostic.sh
```

## Telegram

A integração Telegram é opcional e permanece desativada por padrão. Ao habilitá-la, `TELEGRAM_BOT_TOKEN` e `TELEGRAM_WEBHOOK_SECRET` devem estar preenchidos; a aplicação falha na inicialização se a configuração estiver incompleta.

O webhook público é:

```text
POST /api/webhooks/messaging/telegram
```

Ele valida o header `X-Telegram-Bot-Api-Secret-Token` contra `TELEGRAM_WEBHOOK_SECRET`. O registro do webhook no Telegram deve apontar para uma URL pública que alcance esse endpoint; o backend não configura esse registro automaticamente.

No desenvolvimento local, exponha a API por uma URL pública temporária, por exemplo com ngrok, e registre no Telegram uma URL no formato `https://<seu-dominio-publico>/api/webhooks/messaging/telegram`. Não versione nem documente URLs temporárias pessoais.

Usuários podem vincular uma conta de mensageria e, em uma conversa com contexto de fazenda, enviar informações financeiras. A integração também oferece fluxos de recuperação de senha por Telegram quando houver contato elegível.

## Fluxo financeiro pelo Telegram

```text
Mensagem Telegram
→ validação de elegibilidade e contexto da fazenda
→ extração e validação por IA
→ pendência financeira para revisão
→ aprovação ou rejeição
→ movimentação financeira aprovada
```

Mensagens sem contexto financeiro suficiente não criam movimentações. Campos ausentes não devem ser inventados pela IA. Mesmo uma extração válida é registrada primeiro como pendência (`PENDING_REVIEW`) para revisão.

## Executando a aplicação

Com PostgreSQL configurado e `.env` preenchido, execute:

```bash
./mvnw spring-boot:run
```

A API fica disponível em `http://localhost:8080/api`.

## Swagger / OpenAPI

Com a aplicação em execução:

- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api/v3/api-docs`
- Health check: `http://localhost:8080/api/actuator/health`
- Status público da aplicação: `http://localhost:8080/api/system/status`

O health inclui o componente `ai`, que informa provider, modelo, conectividade e latência para usuários autorizados. Ele nunca expõe chaves, prompts, mensagens financeiras ou headers. A verificação real é cacheada pelo TTL configurado; quando a IA externa estiver indisponível, o componente `ai` ficará `DOWN` sem impedir o startup da API.

No startup, a aplicação também registra o resultado da verificação do provider configurado. Uma falha é registrada como aviso e não interrompe a inicialização.

## Relatórios e exportações

O relatório financeiro oferece visão consolidada por período e filtros de fazenda, safra e categoria, com base de caixa ou competência, evolução financeira mensal/trimestral, resumo por categoria e fluxo de caixa acumulado. O fluxo acumulado diferencia o cenário previsto do cenário que também considera valores vencidos ainda abertos.

Na página de movimentações, as exportações XLSX e PDF são geradas pelo backend com os mesmos filtros da listagem. A consulta de exportação busca todo o conjunto filtrado, portanto não é limitada pela paginação visível na interface.

## Autenticação e CORS

Após o login, o JWT é enviado no cookie `gd_session`, configurado como HTTP-only. A opção “lembrar de mim” altera a expiração do token. Uma alteração de credenciais invalida sessões anteriores por meio de `credentialsVersion`.

O CORS permite credenciais e é configurado por `APP_CORS_ALLOWED_ORIGINS`; não use `*` quando cookies forem necessários. O frontend deve enviar requisições com credenciais incluídas.

## Testes e qualidade de código

Os testes usam o profile `test`, IA fake e PostgreSQL provisionado por Testcontainers para cenários de integração. Execute:

```bash
./mvnw test
```

Para gerar o artefato e executar a suíte de testes:

```bash
./mvnw verify
```

Para gerar o pacote da aplicação:

```bash
./mvnw package
```

Para validar ou aplicar a formatação Java:

```bash
./mvnw spotless:check
./mvnw spotless:apply
```

Antes de criar um commit, também é recomendado executar:

```bash
git diff --check
```

## Scripts auxiliares

- `scripts/demo-data.sql`: cria dados de demonstração e faz uma limpeza controlada de dados operacionais; execute somente em ambiente local/de desenvolvimento.
- `scripts/mock_pending_financial_transactions.sql`: adiciona pendências financeiras de exemplo via contexto Telegram já existente; execute somente em ambiente local/de desenvolvimento.
- `scripts/ollama-financial-extraction-diagnostic.sh`: envia frases de diagnóstico diretamente ao Ollama.

Os scripts SQL não substituem migrations Flyway. Revise-os antes de executar, especialmente o script de dados de demonstração, que remove dados operacionais.

## Troubleshooting

### Não conecta ao PostgreSQL

Confira se o serviço `postgres` está ativo e se `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` correspondem à instância configurada.

### Flyway reporta checksum inválido

Não altere migrations aplicadas. Crie uma nova migration para mudanças adicionais e restaure a versão original da migration que já foi executada no ambiente.

### Ollama ou modelo indisponível

Confirme que o serviço está acessível em `APP_AI_OLLAMA_BASE_URL` e que o modelo de `APP_AI_OLLAMA_MODEL` foi baixado. Com Docker, use `docker compose up -d ollama` e execute o comando `ollama pull` mostrado acima.

### Telegram não processa mensagens

Verifique se `TELEGRAM_ENABLED=true`, se token e webhook secret foram definidos e se o Telegram está chamando o endpoint público com o header de secret correto.
