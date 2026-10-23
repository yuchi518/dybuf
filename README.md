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

## Cross-language verification

The required golden-data path is implemented:

- `tools/generate_fixtures.sh` generates versioned fixtures from C and runs the C verifier.
- Python, Java, and JavaScript load the same fixtures, verify decoded values, and compare
  deterministic re-encoded bytes.
- `tools/test_java_fixtures.sh`, the Python pytest suite, and `npm test` provide explicit local
  verification commands.

A unified four-language CI gate is not currently required for product operation. Gradle or Maven
wiring, composite packet/cursor fixtures, automatic npm publication, and consolidated hosted
documentation remain conditional improvements. Add them when the wire format changes materially,
Java gains a production consumer, release frequency makes manual publication costly, or an actual
parity failure shows the local golden suites are insufficient.

## License

GPLv2
