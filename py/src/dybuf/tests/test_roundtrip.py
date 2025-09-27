import random

import pytest

from dybuf import (
    DyBuf,
    TYPDEX_TYP_INT,
    TYPDEX_TYP_STRING,
    TYPDEX_TYP_UINT,
)


def test_roundtrip_operations():
    buf = DyBuf(capacity=32)
    buf.append_uint16(0xABCD)
    buf.append_uint32(0x12345678)
    buf.append_bool(True)
    buf.write(b"abc")

    buf.flip()

    assert buf.remaining() == 2 + 4 + 1 + len(b"abc")
    assert buf.next_uint16() == 0xABCD
    assert buf.next_uint32() == 0x12345678
    assert buf.next_bool() is True
    assert buf.read(3) == b"abc"
    assert buf.remaining() == 0


def test_write_read_all():
    payload = b"hello world"
    buf = DyBuf(data=payload)
    assert buf.limit == len(payload)
    assert buf.read() == payload
    assert buf.remaining() == 0


def test_copy_mode_does_not_mutate_source():
    source = bytearray(b"abcd")
    buf = DyBuf(data=source)

    source[0] = ord("z")

    assert buf.read(2) == b"ab"
    assert buf.remaining() == 2


def test_reference_mode_writable_updates_source():
    source = bytearray(b"\x00" * 8)
    buf = DyBuf(data=source, copy=False, writable=True)

    buf.write(b"hello")
    assert bytes(source[:5]) == b"hello"
    assert buf.position == 5
    assert buf.limit == 5

    buf.flip()
    assert buf.read() == b"hello"


def test_mark_and_reset_restore_position():
    buf = DyBuf(capacity=16)
    buf.append_uint16(0x1111)
    buf.append_uint16(0x2222)
    buf.flip()

    buf.mark()
    assert buf.next_uint16() == 0x1111
    buf.reset()
    assert buf.next_uint16() == 0x1111


def test_ensure_capacity_allows_larger_writes():
    buf = DyBuf(capacity=2)
    buf.ensure_capacity(10)
    buf.write(b"abcdefghij")

    assert buf.capacity >= 10
    assert buf.limit == 10
    assert buf.position == 10
    assert buf.to_bytes().startswith(b"abcdefghij")


def test_read_past_limit_raises_eof():
    buf = DyBuf(capacity=4)
    with pytest.raises(EOFError):
        buf.read(1)


def test_read_negative_size_raises_value_error():
    buf = DyBuf(data=b"abc")
    with pytest.raises(ValueError):
        buf.read(-1)


def test_to_bytes_returns_written_data():
    buf = DyBuf(capacity=8)
    buf.write(b"xy")

    assert buf.to_bytes()[:2] == b"xy"
    assert buf.limit == 2


def test_random_var_uint_roundtrip_with_seed():
    seed = 20250219
    rng = random.Random(seed)
    values = [rng.getrandbits(64) for _ in range(10_000)]

    buf = DyBuf(capacity=128)
    for value in values:
        buf.append_var_u64(value)

    buf.append_var_u64(0)
    buf.append_var_u64((1 << 64) - 1)

    buf.flip()

    for expected in values:
        assert buf.next_var_u64() == expected

    assert buf.next_var_u64() == 0
    assert buf.next_var_u64() == (1 << 64) - 1

    assert buf.remaining() == 0


def test_random_var_int_roundtrip_with_seed():
    seed = 42
    rng = random.Random(seed)
    values = [rng.randint(-(1 << 63) + 1, (1 << 63) - 1) for _ in range(10_000)]

    buf = DyBuf(capacity=128)
    for value in values:
        buf.append_var_s64(value)

    buf.append_var_s64(-(1 << 63) + 1)
    buf.append_var_s64((1 << 63) - 1)

    buf.flip()

    for expected in values:
        assert buf.next_var_s64() == expected

    assert buf.next_var_s64() == -(1 << 63) + 1
    assert buf.next_var_s64() == (1 << 63) - 1

    assert buf.remaining() == 0


def test_var_uint_alias_emits_deprecation_warning():
    buf = DyBuf(capacity=16)
    with pytest.deprecated_call():
        buf.append_var_uint(123)

    buf.flip()

    with pytest.deprecated_call():
        assert buf.next_var_uint() == 123


def test_var_int_alias_emits_deprecation_warning():
    buf = DyBuf(capacity=16)
    with pytest.deprecated_call():
        buf.append_var_int(-42)

    buf.flip()

    with pytest.deprecated_call():
        assert buf.next_var_int() == -42


def test_var_bytes_roundtrip():
    payloads = [
        b"",
        b"abc",
        bytes(range(10)),
        bytes(range(256)),
    ]

    buf = DyBuf(capacity=32)
    for payload in payloads:
        buf.append_var_bytes(payload)

    buf.flip()

    for expected in payloads:
        assert buf.next_var_bytes() == expected

    assert buf.remaining() == 0


def test_var_string_roundtrip_utf8():
    strings = ["", "hello", "你好", "🙂"]

    buf = DyBuf(capacity=32)
    for s in strings:
        buf.append_var_string(s)

    buf.flip()

    for expected in strings:
        assert buf.next_var_string() == expected

    assert buf.remaining() == 0


def test_var_string_custom_encoding_roundtrip():
    strings = ["αβ", "測試"]

    buf = DyBuf(capacity=32)
    for s in strings:
        buf.append_var_string(s, encoding="utf-16-le")

    buf.flip()

    for expected in strings:
        assert buf.next_var_string(encoding="utf-16-le") == expected

    assert buf.remaining() == 0


def test_typdex_roundtrip_four_encodings():
    entries = [
        (TYPDEX_TYP_INT, 3),
        (TYPDEX_TYP_STRING, 0x42),
        (0x50, 0x1234),
        (0x80, 0x20000),
    ]

    buf = DyBuf(capacity=32)
    for entry in entries:
        buf.append_typdex(*entry)

    buf.flip()

    for expected in entries:
        assert buf.peek_typdex() == expected
        assert buf.next_typdex() == expected

    assert buf.remaining() == 0


def test_typdex_append_rejects_invalid_values():
    buf = DyBuf(capacity=8)

    with pytest.raises(ValueError):
        buf.append_typdex(0x1FF, 0)

    with pytest.raises(ValueError):
        buf.append_typdex(TYPDEX_TYP_UINT, 0x200000)


def test_typdex_invalid_header_raises_value_error():
    buf = DyBuf(capacity=4)
    buf.write(b"\xff")
    buf.flip()

    with pytest.raises(ValueError):
        buf.next_typdex()


def test_typdex_incomplete_payload_raises_eof():
    buf = DyBuf(capacity=4)
    buf.write(bytes([0x80]))
    buf.flip()

    with pytest.raises(EOFError):
        buf.next_typdex()


def test_append_methods_support_chaining():
    buf = (
        DyBuf(capacity=16)
        .append_uint8(0xAA)
        .append_uint16(0xBEEF)
        .append_var_u64(42)
        .append_var_string("hi")
        .append_typdex(TYPDEX_TYP_UINT, 7)
    )

    buf.flip()

    assert buf.next_uint8() == 0xAA
    assert buf.next_uint16() == 0xBEEF
    assert buf.next_var_u64() == 42
    assert buf.next_var_string() == "hi"
    assert buf.next_typdex() == (TYPDEX_TYP_UINT, 7)
