# dybuf python package

Python bindings for the [`dybuf`](../c/dybuf.h) dynamic buffer library implemented in C.  The package exposes the `DyBuf` class implemented in Cython and ships binary wheels across the major desktop platforms.

## Key features

- Thin, fast wrapper around the original `dybuf` implementation.
- Supports reading and writing unsigned integers (8–64 bits), booleans, raw byte payloads, and the library’s compact `var_u64`/`var_s64` varints.
- Exposes typdex markers so higher-level protocols can tag fields without hauling in a full protobuf-style schema.
- Compatible with Windows, Linux, and macOS thanks to compiled extension modules.
- Designed for publishing on PyPI with automated release workflows.

## Installation

Once released to PyPI the package can be installed with:

```bash
pip install dybuf
```

## Quick start

```python
from dybuf import DyBuf

buf = (
    DyBuf(capacity=64)
    .append_uint16(0x1234)
    .append_uint32(0xDEADBEEF)
    .append_bool(True)
)

buf.flip()  # prepare for reading
print(hex(buf.next_uint16()))  # 0x1234
print(hex(buf.next_uint32()))  # 0xdeadbeef
print(buf.next_bool())         # True
```

`write()` and `read()` let you work directly with arbitrary byte payloads, while `position`, `limit`, and `capacity` expose the cursor-style API provided by the original library.

## Variable-length integers

`DyBuf` supports zig-zag encoded signed integers and unsigned integers using the same compact varint scheme the C library exposes. These helpers keep the payload small for numbers that fit in a few bits.

```python
from dybuf import DyBuf

buf = (
    DyBuf(capacity=32)
    .append_var_s64(-123)
    .append_var_u64(300)
)

buf.flip()

print(buf.next_var_s64())  # -123
print(buf.next_var_u64())  # 300
```

Use `append_var_s64` / `next_var_s64` when you need negative values; the encoding automatically performs zig-zag conversion under the hood. For values that are always non-negative, stick to `append_var_u64` / `next_var_u64` to avoid the extra zig-zag step. The legacy `append_var_int` / `next_var_int` and `append_var_uint` / `next_var_uint` aliases still work but emit `DeprecationWarning` so callers can migrate. The same encoding also powers `append_var_bytes` and `append_var_string`, which prefix their payload with a varint length field for round-tripping arbitrary byte sequences.

## Typdex markers

`DyBuf` also exposes helpers for working with the library's **typdex** encodings—a compact representation of a logical type paired with an index. These markers are commonly used by higher-level protocols such as `dypkt` to describe field layouts or function identifiers.

```python
from dybuf import DyBuf, TYPDEX_TYP_INT, TYPDEX_TYP_STRING

buf = DyBuf(capacity=32)
buf.append_typdex(TYPDEX_TYP_INT, 3).append_typdex(TYPDEX_TYP_STRING, 0x42)

buf.flip()

assert buf.peek_typdex() == (TYPDEX_TYP_INT, 3)
assert buf.next_typdex() == (TYPDEX_TYP_INT, 3)
assert buf.next_typdex() == (TYPDEX_TYP_STRING, 0x42)
```

The constants `TYPDEX_TYP_*` mirror the underlying C enums. `append_typdex` validates that the type fits in 8 bits and the index can be represented by the packed encoding. `next_typdex` and `peek_typdex` raise `EOFError` for truncated markers and `ValueError` when encountering a malformed header.

## Developing locally

Create a virtual environment and install the build requirements:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt  # optional, see below
```

Build the extension in editable mode for local testing:

```bash
pip install -e .
pytest
```

The project is configured to build wheels via `python -m build`, producing both source and binary distributions:

```bash
python -m build
```

Generate the Sphinx documentation locally with:

```bash
pip install -r requirements-dev.txt  # ensures sphinx/docutils are present
sphinx-build -b html docs docs/_build/html
open docs/_build/html/index.html  # or use your preferred viewer
```

## Automated releases

A GitHub Actions workflow under `.github/workflows/pypi-release.yml` drives [cibuildwheel](https://github.com/pypa/cibuildwheel) to produce Windows, Linux, and macOS artifacts and publish them to PyPI.  Provide a `PYPI_API_TOKEN` secret in your repository and tag releases with a semantic version (e.g. `v0.3.0`) to trigger the pipeline.

## Licensing

The wrapper is distributed under the GNU GPL v2, matching the original dybuf project.
