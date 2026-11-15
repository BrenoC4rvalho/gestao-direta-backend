# Production deployment (release candidate)

Production uses only the Spring Boot API and PostgreSQL containers. Caddy is a separately managed shared reverse proxy on the VPS.

## Before starting

- Configure the DNS A record for `api.gestaodireta.brenocarvalhodev.io`.
- Create the external Docker network once: `docker network create proxy`.
- Copy `.env.prod.example` to `.env.prod` and set strong unique secrets.
- `compose.prod.yaml` reads `POSTGRES_*` values for Compose interpolation. Run commands with `--env-file .env.prod`.
- The production API uses Gemini by default; Ollama and Whisper are development-only. Enable Gemini features only after providing a valid API key.

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

The production Compose does not publish the API or database ports. For local HTTP smoke testing, use a temporary override or a local Caddy container on the proxy network. Do not publish PostgreSQL on a public VPS interface.

## VPS

The shared Caddy container and API must be on the external `proxy` network. Caddy routes to the Compose service DNS name `api:8080` only if it is unambiguous; prefer a unique network alias when other projects are deployed.

The Spring Boot context path is `/api`. Telegram webhook path: `/api/webhooks/messaging/telegram`. Set `TELEGRAM_WEBHOOK_SECRET` and register the same secret with Telegram.

Back up the PostgreSQL volume using a database-consistent backup before upgrades. Validate Flyway migrations against a disposable copy of the database before production deployment.

No Git tag or release should be created until local build, integration tests, HTTPS and Telegram webhook smoke tests pass.
