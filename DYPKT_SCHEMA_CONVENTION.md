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

`dybuf` reserves the following type identifiers:

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
| `TYPDEX_TYP_F` | `0x0f` | dypkt function/control packet |

Application schemas should define `index` values under these types. Keep high-frequency
record IDs in `0..7` whenever practical so the complete typdex marker stays 1 byte.

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

Every dypkt-compatible message should begin with the schema version:

```text
Typdex(TYPDEX_TYP_F, DYPE_F_VERSION)
Var uint(version >= 0)
```

The same version number means the schema remains compatible. If an existing
`type + index` payload layout changes incompatibly, bump the schema version or allocate
a new index.

Optional protocol metadata can follow:

```text
Typdex(TYPDEX_TYP_F, DYPE_F_PROTOCOL)
Var-len cstring(protocol name)

Typdex(TYPDEX_TYP_F, DYPE_F_PROTO_VERSION)
Var uint(protocol version)
```

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

## Compatibility Rules

- Do not change the payload layout for an existing `type + index` within the same
  schema version.
- Prefer adding new indices over redefining old ones.
- Keep common indices in `0..7` for 1-byte typdex markers.
- Document every app-defined `type + index` pair with its exact payload reader.
- Typdex does not include payload length. A decoder can only skip unknown records when
  that record's convention includes a length-delimited payload or the decoder already
  knows its fixed size. For forward-compatible extension points, prefer wrapping unknown
  or nested values in a length-delimited map/object convention.

## Language API Notes

- C exposes `dyb_append_typdex`, `dyb_next_typdex`, and `dyb_peek_typdex`, plus `dype_fid`
  in `c/dypkt.h`.
- Python exposes `append_typdex`, `next_typdex`, `peek_typdex`, `TYPDEX_TYP_*`, and
  `DYPE_F_*` from the `dybuf` package.
- JavaScript exposes `putTypdex`, `getTypdex`, `TYPDEX_TYP_*`, and `DYPE_F_*` from the
  `dybuf` module. The constants are also static fields on `DyBuf`.
- Java exposes `putTypdex`, `getTypdex`, `Typdex`, `TYPDEX_TYP_*`, and `DYPE_F_*` on
  `DyBuf`. Java typdex wire-layout parity is still tracked in `CROSS_LANGUAGE_ALIGNMENT.md`.
