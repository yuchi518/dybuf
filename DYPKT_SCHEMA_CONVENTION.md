# dypkt Schema Convention

`typdex` is the compact wire tag used by `dybuf`/`dypkt`. It is not a full schema
language by itself. The actual schema is a versioned code contract: readers inspect
`Typdex(type, index)` and then dispatch to the payload reader defined by that schema
version.

## Terminology

Use **record** for one `Typdex(type, index)` plus its payload:

```text
record = Typdex(type, index) payload
```

This avoids overloading **packet**, which usually means a complete message or package.
Within object/map payloads, a named record can also be called a **field**. For example,
an object map contains repeated `key + record` pairs, where the key names the field and
the record describes the value type.

Use **message** or **package** for the whole byte stream:

```text
message = version-record *record [eof-record]
```

## Typdex Layout

The C implementation in `c/dybuf.h` is authoritative. Encoders choose the shortest
layout that can hold both values:

| Encoded bytes | Header bits | Type width | Index width | Fast path |
| --- | --- | --- | --- | --- |
| 1 | `0xxx xxxx` | 4 bits | 3 bits | `type <= 0x0f`, `index <= 7` |
| 2 | `10xx xxxx ...` | 6 bits | 8 bits | `type <= 0x3f`, `index <= 255` |
| 3 | `110x xxxx ...` | 8 bits | 13 bits | `type <= 0xff`, `index <= 8191` |
| 4 | `1110 xxxx ...` | 8 bits | 20 bits | `type <= 0xff`, `index <= 1048575` |

All multi-byte integer payloads are encoded big-endian by the existing dybuf helpers.
Because the first bits identify the typdex width, every implementation can decode the
same stream without out-of-band length metadata.

## Canonical Type Values

`dybuf` defines the following type identifiers. Values not listed here are
unassigned, not application extension points:

| Constant | Value | Payload convention |
| --- | ---: | --- |
| `TYPDEX_TYP_NONE` | `0x00` | no payload |
| `TYPDEX_TYP_BOOL` | `0x01` | 1-byte boolean |
| `TYPDEX_TYP_INT` | `0x02` | var signed 64-bit integer |
| `TYPDEX_TYP_UINT` | `0x03` | var unsigned 64-bit integer |
| `TYPDEX_TYP_FLOAT` | `0x06` | 4-byte float |
| `TYPDEX_TYP_DOUBLE` | `0x07` | 8-byte double |
| `TYPDEX_TYP_STRING` | `0x0a` | variable-length string |
| `TYPDEX_TYP_BYTES` | `0x0b` | variable-length binary |
| `TYPDEX_TYP_ARRAY` | `0x0c` | app-defined array payload |
| `TYPDEX_TYP_MAP` | `0x0d` | app-defined map payload |
| `TYPDEX_TYP_OBJ` | `0x0e` | protocol-defined object marker and payload |
| `TYPDEX_TYP_F` | `0x0f` | dypkt function/control packet |

The unassigned 1-byte type IDs are `0x04`, `0x05`, `0x08`, and `0x09`. They are
reserved for future `dybuf` allocation and must not be assigned by application
protocols. `0x0f` is reserved for dypkt control records.

Application schemas should define `index` values under these types. Keep high-frequency
record IDs in `0..7` whenever practical so the complete typdex marker stays 1 byte.

### Protocol-defined objects

`TYPDEX_TYP_OBJ` identifies an object kind, but deliberately defines no object payload
format. Its `index` is local to the protocol named by the enclosing package. Different
protocols may reuse the same index:

```text
Protocol "tour-overview":
  Typdex(TYPDEX_TYP_OBJ, 0) -> OBJ_OVERVIEW
  Typdex(TYPDEX_TYP_OBJ, 1) -> OBJ_LEVEL
  Typdex(TYPDEX_TYP_OBJ, 2) -> OBJ_CELL
```

Indices `0..7` produce 1-byte markers. Larger indices remain valid, but use the
existing multi-byte typdex layouts.

The bytes after an object marker are entirely protocol-defined. A generic `dybuf`
decoder can decode or encode the marker itself, but cannot parse, validate, or skip an
object payload without the protocol schema. Protocol packages must export their own
`OBJ_*` indices and payload rules. `TYPDEX_TYP_OBJ` is the canonical public marker for
protocol-defined objects.

