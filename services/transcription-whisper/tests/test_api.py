import asyncio
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.config import Settings
from app.main import create_app
from app.transcription_service import (
    AudioValidationError,
    TranscriptionError,
    WhisperTranscriptionService,
)


class FakeModel:
    def __init__(self, text: str = "Gastei mil reais com fertilizante") -> None:
        self.text = text
        self.paths: list[str] = []

    def transcribe(self, path: str, **_: object):
        self.paths.append(path)
        return [SimpleNamespace(text=self.text, end=4.8)], SimpleNamespace(language="pt")


class FakeUpload:
    def __init__(self, content: bytes, content_type: str) -> None:
        self._content = content
        self.content_type = content_type

    async def read(self, size: int) -> bytes:
        chunk = self._content[:size]
        self._content = self._content[size:]
        return chunk


def upload(content: bytes, content_type: str = "audio/ogg") -> FakeUpload:
    return FakeUpload(content, content_type)


def service_with(model: FakeModel | None = None) -> tuple[WhisperTranscriptionService, FakeModel]:
    fake = model or FakeModel()
    service = WhisperTranscriptionService(Settings(), lambda *_, **__: fake)
    service.load_model()
    return service, fake


def test_routes_expose_health_and_transcribe() -> None:
    app = create_app(Settings(), lambda *_, **__: FakeModel())

    assert {route.path for route in app.routes} >= {"/health", "/transcribe"}


def test_health_state_reports_loaded_model() -> None:
    service, _ = service_with()

    assert service.ready is True
    assert service.model_name == "small"


def test_transcribe_returns_text_language_duration_and_removes_temporary_file() -> None:
    service, model = service_with()

    result = asyncio.run(service.transcribe(upload(b"ogg-bytes")))

    assert result.text == "Gastei mil reais com fertilizante"
    assert result.language == "pt"
    assert result.durationSeconds == 4.8
    assert model.paths
    assert not Path(model.paths[0]).exists()


def test_transcribe_rejects_invalid_audio_type() -> None:
    service, _ = service_with()

    with pytest.raises(AudioValidationError):
        asyncio.run(service.transcribe(upload(b"audio", "audio/mpeg")))


def test_transcribe_rejects_empty_result_and_model_load_failure() -> None:
    service, _ = service_with(FakeModel(""))

    with pytest.raises(TranscriptionError):
        asyncio.run(service.transcribe(upload(b"ogg-bytes")))

    failing = WhisperTranscriptionService(
        Settings(), lambda *_, **__: (_ for _ in ()).throw(RuntimeError("model unavailable"))
    )
    failing.load_model()

    assert failing.ready is False
