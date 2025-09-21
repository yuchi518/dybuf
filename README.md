# dybuf / dypkt

**dybuf** is a dynamic buffer library that makes binary memory manipulation easy. It supports fixed-width integers, booleans, raw bytes, and compact variable-length encodings so you can focus on protocol logic instead of manual pointer math.

**dypkt** builds upon dybuf to provide a higher-level packet abstraction for structured data handling.

## Projects in this repository

- **C** implementation (stable): [`/c`](c)
- **Python** bindings (published as `dybuf` on PyPI, latest release `0.1.0` and evolving): [`/py`](py)
- **Java** bindings (complete): [`/java`](java)
- **JavaScript** bindings: coming soon
- **Objective-C** bindings: planned

## Roadmap

- Publish the initial JavaScript bindings
- Continue expanding the Python feature set and releases
- Finish Objective-C bindings
- Binary JSON converter

## Cross-language automation TODO

### Pipeline strategy
- Build a unified CI workflow that orchestrates C → Python → Java → JavaScript (and future bindings) so compatibility checks run in a predictable order.
- Fail fast if the canonical C job breaks, preventing downstream bindings from running with stale artifacts.

### Golden artifacts & shared contracts
- Treat the C implementation as the authority by emitting versioned golden payloads (buffers, typdex sequences, protocol transcripts) stored under a documented schema.
- Require every non-C suite to ingest the golden bundle during tests to guarantee round-trip parity across languages.
- Provide a scripted `tools/update_golden` command so maintainers regenerate fixtures consistently and review diffs in code review.

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
