# dypkt Schema Convention

`typdex` is the compact wire tag used by `dybuf`/`dypkt`. It is not a full schema
language by itself. A schema convention is a separate design choice layered on top of
`Typdex(type, index)` and the dybuf primitive payload encodings.

This document describes several schema styles rather than one universal convention.
Choose the style that matches the data model, compatibility goal, and amount of schema
machinery a project wants to maintain.

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

Application schemas should define `index` values under these types. The meaning of
`index` depends on the schema style: it can be a field number, a JSON encoding variant,
or a protocol-level record ID. Keep high-frequency indices in `0..7` whenever practical
so the complete typdex marker stays 1 byte.

## Basic Typdex Use

At the wire level, a record starts with:

```text
Typdex(type, index)
payload chosen by the schema
```

The `type` identifies the broad dybuf payload family, and `index` refines that marker
inside the selected schema style. A generic dybuf implementation can encode and decode
the marker itself, but it cannot know the application payload rules for arrays, maps,
objects, or protocol-specific records without the surrounding schema.

## Schema Styles

The same typdex primitive can support different schema styles:

1. **Field-indexed object serialization**: `index` is the field number within the
   current object/class, similar to protobuf field IDs. The schema is code-first rather
   than generated from a `.proto` file.
2. **JSON-equivalent encoding**: dybuf is used as a binary representation for the JSON
   value model. Helper functions convert JSON values to/from dybuf without app-specific
   record registries.
3. **Semantic record registry**: `(type, index)` is a protocol-level record ID with a
   stable payload convention. This is useful for strict binary file protocols, but it is
   the most specialized style and should not be treated as the default.

## Style 1: Field-Indexed Object Serialization

In this style, `index` means "field number inside this object type". The same
`type + index` can mean different fields in different object classes because the
surrounding object type selects the field table.

Example object schema:

```text
Object User:
  field 0: TYPDEX_TYP_UINT   id
  field 1: TYPDEX_TYP_STRING name
  field 2: TYPDEX_TYP_STRING email       # optional
  field 3: TYPDEX_TYP_ARRAY  tags        # repeated/generic payload
```

Encoder shape:

```text
encode_user(user):
  putTypdex(TYPDEX_TYP_UINT, 0)
  putVarULong(user.id)

  putTypdex(TYPDEX_TYP_STRING, 1)
  putVarString(user.name)

  if user.email is present:
    putTypdex(TYPDEX_TYP_STRING, 2)
    putVarString(user.email)

  if user.tags is present:
    putTypdex(TYPDEX_TYP_ARRAY, 3)
    putVarULong(tag_count)
    repeat tag_count:
      putVarString(tag)
```

Decoder shape:

```text
decode_user(field_count or byte_limit):
  user = new User()
  repeat until object boundary:
    typdex = nextTypdex()
    switch typdex.index:
      case 0:
        require typdex.type == TYPDEX_TYP_UINT
        user.id = nextVarULong()
      case 1:
        require typdex.type == TYPDEX_TYP_STRING
        user.name = nextVarString()
      case 2:
        require typdex.type == TYPDEX_TYP_STRING
        user.email = nextVarString()
      case 3:
        require typdex.type == TYPDEX_TYP_ARRAY
        user.tags = read_tags_array()
      default:
        skip or reject according to the object compatibility policy
```

Wrong type for a known field is a decoding error. For example, field `1` above must be
`TYPDEX_TYP_STRING`; `TYPDEX_TYP_UINT, 1` is not a valid alternate encoding for `name`.

This style is the most natural choice when serializing application objects:

- multiple fields with the same dybuf type are easy because their field numbers differ;
- each object/class can reuse compact field numbers `0..7`;
- code directly documents encode/decode behavior, without requiring a schema compiler;
- optional generic fields can be supported by reserving fields whose payload is itself
  a typdex-tagged value, byte blob, map, or array.

The object boundary must still be explicit. Common choices are:

- a field count before the object payload;
- a byte length before the object payload;
- a parent container count that says how many objects follow;
- a fixed record order for small closed objects.

If unknown field skipping matters, prefer length-delimited object payloads or restrict
unknown fields to canonical primitive/var-length payloads that a reader can consume
without understanding application semantics.

## Style 2: JSON-Equivalent Encoding

This style uses dybuf as a binary representation of the JSON value model. The goal is
not an app-specific schema; the goal is a pair of helpers such as:

```text
append_json_value(buf, value)
next_json_value(buf) -> value
```

The root value is encoded as one JSON value record. Its `index` should be `0`
regardless of whether the value is a scalar, array, or object:

```text
Typdex(json_value_type, 0)
JSON payload
```

A prototype should round-trip the JSON data model:

