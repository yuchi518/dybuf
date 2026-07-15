from __future__ import annotations

import math
import struct
from collections import OrderedDict
from typing import Any

from ._core import (
    DyBuf,
    TYPDEX_TYP_ARRAY,
    TYPDEX_TYP_BOOL,
    TYPDEX_TYP_DOUBLE,
    TYPDEX_TYP_INT,
    TYPDEX_TYP_MAP,
    TYPDEX_TYP_NONE,
    TYPDEX_TYP_OBJ,
    TYPDEX_TYP_STRING,
    TYPDEX_TYP_UINT,
)

JSON_DYBUF_FORMAT_VERSION = 1
_MAX_SAFE_INTEGER = (1 << 53) - 1
_MIN_SAFE_INTEGER = -_MAX_SAFE_INTEGER


def encode_json(value: Any) -> bytes:
    buf = DyBuf(capacity=256)
    append_json_value(buf, value)
    return bytes(buf.to_bytes()[: buf.limit])


def decode_json(data: Any) -> Any:
    buf = DyBuf(data=data)
    value = next_json_value(buf)
    if buf.remaining() != 0:
        raise ValueError("trailing bytes after JSON-dybuf payload")
    return value


def append_json_value(buf: DyBuf, value: Any) -> DyBuf:
    dictionaries = _build_dictionaries(value)

    buf.append_typdex(TYPDEX_TYP_OBJ, 0)
    buf.append_var_u64(JSON_DYBUF_FORMAT_VERSION)
    buf.append_var_u64(len(dictionaries))
    for path, key_to_index in dictionaries.items():
        buf.append_var_string(path)
        buf.append_var_u64(len(key_to_index))
        for key in key_to_index.keys():
            buf.append_var_string(key)

    buf.append_typdex(TYPDEX_TYP_OBJ, 1)
    _append_json_record(buf, value, "$", 0, dictionaries)
    return buf


def next_json_value(buf: DyBuf) -> Any:
    typ, index = buf.next_typdex()
    if (typ, index) != (TYPDEX_TYP_OBJ, 0):
        raise ValueError("JSON-dybuf stream must start with TYPDEX_TYP_OBJ, 0")

    version = buf.next_var_u64()
    if version != JSON_DYBUF_FORMAT_VERSION:
        raise ValueError(f"unsupported JSON-dybuf format version: {version}")

    dictionary_count = _read_count(buf, "dictionary_count")
    dictionaries: dict[str, list[str]] = {}
    for _ in range(dictionary_count):
        path = buf.next_var_string()
        if path in dictionaries:
            raise ValueError(f"duplicate JSON dictionary path: {path}")
        key_count = _read_count(buf, f"key_count for {path}")
        keys: list[str] = []
        seen: set[str] = set()
        for _ in range(key_count):
            key = buf.next_var_string()
            if key in seen:
                raise ValueError(f"duplicate key in JSON dictionary {path}: {key}")
            seen.add(key)
            keys.append(key)
        dictionaries[path] = keys

    typ, index = buf.next_typdex()
    if (typ, index) != (TYPDEX_TYP_OBJ, 1):
        raise ValueError("JSON-dybuf payload marker must be TYPDEX_TYP_OBJ, 1")

    typ, index = buf.next_typdex()
    return _next_json_payload(buf, typ, index, "$", dictionaries)


def _build_dictionaries(value: Any) -> OrderedDict[str, OrderedDict[str, int]]:
    dictionaries: OrderedDict[str, OrderedDict[str, int]] = OrderedDict()

    def ensure(path: str) -> OrderedDict[str, int]:
        dictionary = dictionaries.get(path)
        if dictionary is None:
            dictionary = OrderedDict()
            dictionaries[path] = dictionary
        return dictionary

    def visit(current: Any, path: str) -> None:
        if isinstance(current, dict):
            dictionary = ensure(path)
            for key, child in current.items():
                if not isinstance(key, str):
                    raise TypeError("JSON object keys must be strings")
                if key not in dictionary:
                    dictionary[key] = len(dictionary)
                visit(child, f"{path}.{dictionary[key]}")
        elif isinstance(current, list):
            for child in current:
                visit(child, f"{path}.[]")
        else:
            _json_typdex_type(current)

    visit(value, "$")
    return dictionaries