## Index Scope

`index` can be used with two different scopes. Choose one deliberately for each schema.

### Global Record IDs

For protocol/message schemas, treat `type + index` as a globally defined record ID
within the schema version. In this model, every occurrence of the same `type + index`
has the same payload convention no matter where it appears:

```text
Typdex(TYPDEX_TYP_UINT, dype_uiid_grid_lv) -> Var uint(lv)
Typdex(TYPDEX_TYP_MAP, dype_mid_tags)      -> tags map
```

This is the preferred model for dypkt-style package schemas. It allows readers to
dispatch records consistently and makes compatibility rules easier to document. Within
the same schema version, a record's payload convention is global and stable: a reader
that does not understand the record's application meaning can still consume or skip it
as long as the record format is defined by the shared schema. Canonical primitive types
are naturally skippable from the type alone (for example bool, var integers, strings,
bytes, floats, and doubles). App-defined arrays/maps are also skippable when their
record convention is part of the shared schema or when they are length-delimited.
Known records remain stable across compatible versions.

### Object-Local Field IDs

For object-oriented serialization, `index` can instead mean "field number inside the
current object type". In this model, the same `type + index` may have different meanings
inside different classes or nested objects:

```text
Object User:
  Typdex(TYPDEX_TYP_STRING, 0) -> name
  Typdex(TYPDEX_TYP_UINT,   1) -> age

Object Tile:
  Typdex(TYPDEX_TYP_UINT,   0) -> zoom level
  Typdex(TYPDEX_TYP_ARRAY,  1) -> grid indices
```

This is useful for compact in-process object serialization, but it is a weaker
interchange format. A decoder must already know the surrounding object type before it
can interpret an index. Newer writers can easily produce fields that older readers do
not understand, and because typdex does not carry payload length, older readers usually
cannot skip unknown object-local fields safely unless the object convention adds
length-delimited field payloads.

If long-term forward/backward compatibility matters, prefer global record IDs or wrap
object-local fields in a length-delimited container.

## Reserved Function Indices

For `TYPDEX_TYP_F`, dypkt reserves these indices:

| Constant | Value | Payload |
| --- | ---: | --- |
| `DYPE_F_EOF` | `0` | no payload |
| `DYPE_F_VERSION` | `1` | var unsigned schema/dypkt version |
| `DYPE_F_PROTOCOL` | `7` | variable-length cstring protocol name |
| `DYPE_F_PROTO_VERSION` | `8` | var unsigned protocol version |

`DYPE_F_PROTO_VERSION` intentionally uses index `8`, so it encodes as a 2-byte typdex.
Use `DYPE_F_PROTOCOL` alone when a protocol string already carries enough app/service
and version identity.

## Package Header

Every dypkt-compatible message should begin with enough header records for the reader
to choose the right schema before decoding application records. For a single-purpose
stream, the schema version alone can be sufficient:

```text
Typdex(TYPDEX_TYP_F, DYPE_F_VERSION)
Var uint(version >= 0)
```

The same version number means the schema remains compatible. If an existing
`type + index` payload layout changes incompatibly, bump the schema version or allocate
a new index.

For files or transports that may carry multiple protocol families, put the protocol name
before the schema version so the reader knows which version table to apply:

```text
Typdex(TYPDEX_TYP_F, DYPE_F_PROTOCOL)
Var-len cstring(protocol name)

Typdex(TYPDEX_TYP_F, DYPE_F_VERSION)
Var uint(schema version)

Typdex(TYPDEX_TYP_F, DYPE_F_PROTO_VERSION)
Var uint(protocol version)
```

`DYPE_F_PROTO_VERSION` is optional metadata. Prefer `DYPE_F_VERSION` for binary schema
compatibility and use `DYPE_F_PROTO_VERSION` only when the protocol needs an additional
semantic/product version.

The message can end with:

```text
Typdex(TYPDEX_TYP_F, DYPE_F_EOF)
```

## Designing App Schemas

Define app-level record IDs per type:

```c
enum dype_uint_id {
    dype_uiid_grid_lv = 0,
};

enum dype_map_id {
    dype_mid_tags = 0,
    dype_mid_object = 7,
};

enum dype_array_id {
    dype_gridex = 0,
};
```

Example payload conventions:

