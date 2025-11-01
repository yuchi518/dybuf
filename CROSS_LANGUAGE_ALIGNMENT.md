# Cross-language Alignment Plan

This document tracks the work needed to keep the C, Python, Java, and JavaScript
bindings functionally identical. The C implementation is the canonical source of
truth—every other binding should round-trip buffers produced by the C library
without translation or lossy conversions.

## Goals

- Preserve the `dybuf` binary format (fixed-width integers, varints, typdex markers,
  var-length payloads) across all bindings.
- Guarantee empty payloads and cursor semantics (`position`/`limit`/`compact`)
  behave exactly the same in every language.
- Ship reusable fixtures so regressions are caught by CI before release.

## Authoritative Reference

- Treat `c/dybuf.h` as the specification.
- Python’s Cython wrapper already calls into that header and acts as the baseline
  high-level API. Future changes to `dybuf` should land in C first, then be ported
  outward.

## Language Workstreams

### Java

- **Typdex encoding:** switch to the canonical bit layout (type stays 6/8 bits after
  the one-byte form, index widths match 8/13/20 bits) to remain wire-compatible with
  C/Cython (`dyb_append_typdex` / `dyb_next_typdex`).
- **Capacity shrink:** copy only `min(old_capacity, new_capacity)` when resizing to
  avoid copying past the end of a smaller buffer.
- **Zero-length handling:** return empty arrays/strings instead of `null` from
  `getBytesWithFixedLength` and related helpers so var-length readers can distinguish
  empty from missing.
- **Tests:** add JUnit cases that load canonical fixtures and verify helpers
  (varints, typdex, read/write cursor ops) match the decoded values from C.

### JavaScript

- **Typdex parity:** mirror the C bit layout for encoder/decoder and add validation to
  throw on malformed headers.
- **Buffer semantics:** keep capacity unchanged in `compact()`, and copy only the data
  that fits when growing/shrinking to avoid DataView bounds errors.
- **Byte copies:** wrap `ArrayBuffer` inputs with `Uint8Array` before calling `set` so
  payloads aren’t zeroed.
- **Zero-length payloads:** return empty `ArrayBuffer`/`Uint8Array` instances from
  readers; update string helpers to return `""` for empty payloads.
- **Tests:** port the Python smoke tests using Node (e.g., Jest or Vitest) and include
  fixture-based round trips.

## Shared Tasks

- Document the canonical typdex format (include tables for header markers, type bits,
  and index width) and keep it in the repo for easy reference.
- Add helper scripts under `tools/` (or similar) to regenerate fixtures:
  1. Build the C library/executable.
  2. Emit JSON or binary blobs for representative values:
     - fixed-width integers of each size;
     - varints covering every encoding bucket;
     - typdex markers across the supported ranges;
     - zero-length strings/bytes.
  3. Store outputs under `fixtures/` with versioned filenames.
- Provide a verifier (`dybuf_verify_fixtures`) so every fixture set round-trips through
  the canonical C implementation before other languages consume it.
- Extend Python’s test suite to compare runtime results with the fixture files.
- Import the same fixtures in Java/JS tests and assert byte-for-byte equality.
- Add composite “packet” fixtures that chain multiple encoded elements to test cursor
  behaviour and mixed read/write flows.

## CI Integration

- Update GitHub Actions so the C job produces the fixture artifacts as build outputs.
- Downstream language jobs download the artifacts, run their respective tests, and fail
  fast when decoding mismatches occur.
- Gate releases on every language job passing against the latest fixtures.

## Operational Checklist

- [ ] Implement Java fixes (typdex, capacity, zero-length, tests).
- [ ] Implement JavaScript fixes (typdex, capacity/compact, byte copying, zero-length).
- [x] Land fixture generator tooling and share sample fixtures.
- [x] Wire fixtures into the JavaScript regression suite.
- [x] Wire fixtures into the Python regression suite.
- [ ] Wire fixtures into the Java regression suite.
- [ ] Update CI pipelines to enforce the cross-language checks.
