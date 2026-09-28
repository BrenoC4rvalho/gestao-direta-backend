# Benchmark de áudio do Gestão Direta

Contém 150 arquivos WAV em pt-BR, manifesto, inventário e relatório de validação.

Gerado com Piper TTS, voz `pt_BR-faber-medium`, modelo de `rhasspy/piper-voices`. A voz não é incluída no ZIP para reduzir tamanho; os áudios prontos estão incluídos.

Para reproduzir, instale `piper-tts` e baixe os arquivos do modelo para `model/`:

```bash
python -m pip install piper-tts
mkdir -p model
curl -L -o model/pt_BR-faber-medium.onnx https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/faber/medium/pt_BR-faber-medium.onnx
curl -L -o model/pt_BR-faber-medium.onnx.json https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/faber/medium/pt_BR-faber-medium.onnx.json
python generate_dataset.py
```

O script preserva arquivos existentes por padrão. Use `--force` para substituir. O formato WAV mestre foi escolhido para permitir decodificação direta e conversão posterior para OGG/Opus caso a aplicação exija o formato recebido pelo Telegram.