```text
Typdex(TYPDEX_TYP_MAP, dype_mid_object)
Var uint(count)
repeat count times:
  Var-len string(key)
  Typdex(value_type, value_index)
  Payload defined by value_type/value_index
```

```text
Typdex(TYPDEX_TYP_MAP, dype_mid_tags)
Var uint(count)
repeat count times:
  Var-len string(key)
  Var-len string(value)
```

```text
Typdex(TYPDEX_TYP_ARRAY, dype_gridex)
Var signed int(londex)
Var signed int(latdex)
```

```text
Typdex(TYPDEX_TYP_UINT, dype_uiid_grid_lv)
Var uint(lv)
```

## Container Boundary Strategies

Typdex records do not carry a generic payload length. A schema must therefore define
how nested containers end. There are two common strategies.

### Length-Delimited Containers

Wrap a nested object, array, or map in a byte length:

```text
Typdex(TYPDEX_TYP_OBJ, obj_id)
Var uint(byte_length)
byte_length bytes of nested payload
```

This makes unknown containers easy to skip and supports looser forward compatibility.
The tradeoff is writer complexity: the writer must know the encoded nested length before
writing the parent stream, usually by buffering the nested payload first.

### Count-Bounded Containers

Use schema-defined counts to delimit repeated data:

```text
Typdex(TYPDEX_TYP_UINT, record_count_id)
Var uint(record_count)
repeat record_count times:
  Typdex(...)
  payload defined by the schema
```

This keeps encoding direct and avoids nested buffering. The tradeoff is stricter
compatibility: the reader must understand the schema-defined sequence well enough to
consume the exact number of child records. In this model, unknown `type + index` records
inside the same schema version are normally rejected unless they are already registered
as skippable records.

Both strategies are valid. Choose length-delimited containers for extension-heavy
protocols where old readers should skip future records. Choose count-bounded containers
for compact, deterministic data files where the schema version is the complete binary
contract.

## Registry Design Pattern

For protocol-level schemas, define a per-type registry and name constants by both type
and purpose:

```text
PROTO_OBJ_ROOT     = 0
PROTO_OBJ_SECTION  = 1
PROTO_UINT_COUNT   = 0
PROTO_ARRAY_COORD  = 0
PROTO_MAP_ATTRS    = 0
```

The `(type, index)` pair is the full record identity. Each type has its own `index`
namespace, so `TYPDEX_TYP_OBJ, 0` and `TYPDEX_TYP_UINT, 0` are different records. This
keeps common records in the 1-byte `index 0..7` range without forcing every protocol
record into one shared integer namespace.

For each registered record, document:

- constant name and `(type, index)`;
- required or optional status;
- allowed parent/container position;
- singleton or repeated cardinality;
- exact payload reader;
- whether a reader may skip it when the application does not use its value.

Avoid object-local field numbering when the data is meant to be a long-lived interchange
format. Object-local field IDs are compact but require the surrounding object type to
interpret the same `(type, index)` pair.

## Ordering and Parser Shape

Schemas should define deterministic record ordering. A common pattern is:

1. required metadata records;
2. optional metadata records in a fixed order;
3. count records;
4. repeated child object records;
5. registered optional extension records.

Deterministic order makes parser state machines simpler, improves golden fixture diffs,
and gives stable binary output for cache validation. Readers can use `peek_typdex()` to
detect whether the next record is an optional record for the current container or the
next required record expected by the parent.

Do not silently treat an unexpected marker as "end of object" unless the schema defines
that marker as an explicit scoped terminator. `DYPE_F_EOF` should normally mean end of
the whole message, not the end of a nested object.

## Extension Records

If a schema needs an escape hatch, register it explicitly as a known optional record,
for example a `TYPDEX_TYP_BYTES` extension payload:

```text
Typdex(TYPDEX_TYP_BYTES, extension_id)
Var bytes(extension_blob)
```

Because this record is known, old readers can consume or ignore it safely. This is
different from accepting arbitrary unknown records. Long-term protocol data should be
promoted to named records in a future schema version instead of accumulating hidden
semantics inside an opaque extension blob.

## Compatibility Rules

- Do not change the payload layout for an existing `type + index` within the same
  schema version.
