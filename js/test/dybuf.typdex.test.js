import test from 'node:test';
import assert from 'node:assert/strict';
import { DyBuf } from '../DyBuf.js';
import {
  arrayBufferFrom,
  bufferToHex,
  fixtureBytes,
  loadFixture,
} from '../testutils/fixture-utils.js';

test('typdex fixtures round-trip', () => {
  const cases = loadFixture('typdex');
  for (const { id, type, index, encoded_hex: encodedHex } of cases) {
    const encodedBytes = fixtureBytes(encodedHex);
    const reader = new DyBuf(arrayBufferFrom(encodedBytes));
    const { type: decodedType, index: decodedIndex } = reader.getTypdex();
    assert.strictEqual(decodedType, type, `decoded type mismatch for ${id}`);
    assert.strictEqual(decodedIndex, index, `decoded index mismatch for ${id}`);
    assert.strictEqual(reader.position(), reader.limit(), `unterminated bytes for ${id}`);

    const writer = new DyBuf(encodedBytes.length + 8);
    writer.putTypdex(type, index);
    const actualHex = bufferToHex(writer.toBytesBeforeCurrentPosition());
    assert.strictEqual(actualHex, encodedHex, `re-encode mismatch for ${id}`);
  }
});

test('typdex validates out of range arguments', () => {
  const buf = new DyBuf(8);
  assert.throws(() => buf.putTypdex(-1, 0), RangeError);
  assert.throws(() => buf.putTypdex(0, -1), RangeError);
  assert.throws(() => buf.putTypdex(0x100, 0), RangeError);
  assert.throws(() => buf.putTypdex(0, 0x100000), RangeError);
});

test('legacy typdex aliases remain compatible', () => {
  const buf = new DyBuf(16);
  buf.putIndexAnd4bitsType(0x1234, 0x42);
  buf.flip();
  const { type, value } = buf.getIndexAnd4bitsType();
  assert.strictEqual(type, 0x42);
  assert.strictEqual(value, 0x1234);
});
