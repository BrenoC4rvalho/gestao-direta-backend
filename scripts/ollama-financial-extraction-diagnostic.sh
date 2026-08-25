#!/usr/bin/env bash
set -euo pipefail

base_url="${APP_AI_OLLAMA_BASE_URL:-http://localhost:11434}"
model="${APP_AI_OLLAMA_MODEL:-llama3.2:3b}"
phrases=(
  "Paguei 780 de manutenção da colheitadeira."
  "Gastei R$ 350,00 com diesel para o trator hoje."
  "Comprei R$ 2.300 de fertilizante para a soja."
  "Recebi R$ 4.800 pela venda de milho."
  "Paguei 450 reais de energia da fazenda ontem."
  "Paguei 780 manutenção colheitadeira"
  "350 no diesel do trator hj"
  "adubo soja 2300"
  "Gastei R$ 300 hoje."
  "oi, tudo bem?"
  "Paguei 780 de manutenção da colheitadeira."
  "Gastei R$ 350,00 com diesel para o trator hoje."
)

for phrase in "${phrases[@]}"; do
  request=$(jq -n --arg model "$model" --arg prompt "$phrase" '{model: $model, prompt: $prompt, stream: false, format: "json", options: {temperature: 0}}')
  raw=$(curl --fail --silent --show-error "$base_url/api/generate" --header 'Content-Type: application/json' --data "$request")
  printf '\nInput: %s\nRaw: %s\nParsed: %s\n' "$phrase" "$raw" "$(jq -r '.response | fromjson' <<<"$raw")"
done
