from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str
    model: str


class TranscriptionResponse(BaseModel):
    text: str
    language: str | None
    durationSeconds: float | None
