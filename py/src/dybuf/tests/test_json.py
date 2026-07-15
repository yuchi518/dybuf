from __future__ import annotations

import random

import pytest

from dybuf import decode_json, encode_json

from .fixtures import fixtures_available, hex_to_bytes, load_fixture

_REQUIRED = ["json_values"]

if not fixtures_available(_REQUIRED):  # pragma: no cover
    pytest.skip("JSON fixtures are missing", allow_module_level=True)


@pytest.mark.parametrize("case", load_fixture("json_values"), ids=lambda case: case["id"])
def test_json_fixture_decode_and_encode(case):
    encoded = hex_to_bytes(case["encoded_hex"])
    assert decode_json(encoded) == case["value"]
    assert encode_json(case["value"]).hex() == case["encoded_hex"]


def test_json_random_round_trip():
    rng = random.Random(20260711)
    for _ in range(100):
        value = _random_json_value(rng, depth=0)
        assert decode_json(encode_json(value)) == value


def test_json_rejects_unsupported_values():
    with pytest.raises(ValueError):
        encode_json(2**53)
    with pytest.raises(ValueError):
        decode_json(bytes.fromhex("7001007138000000000000f87f"))
    with pytest.raises(TypeError):
        encode_json({"bad": object()})
    with pytest.raises(TypeError):
        encode_json({1: "not a JSON key"})


def _random_json_value(rng: random.Random, depth: int):
    if depth >= 4:
        return _random_scalar(rng)

    choice = rng.randrange(8)
    if choice <= 4:
        return _random_scalar(rng)
    if choice == 5:
        return [_random_json_value(rng, depth + 1) for _ in range(rng.randrange(4))]

    result = {}
    key_count = rng.randrange(4)
    for key_index in range(key_count):
        result[f"k{depth}_{key_index}"] = _random_json_value(rng, depth + 1)
    return result


def _random_scalar(rng: random.Random):
    choice = rng.randrange(7)
    if choice == 0:
        return None
    if choice == 1:
        return bool(rng.randrange(2))
    if choice == 2:
        return rng.randint(-1000, -1)
    if choice == 3:
        return rng.randint(0, 1000)
    if choice == 4:
        return rng.choice([0.25, -1.5, 3.5, 100.125])
    return rng.choice(["", "dybuf", "json", "map-data"])
