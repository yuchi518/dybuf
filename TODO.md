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
- [x] 實作 Python JSON encoder/decoder utility
- [x] 實作 JavaScript JSON encoder/decoder utility
- [x] 建立 Python/JavaScript 共用 JSON fixture
- [x] 透過共用 fixture 驗證 Python 產生的 JSON-dybuf bytes 可由 JavaScript decode
- [x] 透過共用 fixture 驗證 JavaScript 產生的 JSON-dybuf bytes 可由 Python decode
- [x] 加入隨機 JSON round-trip 測試，先限制深度、key 字元集、number 範圍
- [x] 明確測試 root object、root array、nested object、array of objects、mixed-type array
- [x] 確認第一版不追求 canonical bytes，只追求 encode/decode round-trip 等價
- [x] 文件化 JSON number policy：safe integer、double、`-0`、非 JSON number 的處理

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
- Version 1 does not guarantee that JavaScript `-0` round-trips distinctly from `0`.
- Array elements are always individually typdex-tagged. Array element typdex indices
  default to `0`; object key indices are carried by map value typdex markers.

## Fixed-width floating point endian parity

- [x] 將 C `dyb_append_float` / `dyb_append_double` 的 wire format 明確修正為
  big-endian，和 `dyb_append_u16/u32/u64` 等固定寬度整數一致
- [x] 將 C `dyb_next_float` / `dyb_next_double` 對應調整為讀取 big-endian bits
- [x] 重新產生 JSON fixtures，確認 JSON double payload 不再依賴 host endian
- [x] 將 Python JSON double encode/decode 改回 big-endian
- [x] 將 JavaScript JSON double encode/decode 改回 big-endian
- [x] 增加或確認 golden fixture 覆蓋 fractional JSON number，例如 `3.5`
- [x] 在文件中明確說明 multi-byte fixed-width integer/float/double 都使用 big-endian

### Findings

- dybuf 既有 multi-byte integer helpers 採 big-endian wire order。
- 目前 C float/double helper 先 `dyb_swap_u32/u64` 再呼叫 big-endian append，
  在 little-endian host 上實際輸出 little-endian payload，導致 JSON double fixture
  目前也跟著偏離整體 dybuf 慣例。
- Python/JavaScript JSON helper 曾為了通過 C fixture 暫時跟隨這個輸出；修正 C 後
  需要同步改回 big-endian。

## Java golden fixture alignment

- [x] 修正 Java typdex encode/decode layout：2-byte form 使用 `type:6,index:8`，
  3-byte form 使用 `type:8,index:13`，4-byte form 使用 `type:8,index:20`
- [x] 修正 Java zero-length bytes/string readers，回傳 empty `byte[]` / `""` 而不是 `null`
- [x] 修正 Java `capacity(newCapacity)` 縮小時 copy 超出新陣列長度的問題
- [x] 補 Java golden test runner，讀取 `fixtures/v1/varint_unsigned.json`
- [x] 補 Java golden test runner，讀取 `fixtures/v1/varint_signed.json`
- [x] 補 Java golden test runner，讀取 `fixtures/v1/typdex.json`
- [x] 補 Java golden test runner，讀取 `fixtures/v1/varlen_bytes.json`
- [x] 補 Java golden test runner，讀取 `fixtures/v1/varlen_strings.json`
- [x] 將 Java golden test 加入可重複執行的命令或腳本
- [x] 更新 `CROSS_LANGUAGE_ALIGNMENT.md` 的 Java checklist 狀態

### Findings

- Java varint bucket encoding大致與 C 一致，但尚未用完整 golden fixtures 覆蓋。
- Java typdex 仍使用舊 layout；例如 C canonical `83ff` 應 decode 為
  `type=3,index=255`，Java 目前 decode 為 `type=1,index=511`。
- Java `putTypdex(type=63,index=255)` 目前輸出 `df80ff`，C canonical 是 `bfff`。
- Java var-length empty bytes/string 目前 decode 為 `null`，與 Python/JavaScript
  及 fixture 期待的 empty payload 語義不同。
- Java 目前沒有 Maven/Gradle/JUnit wiring，只有 IntelliJ project files；第一階段可先用
  `javac/java` 可執行的 fixture runner 建立 golden test 流程。
