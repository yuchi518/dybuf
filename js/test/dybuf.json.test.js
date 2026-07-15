import test from 'node:test';
import assert from 'node:assert/strict';
import {
  decodeJson,
  encodeJson,
} from '../DyBuf.js';
import {
  arrayBufferFrom,
  bufferToHex,
  fixtureBytes,
  loadFixture,
} from '../testutils/fixture-utils.js';

test('JSON-dybuf fixtures decode and encode', () => {
  const cases = loadFixture('json_values');
  for (const { id, value, encoded_hex: encodedHex } of cases) {
    const encodedBytes = fixtureBytes(encodedHex);
    assert.deepStrictEqual(
      decodeJson(arrayBufferFrom(encodedBytes)),
      value,
      `decoded value mismatch for ${id}`
    );
    assert.strictEqual(
      bufferToHex(encodeJson(value)),
      encodedHex,
      `encoded bytes mismatch for ${id}`
    );
  }
});

test('JSON-dybuf random round-trip', () => {
  const rng = makeRng(20260711);
  for (let i = 0; i < 100; i += 1) {
    const value = randomJsonValue(rng, 0);
    assert.deepStrictEqual(decodeJson(encodeJson(value)), value);
  }
});

test('JSON-dybuf rejects unsupported values', () => {
  assert.throws(() => encodeJson(2 ** 53), /safe integer/);
  assert.throws(() => encodeJson(Number.NaN), /finite/);
  assert.throws(
    () => decodeJson(fixtureBytes('7001007138000000000000f87f')),
    /finite/
  );
  assert.throws(() => encodeJson({ bad: undefined }), /unsupported JSON value type/);
  assert.throws(() => encodeJson(1n), /unsupported JSON value type/);
});

function makeRng(seed) {
  let state = seed >>> 0;
  return () => {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
    return state / 0x100000000;
  };
}

function randomInt(rng, maxExclusive) {
  return Math.floor(rng() * maxExclusive);
}

function randomJsonValue(rng, depth) {
  if (depth >= 4) {
    return randomScalar(rng);
  }

  const choice = randomInt(rng, 8);
  if (choice <= 4) {
    return randomScalar(rng);
  }
  if (choice === 5) {
    return Array.from(
      { length: randomInt(rng, 4) },
      () => randomJsonValue(rng, depth + 1)
    );
  }

  const result = {};
  const keyCount = randomInt(rng, 4);
  for (let keyIndex = 0; keyIndex < keyCount; keyIndex += 1) {
    result[`k${depth}_${keyIndex}`] = randomJsonValue(rng, depth + 1);
  }
  return result;
}

function randomScalar(rng) {
  switch (randomInt(rng, 7)) {
    case 0:
      return null;
    case 1:
      return randomInt(rng, 2) === 1;
    case 2:
      return -1 - randomInt(rng, 1000);
    case 3:
      return randomInt(rng, 1001);
    case 4:
      return [0.25, -1.5, 3.5, 100.125][randomInt(rng, 4)];
    default:
      return ['', 'dybuf', 'json', 'map-data'][randomInt(rng, 4)];
  }
}
