# JavaScript Binding (`dybuf-js`)

This directory contains the JavaScript port of the `dybuf` dynamic buffer. It mirrors
the core semantics of the C and Python implementations—fixed-width integers, compact
varints, and typdex helpers—but runs entirely on native JavaScript primitives.

## Runtime requirements

- **BigInt support** (stage 4 as of 2018) and `DataView#getBigUint64` /
  `setBigUint64`. We intentionally do **not** ship a fallback shim; environments must
  be equivalent to Chrome 67+, Node.js 10.4+, Safari 14+, or Edge Chromium.
- ES modules. The implementation exports `DyBuf` with `export default`.

If either BigInt or the 64-bit DataView APIs are missing, module evaluation throws
immediately so callers fail fast during startup.

## Feature parity

- Variable-length readers behave like the C/Python bindings: a length of 0 yields an empty `ArrayBuffer`/string, whereas `null` still signals EOF or protocol errors.

- Read/write unsigned integers from 1 to 8 bytes (`getULong(length)` /
  `putULong(length)`) using native `BigInt` arithmetic.
- `putTypdex` / `getTypdex` match the canonical bit layout, and legacy helpers remain as aliases.
- `getVarULong` / `putVarULong` and `getVarLong` / `putVarLong` follow the same
  varint/zig-zag encoding as the C library.
- `getCStringWithVarLength` trims, and `putCStringWithVarLength` appends, the trailing
  null terminator so C fixtures round-trip faithfully. Legacy `getStringWithVarLength` /
  `putStringWithVarLength` remain as aliases for now.
- Buffer cursor semantics (`position`, `limit`, `flip`, `rewind`, `compact`) are
  aligned with the canonical implementation.
- Bulk payload helpers (`getBytesWithVarLength`, `putBytesWithVarLength`, etc.) work
  with `ArrayBuffer` instances.
- The Node-based regression suite covers typdex layouts, varint/zig-zag boundaries,
  and zero-length payload semantics.

## Known gaps vs. C/Python

- `putLastBytes` currently expects an `ArrayBuffer`. Accepting `TypedArray` views is a
  potential follow-up improvement.

## Next steps

- Generate golden fixtures from the C implementation and plug them into the Node test
  suite for cross-language parity checks.
- Expand byte helpers to accept `TypedArray` views directly (mirroring the C/Python
  ergonomics).
- Wire the package into CI so `npm test` runs automatically alongside the other
  bindings.

## Usage

```js
import DyBuf from './DyBuf.js';

const buf = new DyBuf(32);
buf.putUInt(0xDEADBEEF)
   .putVarULong(300n)
   .flip();

console.log(buf.getUInt().toString(16)); // deadbeef
console.log(buf.getVarULong());          // 300n
```

## Development notes

- Run `npm test` (Node 18+ recommended) inside this directory for the regression
  suite.
- Regenerate golden data with `tools/generate_fixtures.sh` (it also runs the C verifier)
  and import the JSON cases under `fixtures/v1/` for cross-language checks.
- The module has no build step; `node -e "import('./DyBuf.js')"` still
  works for quick manual pokes.
- When adding features, ensure they remain compatible with the canonical C fixtures
  once the cross-language regression suite is in place.
