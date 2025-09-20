# dybuf python package

Python bindings for the [`dybuf`](../c/dybuf.h) dynamic buffer library implemented in C.  The package exposes the `DyBuf` class implemented in Cython and ships binary wheels across the major desktop platforms.

## Key features

- Thin, fast wrapper around the original `dybuf` implementation.
- Supports reading and writing unsigned integers (8–64 bits), booleans, and raw byte payloads.
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

buf = DyBuf(capacity=64)
buf.append_uint16(0x1234)
buf.append_uint32(0xDEADBEEF)
buf.append_bool(True)

buf.flip()  # prepare for reading
print(hex(buf.next_uint16()))  # 0x1234
print(hex(buf.next_uint32()))  # 0xdeadbeef
print(buf.next_bool())         # True
```

`write()` and `read()` let you work directly with arbitrary byte payloads, while `position`, `limit`, and `capacity` expose the cursor-style API provided by the original library.

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

A GitHub Actions workflow under `.github/workflows/pypi-release.yml` drives [cibuildwheel](https://github.com/pypa/cibuildwheel) to produce Windows, Linux, and macOS artifacts and publish them to PyPI.  Provide a `PYPI_API_TOKEN` secret in your repository and tag releases with a semantic version (e.g. `v0.1.0`) to trigger the pipeline.

## Licensing

The wrapper is distributed under the GNU GPL v2, matching the original dybuf project.
