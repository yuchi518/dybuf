# TODO

## Typdex / TYP_OBJ

- [x] 盤點 C canonical type/dex 定義與實際數值
- [x] 列出 1-byte typdex 中所有已使用、保留及未配置的 type IDs
- [x] 確認 type ID `0xE` 是否可配置為 `TYP_OBJ`
- [x] 核對 Python、JavaScript、Java 公開的 type/dex 常數
- [x] 記錄各語言的缺漏、命名差異及數值漂移
- [x] 在 C 中正式定義 `TYP_OBJ`，但不定義 object payload 或 index 語義
- [x] 將 `TYP_OBJ` 與既有 type/dex 數值公開至 Python
- [x] 將 `TYP_OBJ` 與既有 type/dex 數值公開至 JavaScript
- [x] 評估 Java 是否僅同步常數，或延後至相容性重構
- [x] 建立 canonical type ID registry 文件
- [x] 說明 `TYP_OBJ` 的 dex/index 屬於 protocol-local namespace
- [x] 說明通用 decoder 只辨識 marker，不解析或跳過 protocol-defined payload
- [x] 增加 fixture/test，驗證各 binding 的 typdex 組合與拆解數值一致
- [x] 執行 C、Python、JavaScript 測試及跨語言 fixture 驗證

### Findings

- Canonical public name: `TYPDEX_TYP_OBJ = 0x0e`.
- Python and JavaScript export the canonical `TYPDEX_TYP_OBJ` name without an object alias.
- C/dypkt exposes the corresponding `dype_obj`; Java only synchronizes constants.
- A 1-byte typdex has a 4-bit type and a 3-bit index, so protocol-local indices
  `0..7` stay within one byte. Index `8` and above use an existing wider layout.
- Unassigned 1-byte type IDs are `0x04`, `0x05`, `0x08`, and `0x09`.

## JSON-equivalent dybuf utilities

- [x] 在 `DYPKT_SCHEMA_CONVENTION.md` 區分三種 schema style
- [x] 定義 JSON-equivalent encoding 的 document-level dictionary collection 概念
- [x] 決定 JSON dictionary metadata 使用 `TYPDEX_TYP_OBJ`，不使用 `TYPDEX_TYP_F`
- [x] 定義 dictionary path 使用 structural index path，例如 `$`, `$.[]`, `$.2.[]`
- [x] 記錄 encoder 需要 pre-pass 或 temporary payload buffer，因為 dictionary collection
  必須寫在 JSON payload 前面
- [ ] 實作 Python JSON encoder/decoder utility
- [ ] 實作 JavaScript JSON encoder/decoder utility
- [ ] 建立 Python/JavaScript 共用 JSON fixture
- [ ] 加入 Python encode -> JS decode 驗證
- [ ] 加入 JS encode -> Python decode 驗證
- [ ] 加入隨機 JSON round-trip 測試，先限制深度、key 字元集、number 範圍
- [ ] 明確測試 root object、root array、nested object、array of objects、mixed-type array
- [x] 確認第一版不追求 canonical bytes，只追求 encode/decode round-trip 等價
- [ ] 文件化 JSON number policy：safe integer、double、`-0`、非 JSON number 的處理

### Draft v1 constraints

- v1 target is JSON value round-trip equivalence: `decode(encode(value)) == value`.
  Canonical byte output is not a v1 goal.
- JSON object member order follows the parsed input order when assigning dictionary
  indices. Semantically equivalent objects with different member order may encode to
  different byte streams.
- Object keys are strings. Duplicate keys should be rejected before encoding if the
  source parser exposes them; otherwise the parser's object semantics apply.
- Integers should stay within JavaScript's safe integer range for the first
  cross-language utility. Fractional values use double. `NaN` and infinities are
  rejected because they are not valid JSON values.
- Array elements are always individually typdex-tagged. Array element typdex indices
  default to `0`; object key indices are carried by map value typdex markers.
