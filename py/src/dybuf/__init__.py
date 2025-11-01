from ._core import (
    DyBuf,
    TYPDEX_TYP_NONE,
    TYPDEX_TYP_BOOL,
    TYPDEX_TYP_INT,
    TYPDEX_TYP_UINT,
    TYPDEX_TYP_FLOAT,
    TYPDEX_TYP_DOUBLE,
    TYPDEX_TYP_STRING,
    TYPDEX_TYP_BYTES,
    TYPDEX_TYP_ARRAY,
    TYPDEX_TYP_MAP,
    TYPDEX_TYP_F,
)

__all__ = [
    "DyBuf",
    "append_var_cstring",
    "next_var_cstring",
    "TYPDEX_TYP_NONE",
    "TYPDEX_TYP_BOOL",
    "TYPDEX_TYP_INT",
    "TYPDEX_TYP_UINT",
    "TYPDEX_TYP_FLOAT",
    "TYPDEX_TYP_DOUBLE",
    "TYPDEX_TYP_STRING",
    "TYPDEX_TYP_BYTES",
    "TYPDEX_TYP_ARRAY",
    "TYPDEX_TYP_MAP",
    "TYPDEX_TYP_F",
]

__version__ = "0.4.0"


def append_var_cstring(buf: DyBuf, text: str, *, encoding: str = "utf-8") -> DyBuf:
    data = text.encode(encoding) + b"\x00"
    buf.append_var_bytes(data)
    return buf


def next_var_cstring(buf: DyBuf, *, encoding: str = "utf-8") -> str:
    data = buf.next_var_bytes()
    if data.endswith(b"\x00"):
        data = data[:-1]
    return data.decode(encoding)
