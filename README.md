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
APP_TRANSCRIPTION_PROVIDER=gemini
APP_TRANSCRIPTION_WHISPER_BASE_URL=http://localhost:8090
APP_TRANSCRIPTION_WHISPER_TIMEOUT=120s
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
| `APP_TRANSCRIPTION_PROVIDER` | Não | Provider de transcrição: `gemini` ou `whisper`; independente de `APP_AI_PROVIDER`. | `gemini` |
| `APP_TRANSCRIPTION_WHISPER_BASE_URL` | Não | URL do serviço local Whisper. | `http://localhost:8090` |
| `APP_TRANSCRIPTION_WHISPER_TIMEOUT` | Não | Timeout exclusivo da chamada ao Whisper. | `120s` |
| `APP_AI_AUDIO_TRANSCRIPTION_ENABLED` | Não | Habilita transcrição de mensagens de voz do Telegram. | `false` |
| `APP_AI_AUDIO_TRANSCRIPTION_MODEL` | Não | Modelo Gemini multimodal usado na transcrição. | `gemini-3.1-flash-lite` |
| `APP_AI_AUDIO_TRANSCRIPTION_MAX_DURATION_SECONDS` | Não | Duração máxima aceita para uma voz. | `60` |
| `APP_AI_AUDIO_TRANSCRIPTION_MAX_SIZE_MB` | Não | Tamanho máximo aceito para uma voz. | `10` |
| `APP_AI_AUDIO_TRANSCRIPTION_TIMEOUT_SECONDS` | Não | Timeout de upload e transcrição. | `30` |
| `APP_AI_AUDIO_TRANSCRIPTION_DEBUG_RESPONSE` | Não | Salva o corpo JSON da resposta Gemini para diagnóstico local. | `false` |
| `APP_AI_AUDIO_TRANSCRIPTION_DEBUG_RESPONSE_DIRECTORY` | Não | Diretório local usado para respostas Gemini de diagnóstico. | `telegram-audio-debug` |
| `APP_TELEGRAM_AUDIO_DEBUG_SAVE_ENABLED` | Não | Salva localmente, para diagnóstico, os bytes baixados de áudios do Telegram. | `false` |
| `APP_TELEGRAM_AUDIO_DEBUG_DIRECTORY` | Não | Diretório local relativo usado pelo diagnóstico de áudio. | `telegram-audio-debug` |
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

O banco é PostgreSQL. O arquivo `compose.yaml` disponibiliza PostgreSQL, Ollama e o serviço local `transcription-whisper`. O Whisper publica somente `127.0.0.1:8090` e mantém o cache de modelos em volume Docker; não há container da aplicação backend no Compose atual.

Para iniciar os serviços locais:

```bash
docker compose up -d postgres ollama transcription-whisper
```

Configure `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` em `.env` de acordo com o PostgreSQL utilizado. Para uma instância manual, crie o banco antes de iniciar a aplicação. O schema é criado e evoluído pelo Flyway.

## Flyway

As migrations ficam em `src/main/resources/db/migration/` e são executadas no startup com `spring.flyway.enabled=true`. O Hibernate está configurado com `ddl-auto=validate`: ele valida o schema, mas não deve alterá-lo.

Para mudanças estruturais, crie uma nova migration versionada nesse diretório. Não edite migrations já aplicadas em ambientes compartilhados, pois isso pode causar divergência de checksum.

O plugin Maven do Flyway usa `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` do ambiente.

## Base demonstrativa para desenvolvimento/TCC

O reset e seed da base demonstrativa está em [`scripts/reset_and_seed_tcc_demo.sql`](scripts/reset_and_seed_tcc_demo.sql). Ele remove os dados atuais do banco local `gestaodireta` e recria os dados usados no desenvolvimento e nas demonstrações do TCC.

```bash
set -a
source .env
set +a
PGPASSWORD="$DB_PASSWORD" psql "${DB_URL#jdbc:}" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -f scripts/reset_and_seed_tcc_demo.sql
```

Consulte usuários, senhas, pré-requisitos e as validações em [`scripts/SEED_TCC_DEMO.md`](scripts/SEED_TCC_DEMO.md). Execute somente em desenvolvimento.

## Inteligência artificial

O provider de extração de texto é escolhido por `APP_AI_PROVIDER`. A API aceita `gemini` e `ollama`; não há fallback automático entre eles. A transcrição é escolhida separadamente por `APP_TRANSCRIPTION_PROVIDER`, aceitando `gemini` e `whisper`.

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

### Mensagens de voz

Quando `APP_AI_AUDIO_TRANSCRIPTION_ENABLED=true`, mensagens de voz do Telegram são baixadas uma única vez e encaminhadas ao mesmo fluxo de texto acima. Com `APP_TRANSCRIPTION_PROVIDER=gemini`, a transcrição usa Files API e `generateContent`. Com `APP_TRANSCRIPTION_PROVIDER=whisper`, o backend envia multipart ao serviço local configurado. `audio/ogg` e `audio/opus` são enviados diretamente, sem conversão nem FFmpeg do sistema.

O áudio não é salvo no banco. Para diagnóstico local temporário, defina `APP_TELEGRAM_AUDIO_DEBUG_SAVE_ENABLED=true`: os mesmos bytes recebidos do Telegram são copiados, antes da transcrição, para `telegram-audio-debug/telegram-{updateId}-{sourceMessageId}.ogg`. O salvamento é best-effort e uma falha local não interrompe a transcrição; por padrão ele está desabilitado. O arquivo temporário criado na Files API é removido após a transcrição em modo best-effort; apenas o texto transcrito é mantido no histórico da mensagem para permitir as validações financeiras existentes. A chave `APP_AI_GEMINI_API_KEY` é obrigatória para a transcrição apenas quando `APP_TRANSCRIPTION_PROVIDER=gemini`.

