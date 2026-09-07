from __future__ import annotations

from collections.abc import Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, HTTPException, UploadFile
from faster_whisper import WhisperModel
from starlette.responses import JSONResponse

from app.config import Settings
from app.schemas import HealthResponse, TranscriptionResponse
from app.transcription_service import (
    AudioValidationError,
    TranscriptionError,
    WhisperTranscriptionService,
)


def create_app(
    settings: Settings | None = None,
    model_loader: Callable[..., WhisperModel] = WhisperModel,
) -> FastAPI:
    service = WhisperTranscriptionService(settings or Settings.from_environment(), model_loader)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        service.load_model()
        yield

    app = FastAPI(title="Gestao Direta Whisper Transcription", lifespan=lifespan)
    app.state.transcription_service = service

    @app.get("/health", response_model=HealthResponse)
    async def health() -> HealthResponse | JSONResponse:
        if not service.ready:
            return JSONResponse(
                status_code=503,
                content=HealthResponse(status="DOWN", model=service.model_name).model_dump(),
            )
        return HealthResponse(status="UP", model=service.model_name)

    @app.post("/transcribe", response_model=TranscriptionResponse)
    async def transcribe(file: UploadFile = File(...)) -> TranscriptionResponse:  # noqa: B008
        try:
            return await service.transcribe(file)
        except AudioValidationError as exception:
            raise HTTPException(status_code=422, detail=str(exception)) from exception
        except TranscriptionError as exception:
            raise HTTPException(status_code=503, detail="Audio transcription is unavailable") from exception

    return app


app = create_app()
