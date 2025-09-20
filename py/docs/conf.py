import os
import sys
from datetime import datetime

# Add project root to sys.path so Sphinx can import dybuf if needed.
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

project = "dybuf"
author = "Yuchi"
copyright = f"{datetime.now():%Y}, {author}"

extensions = [
    "sphinx.ext.autodoc",
    "sphinx.ext.napoleon",
]

templates_path = ["_templates"]
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]

html_theme = "alabaster"
