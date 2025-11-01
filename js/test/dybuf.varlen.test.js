import test from 'node:test';
import assert from 'node:assert/strict';
import { DyBuf } from '../DyBuf.js';
import {
  arrayBufferFrom,
  bufferToHex,
  fixtureBytes,
  loadFixture,
} from '../testutils/fixture-utils.js';

test('varlen bytes fixtures round-trip', () => {
  const cases = loadFixture('varlen_bytes');
  for (const {
    id,
    payload_hex: payloadHex,
    payload_length: payloadLength,
    encoded_hex: encodedHex,
  } of cases) {
    const encodedBytes = fixtureBytes(encodedHex);
    const reader = new DyBuf(arrayBufferFrom(encodedBytes));
    const decodedBuffer = reader.getBytesWithVarLength();
    assert.strictEqual(reader.position(), reader.limit(), `unterminated bytes for ${id}`);
    assert.strictEqual(decodedBuffer.byteLength, payloadLength, `length mismatch for ${id}`);
    const actualPayloadHex = bufferToHex(decodedBuffer);
    assert.strictEqual(actualPayloadHex, payloadHex, `payload mismatch for ${id}`);

    const payloadBytes = fixtureBytes(payloadHex);
    const writer = new DyBuf(encodedBytes.length + 16);
    writer.putBytesWithVarLength(arrayBufferFrom(payloadBytes));
    const actualHex = bufferToHex(writer.toBytesBeforeCurrentPosition());
    assert.strictEqual(actualHex, encodedHex, `re-encode mismatch for ${id}`);
  }
});

test('putBytesWithVarLength(null) encodes length 0', () => {
  const buf = new DyBuf(8);
  buf.putBytesWithVarLength(null);
  const encodedHex = bufferToHex(buf.toBytesBeforeCurrentPosition());
  assert.strictEqual(encodedHex, '00');
  buf.flip();
  const decoded = buf.getBytesWithVarLength();
  assert.ok(decoded instanceof ArrayBuffer);
  assert.strictEqual(decoded.byteLength, 0);
});

test('varlen string fixtures round-trip', () => {
  const cases = loadFixture('varlen_strings');
  for (const { id, utf8, encoded_hex: encodedHex } of cases) {
    const encodedBytes = fixtureBytes(encodedHex);
    const reader = new DyBuf(arrayBufferFrom(encodedBytes));
    const decoded = reader.getCStringWithVarLength();
    assert.strictEqual(decoded, utf8, `decoded string mismatch for ${id}`);
    assert.strictEqual(reader.position(), reader.limit(), `unterminated bytes for ${id}`);

    const writer = new DyBuf(encodedBytes.length + 16);
    writer.putCStringWithVarLength(utf8);
    const actualHex = bufferToHex(writer.toBytesBeforeCurrentPosition());
    assert.strictEqual(actualHex, encodedHex, `re-encode mismatch for ${id}`);
  }
});

test('varlen string helpers omit trailing terminator', () => {
  const varBuf = new DyBuf(32);
  varBuf.putVarString('hello');
  const varHex = bufferToHex(varBuf.toBytesBeforeCurrentPosition());

  const cBuf = new DyBuf(32);
  cBuf.putCStringWithVarLength('hello');
  const cHex = bufferToHex(cBuf.toBytesBeforeCurrentPosition());

  assert.ok(varHex.length < cHex.length, 'var string should be shorter than c-string encoding');

  varBuf.flip();
  assert.strictEqual(varBuf.getVarString(), 'hello');
  assert.strictEqual(varBuf.remaining(), 0);
});
