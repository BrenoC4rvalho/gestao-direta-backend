# Gestão Direta API — Documentação para Codex

Arquivos incluídos:

- `AGENTS.md`
- `docs/architecture.md`
- `docs/modules.md`
- `docs/api-rules.md`
- `docs/database.md`
- `docs/codex-tasks.md`

Copie estes arquivos para a raiz do repositório `api`.

## IA com Ollama

O backend possui o endpoint `POST /api/ai/transactions/parse` para transformar texto livre em uma sugestao estruturada de movimentacao financeira. Ele nao salva movimentacoes, nao cria `FinancialTransaction` e nao altera categoria, safra ou fazenda.

Permissoes: `ADMIN`, `PRODUCER` e `EMPLOYEE` podem usar conforme acesso ativo a fazenda. `ACCOUNTANT`, vinculo `INACTIVE` e usuario sem vinculo recebem `403`.

Configuracao local padrao:

```properties
APP_AI_PROVIDER=ollama
APP_AI_OLLAMA_BASE_URL=http://localhost:11434
APP_AI_OLLAMA_MODEL=llama3.1:8b
```

Se a API tambem rodar dentro do Docker Compose, use a URL interna `http://ollama:11434`.

Subir Ollama, baixar o modelo configurado e conferir os modelos locais:

```bash
docker compose up -d ollama
docker exec -it gestao-direta-ollama ollama pull llama3.1:8b
docker exec -it gestao-direta-ollama ollama list
```

Se preferir um modelo menor, baixe:

```bash
docker exec -it gestao-direta-ollama ollama pull llama3.2:3b
```

Ao usar outro modelo, altere tambem a configuracao:

```properties
app.ai.ollama.model=llama3.2:3b
```

ou a variavel de ambiente:

```properties
APP_AI_OLLAMA_MODEL=llama3.2:3b
```

Se o modelo configurado nao tiver sido baixado no Ollama, o endpoint retorna `503 Service Unavailable` com mensagem amigavel de modelo indisponivel.

Rodar Spring local com Postgres e Ollama no Docker:

```bash
docker compose up -d postgres ollama
./mvnw spring-boot:run
```

Para trocar de provider futuramente, crie outra implementacao de `AiTextGenerationClient` e selecione via `app.ai.provider`.
