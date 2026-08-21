/**
 * G8 — HMAC lookup token parity (G8-03 §34–§35, G8-ADR-004).
 *
 * The pure-TS HMAC-SHA256 must match javax.crypto (backend) on the shared
 * token vector, be deterministic for the same key, and NOT match with a
 * different key (tenant-bound).
 */

import * as fs from 'fs';
import * as path from 'path';
import { hmacSha256Hex } from '../caller/hmac';

const VECTORS_PATH = path.join(
  process.cwd(),
  '../../docs/crm/g8/caller-phone-normalization-vectors.json'
);

describe('G8 HMAC lookup token', () => {
  test('matches the shared backend token vector', () => {
    const vector = JSON.parse(fs.readFileSync(VECTORS_PATH, 'utf-8')).tokenVector;
    const tenantDatasetKey = hmacSha256Hex(vector.masterKey, vector.tenantId);
    const lookupToken = hmacSha256Hex(tenantDatasetKey, vector.message);
    expect(tenantDatasetKey).toBe(vector.tenantDatasetKey);
    expect(lookupToken).toBe(vector.lookupToken);
  });

  test('is deterministic for the same key', () => {
    expect(hmacSha256Hex('k', '+966541234567')).toBe(hmacSha256Hex('k', '+966541234567'));
  });

  test('changes with the key (tenant-bound)', () => {
    const tokenA = hmacSha256Hex('tenant-a-key', '+966541234567');
    const tokenB = hmacSha256Hex('tenant-b-key', '+966541234567');
    expect(tokenA).not.toBe(tokenB);
  });

  test('changes with the number', () => {
    expect(hmacSha256Hex('k', '+966541234567')).not.toBe(hmacSha256Hex('k', '+966541234568'));
  });

  test('is 64 lower-case hex characters', () => {
    expect(hmacSha256Hex('k', '+966541234567')).toMatch(/^[0-9a-f]{64}$/);
  });

  test('sha256 core sanity (empty string is the well-known hash)', () => {
    // Exposed through HMAC with an empty key + empty message.
    expect(hmacSha256Hex('', '')).toBe('b613679a0814d9ec772f95d778c35fc5ff1697c493715653c6c712144292c5ad');
  });
});
