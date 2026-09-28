#!/usr/bin/env python3
"""Gera o dataset de áudio a partir do manifesto JSON, sem sobrescrever por padrão."""
import argparse
import hashlib
import json
import statistics
import wave
from pathlib import Path

from piper.voice import PiperVoice

ROOT = Path(__file__).resolve().parent
MODEL = ROOT / "model" / "pt_BR-faber-medium.onnx"
MANIFEST = ROOT / "audio-benchmark-dataset.json"


def generate(force=False):
    cases = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assert len(cases) == 150 and len({c["id"] for c in cases}) == 150
    if not MODEL.exists():
        raise SystemExit("Modelo ausente. Consulte README.md para baixá-lo.")
    voice = PiperVoice.load(MODEL)
    for index, case in enumerate(cases, 1):
        path = ROOT / "audio" / case["file"]
        path.parent.mkdir(parents=True, exist_ok=True)
        if force or not path.exists():
            with wave.open(str(path), "wb") as wav:
                voice.synthesize_wav(case["originalText"], wav)
        print(f"{index:03d}/150 {case['id']}")
    validate(cases)


def validate(cases):
    expected = {ROOT / "audio" / c["file"] for c in cases}
    found = set((ROOT / "audio").rglob("*.wav"))
    assert found == expected, f"Faltando: {expected-found}; extras: {found-expected}"
    report = []
    for case in cases:
        path = ROOT / "audio" / case["file"]
        assert path.stat().st_size > 44
        with wave.open(str(path), "rb") as wav:
            assert wav.getnframes() > 0 and wav.getframerate() > 0
            duration = wav.getnframes() / wav.getframerate()
            sample_rate = wav.getframerate()
        report.append({"id": case["id"], "file": case["file"],
                       "durationSeconds": round(duration, 3),
                       "sampleRateHz": sample_rate,
                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    durations = [c["durationSeconds"] for c in report]
    summary = {"count": len(report), "minSeconds": min(durations),
               "maxSeconds": max(durations), "meanSeconds": round(statistics.mean(durations), 3),
               "totalSeconds": round(sum(durations), 3), "files": report}
    (ROOT / "generation-report.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Validação 150/150:", {k: v for k, v in summary.items() if k != "files"})


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="Regenerar áudios existentes")
    args = parser.parse_args()
    generate(args.force)
