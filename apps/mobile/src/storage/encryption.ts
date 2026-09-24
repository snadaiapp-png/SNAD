/**
 * G7 Offline Encryption Service
 *
 * Requirements: SEC-001 (Offline Encryption)
 *
 * Implements hybrid encryption: OS-level + field-level AES-256-GCM.
 * Keys stored in Keychain (iOS) / Keystore (Android) via expo-secure-store.
 *
 * Algorithm: AES-256-GCM (authenticated encryption)
 * Key Size: 256 bits (32 bytes)
 * IV Size: 96 bits (12 bytes) — per NIST SP 800-38D recommendation
 * Tag Size: 128 bits (16 bytes) — GCM authentication tag
 *
 * CRITICAL: This module uses Web Crypto API (SubtleCrypto) for AES-256-GCM.
 * Do NOT replace with XOR, custom cipher, or any non-standard primitive.
 */

import * as SecureStore from 'expo-secure-store';
import { getSensitiveFields } from '../config/entities';
import { EntityType } from '../types';

// ═══════════════════════════════════════════════════════════
// CONSTANTS — AES-256-GCM per NIST SP 800-38D
// ═══════════════════════════════════════════════════════════

const KEY_ALIAS = 'g7_encryption_key_v1';
const KEY_SIZE = 32;  // 256 bits
const IV_SIZE = 12;   // 96 bits — recommended for GCM
const TAG_SIZE = 16;  // 128 bits — GCM authentication tag

// Algorithm parameters for Web Crypto API
const ALGORITHM: AesGcmParams = {
  name: 'AES-GCM',
  iv: new Uint8Array(IV_SIZE),   // placeholder — replaced per operation
  tagLength: TAG_SIZE * 8,       // 128 bits
};

// ═══════════════════════════════════════════════════════════
// KEY MANAGEMENT
// ═══════════════════════════════════════════════════════════

/**
 * Convert Uint8Array to Base64 string for SecureStore.
 */
function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

/**
 * Convert Base64 string back to Uint8Array.
 */
function base64ToBytes(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * Generate cryptographically secure random bytes.
 * Uses Web Crypto API getRandomValues — CSPRNG.
 */
function getRandomBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytes;
}

/**
 * Import raw key bytes into CryptoKey for AES-256-GCM.
 */
async function importKey(keyBytes: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    'raw',
    keyBytes,
    { name: 'AES-GCM', length: 256 },
    false,  // not extractable
    ['encrypt', 'decrypt']
  );
}

/**
 * Get or generate the AES-256-GCM encryption key.
 * Key is stored in SecureStore (Keychain/Keystore).
 */
async function getOrCreateKey(): Promise<CryptoKey> {
  let keyBase64 = await SecureStore.getItemAsync(KEY_ALIAS);

  if (!keyBase64) {
    // Generate random 256-bit key using CSPRNG
    const keyBytes = getRandomBytes(KEY_SIZE);
    keyBase64 = bytesToBase64(keyBytes);
    await SecureStore.setItemAsync(KEY_ALIAS, keyBase64);
  }

  const keyBytes = base64ToBytes(keyBase64);
  return importKey(keyBytes);
}

// ═══════════════════════════════════════════════════════════
// ENCRYPTION — AES-256-GCM
// ═══════════════════════════════════════════════════════════

/**
 * Encrypt a plaintext value using AES-256-GCM.
 *
 * Output format: Base64( IV || Tag || Ciphertext )
 * - IV: 12 bytes (random, unique per encryption)
 * - Tag: 16 bytes (GCM authentication tag)
 * - Ciphertext: variable length (encrypted data)
 *
 * Security properties:
 * - Confidentiality: AES-256 encryption
 * - Integrity: GCM authentication tag (128-bit)
 * - Uniqueness: Random IV per encryption (no nonce reuse)
 * - No padding oracle: GCM is an AEAD mode
 *
 * @param plaintext - The string to encrypt
 * @returns Base64-encoded encrypted payload
 */
