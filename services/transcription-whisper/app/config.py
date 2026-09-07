from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    model: str = "small"
    device: str = "cpu"
    compute_type: str = "int8"
    language: str | None = "pt"
    max_upload_size_mb: int = 10
    max_concurrency: int = 1

    @classmethod
    def from_environment(cls) -> Settings:
        language = os.getenv("WHISPER_LANGUAGE", "pt").strip()
        return cls(
            model=os.getenv("WHISPER_MODEL", "small"),
            device=os.getenv("WHISPER_DEVICE", "cpu"),
            compute_type=os.getenv("WHISPER_COMPUTE_TYPE", "int8"),
            language=None if language.lower() in {"", "auto"} else language,
            max_upload_size_mb=int(os.getenv("WHISPER_MAX_UPLOAD_SIZE_MB", "10")),
            max_concurrency=int(os.getenv("WHISPER_MAX_CONCURRENCY", "1")),
        )
