from __future__ import annotations

import json
from pathlib import Path
from typing import Iterable, List, Mapping

_FIXTURE_ROOT = Path(__file__).resolve().parents[4] / "fixtures" / "v1"


def load_fixture(name: str) -> List[Mapping[str, str]]:
    path = _FIXTURE_ROOT / f"{name}.json"
    if not path.exists():
        raise FileNotFoundError(f"Fixture file not found: {path}")
    with path.open("r", encoding="utf-8") as fp:
        data = json.load(fp)
    return data.get("cases", [])


def hex_to_bytes(hex_str: str) -> bytes:
    return bytes.fromhex(hex_str) if hex_str else b""


def fixtures_available(names: Iterable[str]) -> bool:
    return all((_FIXTURE_ROOT / f"{name}.json").exists() for name in names)


__all__ = [
    "load_fixture",
    "hex_to_bytes",
    "fixtures_available",
]