- Prefer adding new indices over redefining old ones.
- Keep common indices in `0..7` for 1-byte typdex markers.
- Document every app-defined `type + index` pair with its exact payload reader.
- Decide whether the schema is strict-versioned or forward-skippable.
- In a strict-versioned schema, reject unknown records in the same schema version unless
  they were already registered as optional/skippable records.
- In a forward-skippable schema, make future extension points length-delimited or define
  their payload solely with canonical primitive conventions so old readers can consume
  them safely.
- Starting to emit an already registered optional record can remain schema-compatible.
  Adding a new record, changing record order, changing required/optional status, or
  changing payload encoding should bump `DYPE_F_VERSION`.

## Small Complete Example

The following example defines a strict-versioned catalog message. It is intentionally
small but includes a protocol header, per-type registry, count-bounded objects, optional
metadata, and an extension record.

### Registry

```c
enum proto_obj_id {
    proto_obj_catalog = 0,
    proto_obj_entry = 1,
};

enum proto_uint_id {
    proto_uint_entry_count = 0,
    proto_uint_entry_id = 1,
    proto_uint_score = 2,
};

enum proto_string_id {
    proto_string_entry_name = 0,
};

enum proto_map_id {
    proto_map_attrs = 0,
};

enum proto_bytes_id {
    proto_bytes_extension = 0,
};
```

All of these indices are in `0..7`, so every marker above is 1 byte.

### Message Layout

```text
Typdex(TYPDEX_TYP_F, DYPE_F_PROTOCOL)
Var-len cstring("example.catalog")

Typdex(TYPDEX_TYP_F, DYPE_F_VERSION)
Var uint(1)

Typdex(TYPDEX_TYP_OBJ, proto_obj_catalog)
Catalog payload

Typdex(TYPDEX_TYP_F, DYPE_F_EOF)        # optional
```

### Catalog Payload

```text
Typdex(TYPDEX_TYP_MAP, proto_map_attrs) # optional
Var uint(entry_count)
repeat entry_count times:
  Var-len string(key)
  Var-len string(value)

Typdex(TYPDEX_TYP_UINT, proto_uint_entry_count)
Var uint(entry_count)

repeat entry_count times:
  Typdex(TYPDEX_TYP_OBJ, proto_obj_entry)
  Entry payload

Typdex(TYPDEX_TYP_BYTES, proto_bytes_extension) # optional
Var bytes(extension_blob)
```

### Entry Payload

```text
Typdex(TYPDEX_TYP_UINT, proto_uint_entry_id)
Var uint(entry_id)

Typdex(TYPDEX_TYP_STRING, proto_string_entry_name)
Var-len string(name)

Typdex(TYPDEX_TYP_UINT, proto_uint_score) # optional
Var uint(score)

Typdex(TYPDEX_TYP_MAP, proto_map_attrs)   # optional
Var uint(entry_count)
repeat entry_count times:
  Var-len string(key)
  Var-len string(value)
```

### Reader Rules

- Verify `DYPE_F_PROTOCOL` and `DYPE_F_VERSION` before reading `proto_obj_catalog`.
- Decode records in the documented order.
- Use `proto_uint_entry_count` to know exactly how many `proto_obj_entry` objects follow.
- If an optional record is present, decode it with its registered payload convention.
- Reject unknown records in schema version `1`; do not guess where an object ends.
- Accept unknown keys inside `proto_map_attrs` if the map is explicitly documented as
  descriptive metadata.
- Ignore `proto_bytes_extension` if the application does not understand its contents,
  but still consume its `varbytes` payload.

## Language API Notes

- C exposes `dyb_append_typdex`, `dyb_next_typdex`, and `dyb_peek_typdex`, plus `dype_fid`
  in `c/dypkt.h`.
- Python exposes `append_typdex`, `next_typdex`, `peek_typdex`, `TYPDEX_TYP_*`, and
  `DYPE_F_*` from the `dybuf` package.
- JavaScript exposes `putTypdex`, `getTypdex`, `TYPDEX_TYP_*`, and `DYPE_F_*` from the
  `dybuf` module. The constants are also static fields on `DyBuf`.
- Java exposes `putTypdex`, `getTypdex`, `Typdex`, `TYPDEX_TYP_*`, and `DYPE_F_*` on
  `DyBuf`. Java typdex wire-layout parity is still tracked in `CROSS_LANGUAGE_ALIGNMENT.md`.
