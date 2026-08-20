/**
 * G8 — NORMALIZATION_PARITY gate (G8-03 §50, §69).
 *
 * The mobile normalizer must produce IDENTICAL output to the backend
 * PhoneNumberNormalizer for every shared vector in
 * docs/crm/g8/caller-phone-normalization-vectors.json.
 */

import * as fs from 'fs';
import * as path from 'path';
import { normalizePhone } from '../caller/normalizer';

const VECTORS_PATH = path.join(
  process.cwd(),
  '../../docs/crm/g8/caller-phone-normalization-vectors.json'
);

interface Vector {
  input: string | null;
  countryHint: string | null;
  expected: string | null;
}

function loadVectors(): Vector[] {
  const doc = JSON.parse(fs.readFileSync(VECTORS_PATH, 'utf-8'));
  return doc.normalization as Vector[];
}

describe('G8 NORMALIZATION_PARITY (mobile mirrors backend)', () => {
  test('shared vectors file exists and is non-empty', () => {
    const vectors = loadVectors();
    expect(vectors.length).toBeGreaterThan(10);
  });

  test('mobile output matches every shared vector', () => {
    for (const vector of loadVectors()) {
      const actual = normalizePhone(vector.input, vector.countryHint);
      expect(actual).toBe(vector.expected);
    }
  });

  test('golden Saudi forms', () => {
    expect(normalizePhone('0541234567', 'SA')).toBe('+966541234567');
    expect(normalizePhone('541234567', 'SA')).toBe('+966541234567');
    expect(normalizePhone('966541234567', 'SA')).toBe('+966541234567');
    expect(normalizePhone('+966541234567', 'SA')).toBe('+966541234567');
    expect(normalizePhone('00966541234567', 'SA')).toBe('+966541234567');
  });

  test('invalid and empty inputs are rejected', () => {
    expect(normalizePhone('not-a-number', 'SA')).toBeNull();
    expect(normalizePhone('', 'SA')).toBeNull();
    expect(normalizePhone(null, 'SA')).toBeNull();
    expect(normalizePhone('0541234567', 'GB')).toBeNull();
  });
});
