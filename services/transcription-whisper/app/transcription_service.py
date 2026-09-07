from __future__ import annotations

import asyncio
import tempfile
from collections.abc import Callable
from pathlib import Path

from fastapi import UploadFile
from faster_whisper import WhisperModel

from app.config import Settings
from app.schemas import TranscriptionResponse


class TranscriptionError(Exception):
    pass


class AudioValidationError(TranscriptionError):
    pass


class WhisperTranscriptionService:
    def __init__(
        self,
        settings: Settings,
        model_loader: Callable[..., WhisperModel] = WhisperModel,
    ) -> None:
        self._settings = settings
        self._model_loader = model_loader
        self._model: WhisperModel | None = None
        self._load_error: Exception | None = None
        self._semaphore = asyncio.Semaphore(settings.max_concurrency)

    def load_model(self) -> None:
        try:
            self._model = self._model_loader(
                self._settings.model,
                device=self._settings.device,
                compute_type=self._settings.compute_type,
            )
            self._load_error = None
        except Exception as exception:  # noqa: BLE001
            self._model = None
            self._load_error = exception

    @property
    def ready(self) -> bool:
        return self._model is not None

    @property
    def model_name(self) -> str:
        return self._settings.model

    async def transcribe(self, upload: UploadFile) -> TranscriptionResponse:
        self._validate_upload(upload)
        temporary_path = await self._write_temporary_file(upload)
        try:
            async with self._semaphore:
                return self._transcribe_file(temporary_path)
        finally:
            temporary_path.unlink(missing_ok=True)

    def _validate_upload(self, upload: UploadFile) -> None:
        if upload.content_type not in {"audio/ogg", "audio/opus", "application/ogg"}:
            raise AudioValidationError("Only OGG/Opus audio is supported")
        if not self.ready:
            raise TranscriptionError("Whisper model is unavailable")

    async def _write_temporary_file(self, upload: UploadFile) -> Path:
        suffix = ".opus" if upload.content_type == "audio/opus" else ".ogg"
        limit = self._settings.max_upload_size_mb * 1024 * 1024
        total = 0
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temporary_file:
            while chunk := await upload.read(1024 * 1024):
                total += len(chunk)
                if total > limit:
                    temporary_file.close()
                    Path(temporary_file.name).unlink(missing_ok=True)
                    raise AudioValidationError("Audio file exceeds the configured size limit")
                temporary_file.write(chunk)
            if total == 0:
                Path(temporary_file.name).unlink(missing_ok=True)
                raise AudioValidationError("Audio file is empty")
            return Path(temporary_file.name)

    def _transcribe_file(self, path: Path) -> TranscriptionResponse:
        if self._model is None:
            raise TranscriptionError("Whisper model is unavailable")
        try:
            segments, info = self._model.transcribe(
                str(path), language=self._settings.language, beam_size=5
            )
            segment_list = list(segments)
        except Exception as exception:
            raise TranscriptionError("Whisper could not transcribe the audio") from exception

        text = " ".join(segment.text.strip() for segment in segment_list if segment.text.strip()).strip()
        if not text:
            raise TranscriptionError("Whisper returned an empty transcript")
        duration = max((segment.end for segment in segment_list), default=None)
        return TranscriptionResponse(
            text=text,
            language=getattr(info, "language", None),
            durationSeconds=duration,
        )