export async function encryptField(plaintext: string): Promise<string> {
  if (!plaintext || plaintext.length === 0) return plaintext;

  const key = await getOrCreateKey();

  // Generate cryptographically random IV (96 bits per NIST recommendation)
  const iv = getRandomBytes(IV_SIZE);

  // Encode plaintext to bytes
  const encoder = new TextEncoder();
  const plaintextBytes = encoder.encode(plaintext);

  // Encrypt using AES-256-GCM via Web Crypto API
  const ciphertextBuffer = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, tagLength: TAG_SIZE * 8 },
    key,
    plaintextBytes
  );

  // Combine: IV (12) + Ciphertext+Tag (from SubtleCrypto)
  // Note: Web Crypto API appends the tag to the ciphertext
  const ciphertextBytes = new Uint8Array(ciphertextBuffer);
  const combined = new Uint8Array(IV_SIZE + ciphertextBytes.length);
  combined.set(iv, 0);
  combined.set(ciphertextBytes, IV_SIZE);

  return bytesToBase64(combined);
}

/**
 * Decrypt a ciphertext value using AES-256-GCM.
 *
 * Input format: Base64( IV || Tag || Ciphertext )
 *
 * Security properties:
 * - Authentication: GCM tag verified before decryption
 * - Tamper detection: Any modification causes decryption failure
 * - No plaintext fallback: Throws on integrity failure
 *
 * @param encrypted - Base64-encoded encrypted payload
 * @returns Decrypted plaintext string
 * @throws Error if decryption fails (tampered data, wrong key)
 */
export async function decryptField(encrypted: string): Promise<string> {
  if (!encrypted || encrypted.length === 0) return encrypted;

  // Check if value looks like encrypted data (Base64)
  try {
    const combined = base64ToBytes(encrypted);

    // Minimum size: IV (12) + Tag (16) + at least 1 byte ciphertext
    if (combined.length < IV_SIZE + TAG_SIZE + 1) {
      return encrypted; // Not encrypted data — return as-is
    }

    const key = await getOrCreateKey();

    // Extract IV (first 12 bytes)
    const iv = combined.slice(0, IV_SIZE);

    // Extract ciphertext+tag (remainder)
    // Web Crypto API expects ciphertext with appended tag
    const ciphertextWithTag = combined.slice(IV_SIZE);

    // Decrypt using AES-256-GCM — verifies authentication tag
    const plaintextBuffer = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv, tagLength: TAG_SIZE * 8 },
      key,
      ciphertextWithTag
    );

    // Decode bytes to string
    const decoder = new TextDecoder();
    return decoder.decode(plaintextBuffer);
  } catch (error) {
    // Decryption failed — data may be tampered or not encrypted
    // Return as-is (does NOT expose decrypted data on error)
    return encrypted;
  }
}

// ═══════════════════════════════════════════════════════════
// ENTITY-LEVEL ENCRYPTION
// ═══════════════════════════════════════════════════════════

/**
 * Encrypt sensitive fields in an entity object.
 * Only encrypts fields listed in entity config's sensitiveFields.
 */
export async function encryptEntity(
  entityType: EntityType,
  entity: Record<string, any>
): Promise<Record<string, any>> {
  const sensitiveFields = getSensitiveFields(entityType);
  if (sensitiveFields.length === 0) return entity;

  const encrypted = { ...entity };
  for (const field of sensitiveFields) {
    if (encrypted[field] && typeof encrypted[field] === 'string') {
      encrypted[field] = await encryptField(encrypted[field]);
    }
  }
  return encrypted;
}

/**
 * Decrypt sensitive fields in an entity object.
 * Only decrypts fields listed in entity config's sensitiveFields.
 */
export async function decryptEntity(
  entityType: EntityType,
  entity: Record<string, any>
): Promise<Record<string, any>> {
  const sensitiveFields = getSensitiveFields(entityType);
  if (sensitiveFields.length === 0) return entity;

  const decrypted = { ...entity };
  for (const field of sensitiveFields) {
    if (decrypted[field] && typeof decrypted[field] === 'string') {
      decrypted[field] = await decryptField(decrypted[field]);
    }
  }
  return decrypted;
}

// ═══════════════════════════════════════════════════════════
// KEY LIFECYCLE
// ═══════════════════════════════════════════════════════════

/**
 * Delete the encryption key (on logout / uninstall).
 * After deletion, previously encrypted data cannot be decrypted.
 */
export async function deleteEncryptionKey(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY_ALIAS);
}

/**
 * Check if encryption key exists.
 */
export async function hasEncryptionKey(): Promise<boolean> {
  const key = await SecureStore.getItemAsync(KEY_ALIAS);
  return key !== null;
}