| JSON value | Suggested root/array typdex |
| --- | --- |
| `null` | `TYPDEX_TYP_NONE, 0` |
| boolean | `TYPDEX_TYP_BOOL, 0` + bool payload |
| integer number | `TYPDEX_TYP_INT/UINT, 0` + varint payload |
| floating number | `TYPDEX_TYP_DOUBLE, 0` + double payload |
| string | `TYPDEX_TYP_STRING, 0` + varstring payload |
| array | `TYPDEX_TYP_ARRAY, 0` + count + repeated JSON values |
| object | `TYPDEX_TYP_MAP, 0` + current dictionary context + repeated value records |

### Array Payload

JSON arrays can contain mixed value types, so every element needs its own typdex marker:

```text
Typdex(TYPDEX_TYP_ARRAY, 0)
Var uint(item_count)
repeat item_count times:
  Typdex(element_type, 0)
  JSON value
```

The recommended convention is `index = 0` for array elements. Array position already
comes from sequence order, so storing the array index in typdex is redundant and becomes
less compact after index `7`. A variant may store the element position in `index`, but
that should be documented as a non-default JSON encoding variant.

### Object / Map Payload

JSON object keys are strings and values can be any JSON type. A JSON-equivalent dybuf
encoding therefore needs a key dictionary for object/map values. There are two useful
variants.

#### Inline Dictionary Variant

The simplest variant stores the key dictionary directly inside each map payload. Then
each value typdex uses `index` to point into that local dictionary:

```text
Typdex(TYPDEX_TYP_MAP, 0)
Var uint(key_count)
repeat key_count times:
  Var-len string(key)

repeat key_count times:
  Typdex(value_type, key_index)
  JSON value
```

For deterministic output, writers should emit value records in key dictionary order and
set `key_index` to the corresponding dictionary slot. Readers should reject duplicate
keys in the dictionary, duplicate `key_index` value records, missing value records, and
`key_index >= key_count`.

This gives `index` a local, mechanical meaning only inside the current JSON object. It
does not create an app-specific schema: the dictionary embedded in that object is the
schema for that object's keys.

This variant is self-contained but repeats key dictionaries. For many small objects with
the same shape, dictionary overhead can be larger than the payload savings.

#### Document-Level Dictionary Variant

For larger JSON documents, define all object-key dictionaries once near the beginning
of the dybuf stream. This keeps repeated object payloads small while still allowing
generic JSON decoding.

The encoder cannot stream the final bytes directly to the output file in one pass
unless it already knows all dictionaries. It must either:

- make a pre-pass over the JSON tree to build the dictionary collection, then encode
  the payload; or
- encode the payload into a temporary dybuf while collecting dictionaries, then write
  the dictionary collection before the buffered payload.

One practical layout is to reserve two JSON document objects under `TYPDEX_TYP_OBJ`:

```text
Typdex(TYPDEX_TYP_OBJ, 0)          # JSON dictionary collection
Var uint(json_dybuf_format_version)
Var uint(dictionary_count)
repeat dictionary_count times:
  Var-len string(dictionary_name)
  Var uint(key_count)
  repeat key_count times:
    Var-len string(key)

Typdex(TYPDEX_TYP_OBJ, 1)          # JSON payload
Typdex(root_json_value_type, 0)
Root JSON payload
```

`TYPDEX_TYP_OBJ, 0` is the metadata block that a generic JSON-dybuf decoder needs
before reading object values. `json_dybuf_format_version` should start at `1` and
identify this JSON encoding convention, including path grammar, dictionary rules, and
number policy. `TYPDEX_TYP_OBJ, 1` marks the start of the encoded JSON payload. This is
a JSON encoding convention; it is separate from dypkt function/control records.

The dictionary collection is not one global key dictionary for the whole JSON document.
It is a set of dictionaries, one per structural object position. During encoding, each
dictionary grows as keys are encountered at that structural position. When a new key is
inserted, the next index is the previous dictionary size.

Example encoder-side dictionary state:

```json
{
  "$": {
    "key1": 0,
    "key2": 1,
    "key3": 2
  },
  "$.2.[]": {
    "key11": 0
  }
}
```

In this example, `$` is the root object dictionary. `key3` is assigned index `2` in the
root dictionary, so objects inside the array stored at `key3` use the dictionary named
`$.2.[]`. The path uses dictionary indices, not key strings. This keeps dictionary names
stable after encoding and avoids repeating long key names in nested dictionary names.

The dictionary name is a structural value path. A version-1 prototype should use this
grammar:

```text
$       root value
.K      value stored under object key index K in the current object dictionary
.[]     value stored as an element of the current array
```

Object values use their current value path as the dictionary name. Examples:

```text
$          root object
$.[]       object elements of a root array
$.2        object value stored under root key index 2
$.2.[]     object elements inside the array stored under root key index 2
$.2.[].1   object value stored under key index 1 of those array elements
```

When an array contains objects, all object elements at that array position share the
same dictionary path. This is the main compression benefit for common JSON payloads such
as `[{...}, {...}, ...]`: the root array object elements share `$.[]`.

When an object contains different object-valued keys, those child objects use different
dictionary paths because their parent key indices differ. This avoids merging unrelated
object shapes just because they appear at the same depth.

