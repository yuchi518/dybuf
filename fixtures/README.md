# dybuf Fixtures

Generated test vectors that guarantee every language binding stays byte-for-byte
compatible with the canonical C implementation. Each versioned directory contains
JSON manifests that hold:

- `encoded_hex`: lowercase hex string representing the exact byte sequence emitted by
  the C generator.
- `decoded`: typed fields describing the semantic meaning (e.g., unsigned integer
  value, typdex `{ "type": 3, "index": 42 }`, payload text).

Consumers should take the following approach:

1. Parse the JSON file for the fixture category they are testing.
2. Decode `encoded_hex` into raw bytes and feed them into the language binding.
3. Assert the decoded values match the `decoded` metadata.
4. Re-encode using the binding under test and compare the bytes to `encoded_hex`
   (round-trip check).

Fixtures currently cover:

- Unsigned and signed varints around every encoding boundary.
- Typdex headers across all width tiers.
- Variable-length payload helpers, including empty payloads.

The fixture generator lives in `c/` and will materialise outputs into
`fixtures/v1/` by default.

## Regenerating & Verifying

```sh
# from repo root
tools/generate_fixtures.sh                # writes fixtures/v1/*.json and validates them
tools/generate_fixtures.sh /tmp/dybuf-fixtures   # optional custom path + validation
```

The script compiles the C generator and verifier on the fly, emits the JSON bundle,
and runs `dybuf_verify_fixtures` to ensure the outputs round-trip before returning.

For detailed per-case output (useful when debugging failures) run the verifier
directly in verbose mode:

```sh
c/build/dybuf_verify_fixtures -v fixtures/v1
```
