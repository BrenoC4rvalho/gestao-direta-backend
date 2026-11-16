# Production deployment (release candidate)

Production uses only the Spring Boot API and PostgreSQL containers. Caddy is a separately managed shared reverse proxy on the VPS.

## Before starting

- Configure the DNS A record for `api.gestaodireta.brenocarvalhodev.io`.
- Create the external Docker network once: `docker network create proxy`.
- Copy `.env.prod.example` to `.env.prod` and set strong unique secrets.
- `compose.prod.yaml` reads `POSTGRES_*` values for Compose interpolation. Run commands with `--env-file .env.prod`.
- The production API uses Gemini by default; Ollama and Whisper are development-only. Enable Gemini features only after providing a valid API key.
- HikariCP uses safe defaults from `application.yaml`. The optional `SPRING_DATASOURCE_HIKARI_*` variables in `.env.prod` override those values without a code change or image rebuild; recreate only the API container after changing them so Compose injects the updated environment.

## Local verification

```bash
mvn -B clean verify
docker network inspect proxy >/dev/null 2>&1 || docker network create proxy
cp .env.prod.example .env.prod
# Edit .env.prod with secure local test values.
docker compose --env-file .env.prod -f compose.prod.yaml config
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
docker compose --env-file .env.prod -f compose.prod.yaml logs -f api
```

To update only the API container after publishing a new backend image or rebuilding the API locally, keep PostgreSQL running and recreate only the API service:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d --no-deps --build api
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=100 api
```

This does not recreate the `postgres` service or its `postgres_data` volume. Confirm the startup log contains the effective `HikariCP -` values before considering the update complete.

If only `.env.prod` changed, omit `--build` and force recreation of the API container:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d --no-deps --force-recreate api
```

The production Compose does not publish the API or database ports. For local HTTP smoke testing, use a temporary override or a local Caddy container on the proxy network. Do not publish PostgreSQL on a public VPS interface.

## VPS

The shared Caddy container and API must be on the external `proxy` network. Caddy routes to the Compose service DNS name `api:8080` only if it is unambiguous; prefer a unique network alias when other projects are deployed.

The Spring Boot context path is `/api`. Telegram webhook path: `/api/webhooks/messaging/telegram`. Set `TELEGRAM_WEBHOOK_SECRET` and register the same secret with Telegram.

Back up the PostgreSQL volume using a database-consistent backup before upgrades. Validate Flyway migrations against a disposable copy of the database before production deployment.

No Git tag or release should be created until local build, integration tests, HTTPS and Telegram webhook smoke tests pass.

The Hikari environment variable names are intentionally documented explicitly. Spring Boot's relaxed environment binding treats underscores as nested-property separators; binding them directly to `HikariDataSource` can interpret names such as `CONNECTION_TIMEOUT` as `connection.timeout`, call `getConnection()` during binding, and then fail when a later property is applied to the sealed pool. The application reads the six documented override values before constructing the pool, so the existing `.env.prod` names remain supported without late mutation.
