# Benchmark de áudio do Gestão Direta

Contém 150 WAV mestre em pt-BR, manifesto, inventário e relatório de validação.
O gerador produz as fixtures OGG/Opus correspondentes para o benchmark.

Gerado com Piper TTS, voz `pt_BR-faber-medium`, modelo de `rhasspy/piper-voices`. A voz não é incluída no ZIP para reduzir tamanho; os áudios prontos estão incluídos.

Para reproduzir, instale `piper-tts` e baixe os arquivos do modelo para `model/`:

```bash
python -m pip install piper-tts
mkdir -p model
curl -L -o model/pt_BR-faber-medium.onnx https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/faber/medium/pt_BR-faber-medium.onnx
curl -L -o model/pt_BR-faber-medium.onnx.json https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/faber/medium/pt_BR-faber-medium.onnx.json
python generate_dataset.py
```

O script exige `piper-tts` e `ffmpeg`, preserva arquivos existentes por padrão e gera OGG/Opus com `libopus` para reproduzir o formato recebido do Telegram. Use `--force` para substituir. O benchmark nunca chama TTS.
