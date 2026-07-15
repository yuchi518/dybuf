# JavaScript Binding (`dybuf-js`)

This directory contains the JavaScript port of the `dybuf` dynamic buffer. It mirrors
the core semantics of the C and Python implementations—fixed-width integers, compact
varints, and typdex helpers—but runs entirely on native JavaScript primitives.

## Runtime requirements

- **BigInt support** (stage 4 as of 2018) and `DataView#getBigUint64` /
  `setBigUint64`. We intentionally do **not** ship a fallback shim; environments must
  be equivalent to Chrome 67+, Node.js 10.4+, Safari 14+, or Edge Chromium.
- ES modules. The implementation exports `DyBuf` and helper functions as named exports.

If either BigInt or the 64-bit DataView APIs are missing, module evaluation throws
immediately so callers fail fast during startup.

## Feature parity

- Variable-length readers behave like the C/Python bindings: a length of 0 yields an empty `ArrayBuffer`/string, whereas `null` still signals EOF or protocol errors.

- Read/write unsigned integers from 1 to 8 bytes (`getULong(length)` /
  `putULong(length)`) using native `BigInt` arithmetic.
- `putTypdex` / `getTypdex` match the canonical bit layout, and legacy helpers remain as aliases.
- Named exports `TYPDEX_TYP_*` mirror the canonical typdex enum so callers don't hard-code magic numbers. The same constants are exposed as static fields on `DyBuf`.
- `TYPDEX_TYP_OBJ` (`0x0e`) marks protocol-defined objects. Its index and payload are protocol-defined; `DyBuf` only reads and writes the typdex marker.
- Named exports `DYPE_F_*` mirror the reserved dypkt function indices (`EOF`, schema version, protocol name, protocol version).
- `getVarULong` / `putVarULong` and `getVarLong` / `putVarLong` follow the same
  varint/zig-zag encoding as the C library.
- `getVarString` / `putVarString` encode UTF-8 payloads without a trailing terminator for
  parity with Python bindings, while `getCStringWithVarLength` / `putCStringWithVarLength`
  preserve the C-style `\0` suffix when needed.
- `encodeJson` / `decodeJson` provide JSON-equivalent dybuf round-tripping using the
  document-level dictionary convention.
- `getCStringWithVarLength` trims, and `putCStringWithVarLength` appends, the trailing
  null terminator so C fixtures round-trip faithfully. Legacy `getStringWithVarLength` /
  `putStringWithVarLength` remain as aliases for now.
- Buffer cursor semantics (`position`, `limit`, `flip`, `rewind`, `compact`) are
  aligned with the canonical implementation.
- Bulk payload helpers (`getBytesWithVarLength`, `putBytesWithVarLength`, etc.) work
  with `ArrayBuffer` instances.
- The Node-based regression suite covers typdex layouts, varint/zig-zag boundaries,
  zero-length payload semantics, shared golden fixtures, and JSON-dybuf cases.

## Known gaps vs. C/Python

- `putLastBytes` currently expects an `ArrayBuffer`. Accepting `TypedArray` views is a
  potential follow-up improvement.

## Schema convention

When using `typdex` to design an app-level schema, treat one `Typdex(type, index)`
plus its payload as a **record**. Keep common record IDs in `0..7` for 1-byte typdex
markers, and start dypkt-compatible messages with `TYPDEX_TYP_F` +
`DYPE_F_VERSION`.

See the repository's [dypkt schema convention](https://github.com/yuchi518/dybuf/blob/master/DYPKT_SCHEMA_CONVENTION.md)
for the canonical typdex layout, reserved function IDs, and compatibility rules.

## Next steps

- Expand byte helpers to accept `TypedArray` views directly (mirroring the C/Python
  ergonomics).
- Wire the package into CI so `npm test` runs automatically alongside the other
  bindings.

## Usage

```js
import { DyBuf } from './DyBuf.js';

const buf = new DyBuf(32);
buf.putUInt(0xDEADBEEF)
   .putVarULong(300n)
   .flip();

console.log(buf.getUInt().toString(16)); // deadbeef
console.log(buf.getVarULong());          // 300n
```

JSON-compatible values can be encoded as a complete JSON-dybuf byte stream:

```js
import { decodeJson, encodeJson } from './DyBuf.js';

const payload = { items: [{ id: 1, name: 'alpha' }, { id: 2, name: 'beta' }] };
const encoded = encodeJson(payload);

console.log(decodeJson(encoded)); // { items: [...] }
```

The JSON helper targets round-trip equivalence, not canonical bytes. Integers must fit
JavaScript's safe integer range, fractional numbers are encoded as doubles, and
`NaN`/`Infinity` are rejected because they are not JSON values.

## Version notes

- `0.5.0` is a wire-format compatibility release. Fixed-width floating point payloads
  now follow the same big-endian order as fixed-width integers. Data written by older
  little-endian C builds through the float/double helpers may need migration before
  reading with `0.5.0`.

## Development notes

- Run `npm test` (Node 18+ recommended) inside this directory for the regression
  suite.
- Regenerate golden data with `tools/generate_fixtures.sh` (it also runs the C verifier)
  and keep the JSON cases under `fixtures/v1/` so the Node tests pick up the shared fixtures.
- The module has no build step; `node -e "import('./DyBuf.js')"` still
  works for quick manual pokes.
- When adding features, ensure they remain compatible with the canonical C fixtures
  consumed by the cross-language regression suite.

## Publishing to npm

Manual releases use the plain source files in this directory:

1. Bump the `version` in `package.json` to the desired release.
2. From the repo root, regenerate fixtures if needed: `tools/generate_fixtures.sh`.
3. Back in `js/`, run `npm test` (the prepublish hook reruns this and fails if fixtures are missing).
4. Authorize the npm CLI if needed: `npm run auth:web` opens the browser-based login flow.
5. Publish the module: `npm run publish:public`.

Trusted Publishing via GitHub Actions can replace local login later, but these commands work on macOS/Linux today.
