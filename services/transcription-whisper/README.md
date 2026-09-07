# Serviço local de transcrição Whisper

Este serviço recebe áudios OGG/Opus em `POST /transcribe` e os transcreve com
`faster-whisper`. Não persiste áudio ou transcrições.

## Execução local

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8090
```

Ou execute com Docker:

```bash
docker compose up --build transcription-whisper
```

O primeiro start baixa o modelo selecionado para o cache Hugging Face. O Compose
mantém esse cache em um volume. Não versione pesos ou caches.

## Configuração

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `WHISPER_MODEL` | `small` | Modelo faster-whisper, por exemplo `base`, `medium` ou `large-v3`. |
| `WHISPER_DEVICE` | `cpu` | Dispositivo, como `cpu` ou `cuda`. |
| `WHISPER_COMPUTE_TYPE` | `int8` | Precisão do CTranslate2. |
| `WHISPER_LANGUAGE` | `pt` | Idioma; use `auto` para autodetecção. |
| `WHISPER_MAX_UPLOAD_SIZE_MB` | `10` | Limite de upload. |
| `WHISPER_MAX_CONCURRENCY` | `1` | Transcrições simultâneas permitidas. |

Para CUDA, use uma imagem e bibliotecas NVIDIA compatíveis com a versão de
CTranslate2 escolhida. A configuração padrão é voltada a CPU.

## Endpoints

```bash
curl http://localhost:8090/health

curl -F file=@voice.ogg http://localhost:8090/transcribe
```

`/health` responde com o status e o modelo carregado. `/transcribe` responde
com `text`, `language` e `durationSeconds`.
