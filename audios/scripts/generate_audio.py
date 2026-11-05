#!/usr/bin/env python3
"""Generate the 50 reproducible Brazilian Portuguese benchmark audios."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
import wave
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CASES = ROOT / "cases.json"
AUDIO_DIR = ROOT / "audio"
MODEL = ROOT / "model" / "pt_BR-faber-medium.onnx"
VOICE = "pt_BR-faber-medium"


def load_cases() -> list[dict[str, str]]:
    cases = json.loads(CASES.read_text(encoding="utf-8"))
    ids = [case["id"] for case in cases]
    expected = [f"AUDIO_{index:03d}" for index in range(1, 51)]
    if ids != expected:
        raise SystemExit("cases.json must contain AUDIO_001 through AUDIO_050 in order")
    return cases


def require_tools(require_model: bool) -> None:
    if require_model and shutil.which("ffmpeg") is None:
        try:
            import imageio_ffmpeg
        except ImportError as exception:
            raise SystemExit("Missing ffmpeg (or the Python imageio-ffmpeg package)") from exception
    if require_model and not MODEL.is_file():
        raise SystemExit(
            f"Missing Piper model {MODEL}. See audios/README.md for the download command."
        )


def generate(case: dict[str, str], force: bool, voice) -> None:
    destination = AUDIO_DIR / case["audioFile"]
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and not force:
        return

    with tempfile.TemporaryDirectory(prefix="gestao-direta-audio-") as directory:
        wav_path = Path(directory) / f"{case['id']}.wav"
        with wave.open(str(wav_path), "wb") as output:
            voice.synthesize_wav(case["text"], output)
        ffmpeg = shutil.which("ffmpeg")
        if ffmpeg is None:
            import imageio_ffmpeg

            ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
        subprocess.run(
            [
                ffmpeg,
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                str(wav_path),
                "-vn",
                "-ac",
                "1",
                "-ar",
                "16000",
                "-c:a",
                "libopus",
                "-application",
                "voip",
                str(destination),
            ],
            check=True,
        )


def validate(cases: list[dict[str, str]]) -> dict:
    try:
        import av
    except ImportError as exception:
        raise SystemExit("Missing PyAV; install it with: python -m pip install av") from exception

    expected = {AUDIO_DIR / case["audioFile"] for case in cases}
    found = set(AUDIO_DIR.glob("AUDIO_*.ogg"))
    if found != expected:
        raise SystemExit(f"Audio set mismatch; missing={expected - found}, extra={found - expected}")

    files = []
    for case in cases:
        path = AUDIO_DIR / case["audioFile"]
        container = av.open(str(path))
        streams = [stream for stream in container.streams if stream.type == "audio"]
        if len(streams) != 1 or streams[0].codec_context.name != "opus":
            raise SystemExit(f"{path} is not a single Opus audio stream")
        stream = streams[0]
        duration = float(stream.duration * stream.time_base) if stream.duration else 0
        size = path.stat().st_size
        has_signal = False
        for frame in container.decode(stream):
            if frame.to_ndarray().any():
                has_signal = True
                break
        container.close()
        if not has_signal:
            raise SystemExit(f"{path} contains no audible signal")
        if duration <= 0 or duration > 60 or size <= 0 or size > 10 * 1024 * 1024:
            raise SystemExit(f"{path} violates duration/size limits")
        files.append(
            {
                "id": case["id"],
                "audioFile": case["audioFile"],
                "durationSeconds": round(duration, 3),
                "sizeBytes": size,
                "codec": stream.codec_context.name,
                "sampleRateHz": int(stream.sample_rate),
                "channels": int(stream.codec_context.channels),
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            }
        )
    report = {"count": len(files), "format": "ogg", "codec": "opus", "files": files}
    (ROOT / "generation-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--id", action="append", dest="ids")
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    cases = load_cases()
    require_tools(not args.validate_only)
    if not args.validate_only:
        from piper.voice import PiperVoice

        voice = PiperVoice.load(str(MODEL))
        selected = set(args.ids or [case["id"] for case in cases])
        for case in cases:
            if case["id"] in selected:
                generate(case, args.force, voice)
    report = validate(cases)
    print(f"Validated {report['count']}/50 OGG/Opus files")


if __name__ == "__main__":
    main()