When encoding an object/map value, the current traversal context selects the dictionary.

The object/map payload does not need to repeat the dictionary name when both encoder and
decoder use the same traversal rules. Its value records use `typdex.index` as the key
index in the current dictionary:

```text
Typdex(TYPDEX_TYP_MAP, 0)
Var uint(present_count)
repeat present_count times:
  Typdex(value_type, key_index)
  JSON value
```

`key_index` is the slot in the current structural dictionary. This supports sparse
objects: the dictionary may contain all keys ever seen at that position, while each
object instance emits only its present keys. Readers should reject duplicate
`key_index`, `key_index >= key_count`, missing dictionary names, and missing required
keys if the JSON encoding variant defines required keys.

For the JSON shape:

```json
{
  "key1": 123,
  "key2": "asdf",
  "key3": [{}, {}]
}
```

the root dictionary is named `$`, with keys `key1`, `key2`, `key3`. If objects inside
the `key3` array later gain keys, their dictionary is named `$.2.[]` because `key3`
has root dictionary index `2`.

Writers should preserve JSON object member order while building dictionaries. The first
time a key appears in a structural dictionary, it receives the next index. Later objects
at the same structural path reuse that index. This makes the encoded bytes depend on
the input key order, which is acceptable for a JSON-equivalent transport.

Version 1 targets round-trip equivalence:

```text
decode(encode(value)) == value
```

It does not target canonical bytes. Two JSON objects with the same semantic content but
different member order may encode to different byte streams, and two language bindings
may produce different byte streams if their parsed object member order differs. The
required compatibility property is that Python and JavaScript can decode each other's
valid JSON-dybuf byte streams back to the same JSON value. If canonical binary output
is required later, define a canonical key ordering or frequency-based index assignment
as a separate format version.

A variant may put an explicit dictionary ID in every map payload instead of deriving the
dictionary name from traversal context. That is simpler for random access into nested
payloads, but it costs extra bytes per object and is not the compact default described
above.

For a first cross-language prototype, keep JSON number handling conservative:

- encode integer-looking numbers as `TYPDEX_TYP_INT` or `TYPDEX_TYP_UINT` only when the
  value is inside JavaScript's safe integer range;
- encode fractional numbers as `TYPDEX_TYP_DOUBLE`;
- reject `NaN`, `Infinity`, and `-Infinity`, because they are not JSON values;
- do not guarantee that JavaScript `-0` round-trips distinctly from `0` in version 1.

Avoid `TYPDEX_TYP_F` for JSON dictionaries because `TYPDEX_TYP_F` is reserved for dypkt
control records. If the JSON encoding needs an explicit dictionary-table marker,
`TYPDEX_TYP_OBJ` is the better fit because the dictionary table is a JSON-encoding
object, not a package control function.

The version-1 Python and JavaScript utilities implement the document-level dictionary
variant above. Arrays and objects carry counts, and the shared `json_values` fixture is
generated and verified by the C fixture toolchain before the language bindings consume
it. Duplicate object-key policy remains delegated to the host JSON/object model before
encoding; decoders reject duplicate key indices within an encoded object.

This style is the easiest entry point for users who already have JSON-compatible data
and want a dybuf binary form without designing a custom typdex registry.

## Style 3: Semantic Record Registry

For protocol/message schemas, `type + index` can be treated as a globally defined
record ID within the schema version. In this model, every occurrence of the same
`type + index` has the same payload convention no matter where it appears:

```text
Typdex(TYPDEX_TYP_UINT, record_grid_lv) -> Var uint(lv)
Typdex(TYPDEX_TYP_MAP, record_tags)     -> tags map
```

This style is useful for strict binary file protocols and compact static data. It is
not a good default for general object serialization. If one container needs multiple
fields of the same conceptual type, field-indexed object serialization is usually
clearer than inventing many semantic subtypes.

Within the same schema version, a record's payload convention is global and stable: a
reader that does not understand the record's application meaning can still consume or
skip it if the record format is defined by the shared schema. Canonical primitive types
are naturally skippable from the type alone (for example bool, var integers, strings,
bytes, floats, and doubles). App-defined arrays/maps/objects are skippable only when
their record convention is part of the shared schema or when they are length-delimited.

For this style, `TYPDEX_TYP_OBJ` can identify protocol-defined object kinds:

```text
Protocol "example.catalog":
  Typdex(TYPDEX_TYP_OBJ, 0) -> OBJ_CATALOG
  Typdex(TYPDEX_TYP_OBJ, 1) -> OBJ_ENTRY
```

The bytes after an object marker are entirely protocol-defined. A generic `dybuf`
decoder can decode or encode the marker itself, but cannot parse, validate, or skip an
object payload without the protocol schema. Protocol packages should export their own
`OBJ_*` indices and payload rules.

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

## Style 3 Example Records

For a semantic record registry, define app-level record IDs per type:

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

Example payload conventions for this style:

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
