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
