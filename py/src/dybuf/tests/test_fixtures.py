from __future__ import annotations

import pytest

from dybuf import DyBuf, append_var_cstring, next_var_cstring

from .fixtures import fixtures_available, hex_to_bytes, load_fixture

_REQUIRED = [
    "varint_unsigned",
    "varint_signed",
    "typdex",
    "varlen_bytes",
    "varlen_strings",
]

if not fixtures_available(_REQUIRED):  # pragma: no cover
    pytest.skip("C fixtures are missing; run tools/generate_fixtures.sh", allow_module_level=True)


def _writer(capacity: int) -> DyBuf:
    return DyBuf(capacity=max(capacity, 16))


@pytest.mark.parametrize("case", load_fixture("varint_unsigned"), ids=lambda case: case["id"])
def test_varint_unsigned_fixture(case):
    encoded = hex_to_bytes(case["encoded_hex"])
    buf = DyBuf(data=encoded)
    value = buf.next_var_u64()
    assert value == int(case["value_dec"])
    assert buf.remaining() == 0

    writer = _writer(len(encoded) + 8)
    writer.append_var_u64(int(case["value_dec"]))
    actual = bytes(writer.to_bytes()[: writer.limit]).hex()
    assert actual == case["encoded_hex"]


@pytest.mark.parametrize("case", load_fixture("varint_signed"), ids=lambda case: case["id"])
def test_varint_signed_fixture(case):
    encoded = hex_to_bytes(case["encoded_hex"])
    buf = DyBuf(data=encoded)
    value = buf.next_var_s64()
    assert value == int(case["value_dec"])
    assert buf.remaining() == 0

    writer = _writer(len(encoded) + 8)
    writer.append_var_s64(int(case["value_dec"]))
    actual = bytes(writer.to_bytes()[: writer.limit]).hex()
    assert actual == case["encoded_hex"]


@pytest.mark.parametrize("case", load_fixture("typdex"), ids=lambda case: case["id"])
def test_typdex_fixture(case):
    encoded = hex_to_bytes(case["encoded_hex"])
    buf = DyBuf(data=encoded)
    typ, index = buf.next_typdex()
    assert typ == case["type"]
    assert index == case["index"]
    assert buf.remaining() == 0

    writer = _writer(len(encoded) + 4)
    writer.append_typdex(case["type"], case["index"])
    actual = bytes(writer.to_bytes()[: writer.limit]).hex()
    assert actual == case["encoded_hex"]


@pytest.mark.parametrize("case", load_fixture("varlen_bytes"), ids=lambda case: case["id"])
def test_varlen_bytes_fixture(case):
    encoded = hex_to_bytes(case["encoded_hex"])
    buf = DyBuf(data=encoded)
    payload = buf.next_var_bytes()
    assert buf.remaining() == 0
    expected = hex_to_bytes(case["payload_hex"])
    assert payload == expected

    writer = _writer(len(encoded) + 16)
    writer.append_var_bytes(expected)
    actual = bytes(writer.to_bytes()[: writer.limit]).hex()
    assert actual == case["encoded_hex"]


@pytest.mark.parametrize("case", load_fixture("varlen_strings"), ids=lambda case: case["id"])
def test_varlen_strings_fixture(case):
    encoded = hex_to_bytes(case["encoded_hex"])
    buf = DyBuf(data=encoded)
    decoded = next_var_cstring(buf)
    assert buf.remaining() == 0
    assert decoded == case["utf8"]

    writer = _writer(len(encoded) + 16)
    append_var_cstring(writer, case["utf8"])
    actual = bytes(writer.to_bytes()[: writer.limit]).hex()
    assert actual == case["encoded_hex"]
