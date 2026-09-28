#!/usr/bin/env python3
"""Gera o dataset de áudio a partir do manifesto JSON, sem sobrescrever por padrão."""
import argparse
import hashlib
import json
import statistics
import subprocess
import wave
from pathlib import Path

from piper.voice import PiperVoice

ROOT = Path(__file__).resolve().parent
MODEL = ROOT / "model" / "pt_BR-faber-medium.onnx"
MANIFEST = ROOT / "audio-benchmark-dataset.json"


def generate(force=False):
    cases = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assert len(cases) == 150 and len({c["id"] for c in cases}) == 150
    evolve_manifest(cases)
    if not MODEL.exists():
        raise SystemExit("Modelo ausente. Consulte README.md para baixá-lo.")
    voice = PiperVoice.load(MODEL)
    for index, case in enumerate(cases, 1):
        wav_path = ROOT / "audio" / case["file"]
        ogg_path = wav_path.with_suffix(".ogg")
        wav_path.parent.mkdir(parents=True, exist_ok=True)
        if force or not wav_path.exists():
            with wave.open(str(wav_path), "wb") as wav:
                voice.synthesize_wav(case["originalText"], wav)
        if force or not ogg_path.exists():
            subprocess.run([
                "ffmpeg", "-y", "-i", str(wav_path), "-c:a", "libopus",
                "-b:a", "32k", str(ogg_path)
            ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(f"{index:03d}/150 {case['id']}")
    validate(cases)


def evolve_manifest(cases):
    """Keep old manifests readable while upgrading them to the benchmark contract."""
    changed = False
    for case in cases:
        wav_file = case["file"]
        defaults = {
            "audioFile": str(Path(wav_file).with_suffix(".ogg")),
            "audioVariant": "CLEAN",
            "voiceId": "pt_BR-faber-medium",
            "missingFields": [] if case["expectedOutcome"] == "VALID" else ["amount", "description"],
        }
        for key, value in defaults.items():
            if key not in case:
                case[key] = value
                changed = True
    if changed:
        MANIFEST.write_text(
            json.dumps(cases, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )


def validate(cases):
    expected_wav = {ROOT / "audio" / c["file"] for c in cases}
    expected_ogg = {path.with_suffix(".ogg") for path in expected_wav}
    found_wav = set((ROOT / "audio").rglob("*.wav"))
    found_ogg = set((ROOT / "audio").rglob("*.ogg"))
    assert found_wav == expected_wav, f"WAV faltando: {expected_wav-found_wav}; extras: {found_wav-expected_wav}"
    assert found_ogg == expected_ogg, f"OGG faltando: {expected_ogg-found_ogg}; extras: {found_ogg-expected_ogg}"
    report = []
    for case in cases:
        wav_path = ROOT / "audio" / case["file"]
        ogg_path = wav_path.with_suffix(".ogg")
        assert wav_path.stat().st_size > 44 and ogg_path.stat().st_size > 0
        with wave.open(str(wav_path), "rb") as wav:
            assert wav.getnframes() > 0 and wav.getframerate() > 0
            duration = wav.getnframes() / wav.getframerate()
            sample_rate = wav.getframerate()
        report.append({"id": case["id"], "file": case["file"], "audioFile": str(ogg_path.relative_to(ROOT / "audio")),
                       "durationSeconds": round(duration, 3),
                       "sampleRateHz": sample_rate,
                       "wavSha256": hashlib.sha256(wav_path.read_bytes()).hexdigest(),
                       "oggSha256": hashlib.sha256(ogg_path.read_bytes()).hexdigest()})
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