def _append_json_record(
    buf: DyBuf,
    value: Any,
    path: str,
    index: int,
    dictionaries: OrderedDict[str, OrderedDict[str, int]],
) -> None:
    typ = _json_typdex_type(value)
    buf.append_typdex(typ, index)

    if typ == TYPDEX_TYP_NONE:
        return
    if typ == TYPDEX_TYP_BOOL:
        buf.append_bool(value)
        return
    if typ == TYPDEX_TYP_INT:
        buf.append_var_s64(value)
        return
    if typ == TYPDEX_TYP_UINT:
        buf.append_var_u64(value)
        return
    if typ == TYPDEX_TYP_DOUBLE:
        buf.write(struct.pack(">d", float(value)))
        return
    if typ == TYPDEX_TYP_STRING:
        buf.append_var_string(value)
        return
    if typ == TYPDEX_TYP_ARRAY:
        buf.append_var_u64(len(value))
        child_path = f"{path}.[]"
        for child in value:
            _append_json_record(buf, child, child_path, 0, dictionaries)
        return
    if typ == TYPDEX_TYP_MAP:
        dictionary = dictionaries[path]
        buf.append_var_u64(len(value))
        for key, child in value.items():
            key_index = dictionary[key]
            _append_json_record(buf, child, f"{path}.{key_index}", key_index, dictionaries)
        return
    raise AssertionError(f"unhandled JSON typdex type: {typ}")


def _next_json_payload(
    buf: DyBuf,
    typ: int,
    index: int,
    path: str,
    dictionaries: dict[str, list[str]],
) -> Any:
    if typ == TYPDEX_TYP_NONE:
        return None
    if typ == TYPDEX_TYP_BOOL:
        return buf.next_bool()
    if typ == TYPDEX_TYP_INT:
        return _safe_number(buf.next_var_s64(), "signed integer")
    if typ == TYPDEX_TYP_UINT:
        return _safe_number(buf.next_var_u64(), "unsigned integer")
    if typ == TYPDEX_TYP_DOUBLE:
        value = struct.unpack(">d", buf.read(8))[0]
        if not math.isfinite(value):
            raise ValueError("decoded JSON double must be finite")
        return value
    if typ == TYPDEX_TYP_STRING:
        return buf.next_var_string()
    if typ == TYPDEX_TYP_ARRAY:
        count = _read_count(buf, "array item_count")
        child_path = f"{path}.[]"
        return [
            _next_json_payload(buf, *buf.next_typdex(), child_path, dictionaries)
            for _ in range(count)
        ]
    if typ == TYPDEX_TYP_MAP:
        keys = dictionaries.get(path)
        if keys is None:
            raise ValueError(f"missing JSON dictionary for path: {path}")
        present_count = _read_count(buf, f"present_count for {path}")
        result: dict[str, Any] = {}
        seen_indices: set[int] = set()
        for _ in range(present_count):
            child_type, key_index = buf.next_typdex()
            if key_index in seen_indices:
                raise ValueError(f"duplicate key index {key_index} in JSON object {path}")
            if key_index >= len(keys):
                raise ValueError(f"key index {key_index} out of range for JSON object {path}")
            seen_indices.add(key_index)
            result[keys[key_index]] = _next_json_payload(
                buf,
                child_type,
                key_index,
                f"{path}.{key_index}",
                dictionaries,
            )
        return result
    raise ValueError(f"unsupported JSON typdex type: {typ}")


def _json_typdex_type(value: Any) -> int:
    if value is None:
        return TYPDEX_TYP_NONE
    if isinstance(value, bool):
        return TYPDEX_TYP_BOOL
    if isinstance(value, int):
        if value < _MIN_SAFE_INTEGER or value > _MAX_SAFE_INTEGER:
            raise ValueError("JSON integer is outside JavaScript safe integer range")
        return TYPDEX_TYP_INT if value < 0 else TYPDEX_TYP_UINT
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("JSON number must be finite")
        return TYPDEX_TYP_DOUBLE
    if isinstance(value, str):
        return TYPDEX_TYP_STRING
    if isinstance(value, list):
        return TYPDEX_TYP_ARRAY
    if isinstance(value, dict):
        return TYPDEX_TYP_MAP
    raise TypeError(f"unsupported JSON value type: {type(value).__name__}")


def _read_count(buf: DyBuf, label: str) -> int:
    value = buf.next_var_u64()
    if value > _MAX_SAFE_INTEGER:
        raise ValueError(f"{label} is too large")
    return int(value)


def _safe_number(value: int, label: str) -> int:
    if value < _MIN_SAFE_INTEGER or value > _MAX_SAFE_INTEGER:
        raise ValueError(f"decoded JSON {label} is outside JavaScript safe integer range")
    return int(value)


__all__ = [
    "JSON_DYBUF_FORMAT_VERSION",
    "append_json_value",
    "decode_json",
    "encode_json",
    "next_json_value",
]
