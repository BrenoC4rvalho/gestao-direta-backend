#!/usr/bin/env bash

set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for Testcontainers but was not found in PATH." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker is unavailable. Start Docker and ensure this user can access its daemon." >&2
  echo "On Linux, verify /var/run/docker.sock permissions and docker group membership." >&2
  exit 1
fi

./mvnw test
