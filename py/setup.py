from __future__ import annotations

import shutil
from pathlib import Path

from Cython.Build import cythonize
from setuptools import Extension, setup

ROOT = Path(__file__).parent.resolve()
INCLUDE_DIR = ROOT / "include" / "dybuf"
REPO_ROOT = ROOT.parent


def sync_vendored_headers() -> None:
    """Keep Python's packaged headers aligned with the canonical C headers."""
    header_pairs = [
        (REPO_ROOT / "c" / "dybuf.h", INCLUDE_DIR / "dybuf.h"),
        (REPO_ROOT / "c" / "platform" / "plat_mem.h", INCLUDE_DIR / "plat_mem.h"),
        (REPO_ROOT / "c" / "platform" / "plat_string.h", INCLUDE_DIR / "plat_string.h"),
        (REPO_ROOT / "c" / "platform" / "plat_type.h", INCLUDE_DIR / "plat_type.h"),
    ]
    for source, target in header_pairs:
        if source.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)


sync_vendored_headers()

extensions = [
    Extension(
        name="dybuf._core",
        sources=["src/dybuf/_core.pyx"],
        include_dirs=[str(INCLUDE_DIR)],
        language="c",
    )
]

setup(
    ext_modules=cythonize(
        extensions,
        compiler_directives={"language_level": 3},
    ),
)
