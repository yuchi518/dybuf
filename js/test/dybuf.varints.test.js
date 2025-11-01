import test from 'node:test';
import assert from 'node:assert/strict';
import { DyBuf } from '../DyBuf.js';
import {
  arrayBufferFrom,
  bufferToHex,
  fixtureBytes,
  loadFixture,
} from '../testutils/fixture-utils.js';

test('unsigned varint fixtures round-trip', () => {
  const cases = loadFixture('varint_unsigned');
  for (const { id, value_dec: valueDec, encoded_hex: encodedHex } of cases) {
    const expectedValue = BigInt(valueDec);
    const encodedBytes = fixtureBytes(encodedHex);
    const reader = new DyBuf(arrayBufferFrom(encodedBytes));
    const decoded = reader.getVarULong();
    assert.strictEqual(
      decoded,
      BigInt.asUintN(64, expectedValue),
      `decoded mismatch for ${id}`
    );
    assert.strictEqual(reader.position(), reader.limit(), `unterminated bytes for ${id}`);

    const writer = new DyBuf(encodedBytes.length + 8);
    writer.putVarULong(expectedValue);
    const actualHex = bufferToHex(writer.toBytesBeforeCurrentPosition());
    assert.strictEqual(actualHex, encodedHex, `re-encode mismatch for ${id}`);
  }
});

test('signed varint fixtures round-trip', () => {
  const cases = loadFixture('varint_signed');
  for (const { id, value_dec: valueDec, encoded_hex: encodedHex } of cases) {
    const expectedValue = BigInt(valueDec);
    const encodedBytes = fixtureBytes(encodedHex);
    const reader = new DyBuf(arrayBufferFrom(encodedBytes));
    const decoded = reader.getVarLong();
    assert.strictEqual(
      decoded,
      BigInt.asIntN(64, expectedValue),
      `decoded mismatch for ${id}`
    );
    assert.strictEqual(reader.position(), reader.limit(), `unterminated bytes for ${id}`);

    const writer = new DyBuf(encodedBytes.length + 8);
    writer.putVarLong(expectedValue);
    const actualHex = bufferToHex(writer.toBytesBeforeCurrentPosition());
    assert.strictEqual(actualHex, encodedHex, `re-encode mismatch for ${id}`);
  }
});
