import { readFileSync } from 'node:fs';
import { join } from 'node:path';

function hexToUint8Array(hex) {
  if (hex.length === 0) return new Uint8Array(0);
  if (hex.length % 2 !== 0) {
    throw new Error(`Invalid hex length: ${hex.length}`);
  }
  const byteLength = hex.length / 2;
  const array = new Uint8Array(byteLength);
  for (let i = 0; i < byteLength; i += 1) {
    const byteHex = hex.slice(i * 2, i * 2 + 2);
    array[i] = Number.parseInt(byteHex, 16);
  }
  return array;
}

export function loadFixture(name) {
  const path = join(process.cwd(), '..', 'fixtures', 'v1', `${name}.json`);
  const content = readFileSync(path, 'utf8');
  const { cases } = JSON.parse(content);
  return cases;
}

export function fixtureBytes(hex) {
  return hexToUint8Array(hex);
}

export function bufferToHex(buffer) {
  const view = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  return Array.from(view, (b) => b.toString(16).padStart(2, '0')).join('');
}

export function arrayBufferFrom(bytes) {
  if (bytes.byteLength === 0) return new ArrayBuffer(0);
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
}
