# Agent Guide

This repository hosts `dybuf`/`dypkt`, a dynamic binary buffer plus packet helper
implemented in C and ported to Python, Java, and JavaScript.  The C sources are the
canonical spec, and every other binding must pass the shared “golden” fixtures
generated from the C implementation.

## Directory orientation

- `c/` – reference implementation, fixture generators (`fixtures/` subdir) and
  headers (`dybuf.h`, `dypkt.h`).  Run `cmake` or directly compile helpers as needed.
- `fixtures/` – versioned JSON bundles emitted by the C generator.  Any binding that
  runs tests must ensure `fixtures/v1/*.json` exist (see `tools/generate_fixtures.sh`).
- `py/` – Cython wrapper packaged for PyPI.  Uses pytest + fixtures; see `py/README.md`
  for build instructions and release workflow.
- `java/` – JVM binding under package `oyo.lol.util`.  Uses the shared fixtures through
  `tools/test_java_fixtures.sh`; see `java/README.md` for current build/test notes.
- `js/` – ESM binding published to npm (`dybuf`).  Uses Node’s built-in test runner
  with fixture-based suites; manual publish steps live in `js/README.md`.
- `tools/` – repo-wide scripts.  `generate_fixtures.sh` builds and verifies golden data.

Additional docs:

- `README.md` – high-level roadmap + CI strategy.
- `DYPKT_SCHEMA_CONVENTION.md` – typdex layout, reserved dypkt function IDs, package
  header, and versioned schema compatibility rules.
- `CROSS_LANGUAGE_ALIGNMENT.md` – parity requirements, open checklist per language.
- Language-specific READMEs – runtime requirements, development commands, release
  notes.

## Working conventions

1. **Treat C as the source of truth.** Any protocol change must land in `c/` first,
   regenerate fixtures, then port outward.  The Python vendored headers under
   `py/include/dybuf/` mirror `c/dybuf.h` and `c/platform/*.h`; `py/setup.py` syncs
   them during repo-local builds, and they must not be edited independently.
2. **Keep fixtures fresh.** Before running Python, Java, or JavaScript tests or publishing,
   execute `tools/generate_fixtures.sh` from the repo root so `fixtures/v1/*.json`
   match the latest C behavior.  JS tests call `testutils/ensure-fixtures.js`; Java
   tests read the same directory via `tools/test_java_fixtures.sh`.
3. **Cross-language validation.** Every binding test suite should read the fixtures,
   decode payloads, and re-encode to the same hex.  When adding features, update the
   fixture generator plus verifier to cover new cases.
4. **Testing.**
   - C: build targets via CMake or the helper scripts in `c/fixtures`.
   - Python: `pip install -e . && pytest` inside `py/`.
   - JavaScript: `npm test` inside `js/` (Node 18+).
   - Java: `tools/test_java_fixtures.sh` from the repo root.
5. **Releases.** Python uses its existing GitHub Actions PyPI workflow.  JavaScript
   publishes from `js/` via the scripts in `js/package.json`; Java publishing is not
   wired yet and should wait for Gradle or Maven metadata.

## When in doubt

- Need protocol details? Open `c/dybuf.h` (authoritative API) and the fixture sources
  under `c/fixtures/`.
- Unsure about feature status? Check the “Operational Checklist” near the end of
  `CROSS_LANGUAGE_ALIGNMENT.md`.
- For CI or docs, keep changes under `/tools` or per-language README files so other
  bindings stay in sync.

Use this guide as the quick reference for orienting new automation or contributors
before diving into language-specific directories.
