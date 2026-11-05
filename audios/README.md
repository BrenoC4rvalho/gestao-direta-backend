# Benchmark de áudio financeiro

Este dataset contém exatamente 50 falas sintéticas em português brasileiro,
identificadas de `AUDIO_001` a `AUDIO_050`. Os arquivos finais são OGG com codec
Opus mono, correspondendo ao formato aceito pelo fluxo Telegram e pelo serviço
local Whisper. O áudio PCM é normalizado para 16 kHz antes da codificação; o
stream Opus é reportado pelo contêiner como 48 kHz.

## Geração

Requisitos: Python 3.11+, `piper-tts`, `av` e `ffmpeg`. O PyAV é usado como
equivalente programático ao `ffprobe` para validar o codec e decodificar todos
os frames.

```bash
python -m pip install piper-tts av
mkdir -p audios/model
curl -L -o audios/model/pt_BR-faber-medium.onnx \
  https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/faber/medium/pt_BR-faber-medium.onnx
curl -L -o audios/model/pt_BR-faber-medium.onnx.json \
  https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/faber/medium/pt_BR-faber-medium.onnx.json
python audios/scripts/generate_audio.py
```

O script preserva arquivos existentes. Use `--force` para regenerar todos ou
`--id AUDIO_023` para regenerar um caso específico. A validação escreve
`generation-report.json` com duração, tamanho, codec e SHA-256.

```bash
python audios/scripts/validate_audio.py
```

Não há dados pessoais reais nem credenciais no dataset. O modelo Piper não é
versionado; os 50 arquivos OGG/Opus gerados são os fixtures versionados do
benchmark.
