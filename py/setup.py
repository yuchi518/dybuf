from __future__ import annotations

from pathlib import Path

from Cython.Build import cythonize
from setuptools import Extension, setup

ROOT = Path(__file__).parent.resolve()
INCLUDE_DIR = ROOT / "include" / "dybuf"

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
