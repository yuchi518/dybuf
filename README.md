# dybuf / dypkt

**dybuf** is a dynamic buffer library that makes binary memory manipulation easy. It supports fixed-width integers, booleans, raw bytes, and compact variable-length encodings so you can focus on protocol logic instead of manual pointer math.

Its signature helpers—`var_u64`/`var_s64` varints and **typdex** markers—let you pack indices and payload lengths efficiently without adopting a full protobuf-style schema. The result is a lightweight, self-contained binary format that still trims message size for the common cases you care about.

**dypkt** builds upon dybuf to provide a higher-level packet abstraction for structured data handling.

## Projects in this repository

- **C** implementation (stable; source of golden fixtures): [`/c`](c)
- **Python** bindings (PyPI package + golden fixture parity): [`/py`](py/README.md)
- **Java** binding (package `oyo.lol.util`, golden fixtures + JSON helpers): [`/java`](java/README.md)
- **JavaScript** binding (ESM npm package + golden fixtures): [`/js`](js/README.md)

## Schema convention

`typdex` is the compact `type + index` marker used by `dypkt` and app-level schemas.
See [`DYPKT_SCHEMA_CONVENTION.md`](DYPKT_SCHEMA_CONVENTION.md) for the canonical
typdex layout, reserved dypkt function indices, package header convention, and
compatibility rules for designing versioned schemas.

## Version notes

- `0.5.0` is a wire-format compatibility release. Fixed-width `float` and `double`
  payloads now use big-endian byte order, matching the existing fixed-width integer
  helpers. Older C builds on little-endian hosts wrote `float`/`double` payloads in
  little-endian order through `dyb_append_float` / `dyb_append_double`, so data written
  by those helpers before `0.5.0` may need migration before reading with newer
  bindings. Fixed-width integer payloads are unchanged.

## Roadmap

- Add Gradle or Maven wiring for Java builds, tests, and eventual Maven Central publishing.
- Add CI jobs that run C fixture generation plus Python, Java, and JavaScript regression suites.
- Add composite packet fixtures for mixed record/cursor workflows.
- Automate npm package publishing for the JavaScript binding via GitHub Actions.

## Cross-language automation TODO

### Pipeline strategy
- Build a unified CI workflow that orchestrates C → Python → Java → JavaScript (and future bindings) so compatibility checks run in a predictable order.
- Fail fast if the canonical C job breaks, preventing downstream bindings from running with stale artifacts.

### Golden artifacts & shared contracts
- Treat the C implementation as the authority by emitting versioned golden payloads (buffers, typdex sequences, protocol transcripts) stored under a documented schema.
- Require every non-C suite to ingest the golden bundle during tests to guarantee round-trip parity across languages.
- Use `tools/generate_fixtures.sh` as the canonical fixture refresh command so maintainers regenerate fixtures consistently and review diffs in code review.
- When running language-specific tests, ensure `fixtures/v1/*.json` has been regenerated via `tools/generate_fixtures.sh` so every suite exercises the same data.

### Shared tooling & documentation
- Consolidate helper scripts, environment bootstrapping, and doc builds in a `/tools` directory that all languages reuse.
- Publish a single documentation portal (via GitHub Pages or similar) with per-language sections living under dedicated subdirectories.

### CI workflow outline
1. Prep job: install shared dependencies, lint metadata, and fan out environment caches.
2. C job: build/tests, produce golden artifacts, upload for downstream jobs.
3. Binding jobs: for each language download artifacts, run tests against the golden data, and perform cross-language read/write checks.
4. Docs job: aggregate language docs, build the static site, and deploy.
5. Release gate: only tag/publish when every job succeeds against the latest golden contracts.

## License

GPLv2
