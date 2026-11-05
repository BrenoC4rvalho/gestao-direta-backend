#!/usr/bin/env python3
"""Validate the manifest, ground truth and real OGG/Opus fixtures."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    try:
        import av
    except ImportError as exception:
        raise SystemExit("Install PyAV with: python -m pip install av") from exception

    cases = json.loads((ROOT / "cases.json").read_text(encoding="utf-8"))
    ground_truth = json.loads((ROOT / "ground-truth.json").read_text(encoding="utf-8"))
    expected_ids = [f"AUDIO_{index:03d}" for index in range(1, 51)]
    if [case["id"] for case in cases] != expected_ids:
        raise SystemExit("Manifest IDs are not exactly AUDIO_001..AUDIO_050")
    if [item["id"] for item in ground_truth] != expected_ids:
        raise SystemExit("Ground truth IDs do not match the manifest")
    for case in cases:
        path = ROOT / "audio" / case["audioFile"]
        if not path.is_file():
            raise SystemExit(f"Missing audio: {path}")
        container = av.open(str(path))
        streams = [stream for stream in container.streams if stream.type == "audio"]
        if len(streams) != 1 or streams[0].codec_context.name != "opus":
            raise SystemExit(f"Invalid OGG/Opus stream: {path}")
        if not any(frame.to_ndarray().any() for frame in container.decode(streams[0])):
            raise SystemExit(f"Silent audio: {path}")
        container.close()
    print("Manifest, ground truth and 50 decodable audio files are valid")


if __name__ == "__main__":
    main()
