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
- `java/` – JVM binding under refactor.  Aligns with the same fixtures but currently
  lacks automated wiring—consult `CROSS_LANGUAGE_ALIGNMENT.md` before touching.
- `js/` – ESM binding published to npm (`dybuf`).  Uses Node’s built-in test runner
  with fixture-based suites; manual publish steps live in `js/README.md`.
- `tools/` – repo-wide scripts.  `generate_fixtures.sh` builds and verifies golden data.

Additional docs:

- `README.md` – high-level roadmap + CI strategy.
- `CROSS_LANGUAGE_ALIGNMENT.md` – parity requirements, open checklist per language.
- Language-specific READMEs – runtime requirements, development commands, release
  notes.

## Working conventions

1. **Treat C as the source of truth.** Any protocol change must land in `c/` first,
   regenerate fixtures, then port outward.
2. **Keep fixtures fresh.** Before running Python/JavaScript tests or publishing,
   execute `tools/generate_fixtures.sh` from the repo root so `fixtures/v1/*.json`
   match the latest C behavior.  JS tests call `testutils/ensure-fixtures.js` and fail
   fast when data is missing.
3. **Cross-language validation.** Every binding test suite should read the fixtures,
   decode payloads, and re-encode to the same hex.  When adding features, update the
   fixture generator plus verifier to cover new cases.
4. **Testing.**
   - C: build targets via CMake or the helper scripts in `c/fixtures`.
   - Python: `pip install -e . && pytest` inside `py/`.
   - JavaScript: `npm test` inside `js/` (Node 18+).
   - Java: follow `java/README` once the fixture wiring lands; currently keep manual
     parity in mind.
5. **Releases.** Python uses its existing GitHub Actions PyPI workflow.  JavaScript
   publishes from `js/` via `npm publish --access public` (see README for steps);
   roadmap item tracks automating npm releases via Actions.

## When in doubt

- Need protocol details? Open `c/dybuf.h` (authoritative API) and the fixture sources
  under `c/fixtures/`.
- Unsure about feature status? Check the “Operational Checklist” near the end of
  `CROSS_LANGUAGE_ALIGNMENT.md`.
- For CI or docs, keep changes under `/tools` or per-language README files so other
  bindings stay in sync.

Use this guide as the quick reference for orienting new automation or contributors
before diving into language-specific directories.