Para visualizar o texto transcrito no log local, sem habilitar DEBUG para toda a aplicação, configure `logging.level.br.com.gestaodireta.messaging.service.TelegramVoiceMessageProcessor=DEBUG`. Cada transcrição concluída registra o provider selecionado e o texto retornado; por conter conteúdo informado pelo usuário, esse dado é emitido somente em DEBUG.

Para registrar o corpo JSON retornado pelo Gemini multimodal, ative `APP_AI_AUDIO_TRANSCRIPTION_DEBUG_RESPONSE=true`. A resposta é salva como `telegram-audio-debug/gemini-response-{updateId}-{sourceMessageId}.json`; não inclui headers, credenciais ou áudio.

Para reproduzir o mesmo áudio manualmente, defina as variáveis abaixo sem colocar a chave no comando. O primeiro POST cria uma sessão resumable; o segundo envia exatamente os bytes OGG; o último usa `generateContent` multimodal.

```bash
AUDIO_FILE=telegram-audio-debug/telegram-<updateId>-<sourceMessageId>.ogg
MIME_TYPE=audio/ogg
NUM_BYTES=$(wc -c < "$AUDIO_FILE")
UPLOAD_HEADERS=$(mktemp)

curl -sS -D "$UPLOAD_HEADERS" \
  -H "x-goog-api-key: $APP_AI_GEMINI_API_KEY" \
  -H "X-Goog-Upload-Protocol: resumable" \
  -H "X-Goog-Upload-Command: start" \
  -H "X-Goog-Upload-Header-Content-Length: $NUM_BYTES" \
  -H "X-Goog-Upload-Header-Content-Type: $MIME_TYPE" \
  -H "Content-Type: application/json" \
  -d '{"file":{"display_name":"telegram-voice-debug"}}' \
  https://generativelanguage.googleapis.com/upload/v1beta/files

UPLOAD_URL=$(awk 'BEGIN{IGNORECASE=1} /^x-goog-upload-url:/ {print $2}' "$UPLOAD_HEADERS" | tr -d '\r')
rm "$UPLOAD_HEADERS"

curl -sS "$UPLOAD_URL" \
  -H "Content-Length: $NUM_BYTES" \
  -H "X-Goog-Upload-Offset: 0" \
  -H "X-Goog-Upload-Command: upload, finalize" \
  -H "Content-Type: $MIME_TYPE" \
  --data-binary "@$AUDIO_FILE" > gemini-upload.json

FILE_URI=$(jq -r '.file.uri' gemini-upload.json)
FILE_MIME_TYPE=$(jq -r '.file.mimeType // "audio/ogg"' gemini-upload.json)

curl -sS -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent" \
  -H "x-goog-api-key: $APP_AI_GEMINI_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"contents\":[{\"parts\":[{\"text\":\"Transcreva o conteúdo falado neste áudio. Retorne apenas a transcrição, sem comentários adicionais.\"},{\"file_data\":{\"mime_type\":\"$FILE_MIME_TYPE\",\"file_uri\":\"$FILE_URI\"}}]}]}" \
  > gemini-response.json
```

Depois, compare `gemini-response.json` com a resposta gravada pela aplicação.

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

Os testes usam o profile `test`, IA fake e PostgreSQL provisionado por Testcontainers
para cenários de integração. Eles exigem Java 21 e Docker em execução, com acesso ao
daemon pelo usuário atual. O Maven configura automaticamente o agente do Mockito;
nenhum caminho local ou configuração manual de Byte Buddy é necessário.

Execute:

```bash
./mvnw test
```

Para validar o Docker antes de iniciar a suíte, execute:

```bash
./scripts/test-backend.sh
```

Se Docker estiver indisponível, verifique `docker info`, as permissões de
`/var/run/docker.sock` e a associação do usuário ao grupo `docker`.

### Benchmark de extração financeira por IA

O benchmark executa o conjunto de mensagens financeiras contra o provider real
configurado e avalia a criação de pendências, extração de campos, bloqueio de
mensagens incompletas e rejeição de mensagens inválidas. Ele usa PostgreSQL
temporário via Testcontainers, portanto exige Docker em execução.

Antes de executá-lo, exporte as variáveis de `.env`. Para Gemini, preencha
`APP_AI_GEMINI_API_KEY`; para Ollama, inicie o serviço e baixe o modelo
configurado. Evite usar chaves ou tokens diretamente na linha de comando.

```bash
set -a
source .env
set +a
./mvnw test -Pai-benchmark -Dai.benchmark.provider=gemini
```

Substitua `gemini` por `ollama` para avaliar somente o provider local, ou use
`both` para comparar os dois. Por padrão, `both` é usado e todo o dataset é
executado. Para uma validação rápida com os primeiros casos:

```bash
set -a
source .env
set +a
./mvnw test -Pai-benchmark -Dai.benchmark.provider=ollama -Dai.benchmark.limit=10
```

Os resultados são gerados em `target/ai-benchmark/`: `report.html` para leitura
visual, `summary.json` com métricas por provider, `results.csv` e
`results.jsonl` com o resultado de cada caso. Opcionalmente, limites podem
fazer o comando falhar, por exemplo:

```bash
./mvnw test -Pai-benchmark \
  -Dai.benchmark.provider=gemini \
  -Dai.benchmark.threshold.complete-accuracy=90 \
  -Dai.benchmark.threshold.invalid-rejection=90 \
  -Dai.benchmark.threshold.incomplete-blocking=90 \
  -Dai.benchmark.threshold.max-hallucination-rate=5
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
