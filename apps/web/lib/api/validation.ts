/**
 * Input validation for the SANAD API client.
 *
 * Provides reusable validators for UUIDs, emails, and display names.
 * These validators throw `ApiConfigurationError`-style errors with
 * Arabic-safe messages so callers can surface them to users.
 *
 * All validators are pure functions — no side effects, no logging.
 */

import { ApiConfigurationError } from "./errors";

// ---------------------------------------------------------------------------
// UUID validation
// ---------------------------------------------------------------------------

/**
 * RFC 4122 UUID regex (case-insensitive).
 * Accepts: 8-4-4-4-12 hex digits with optional urn:uuid: prefix.
 */
const UUID_REGEX =
  /^(urn:uuid:)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

/**
 * Validate that a string is a well-formed UUID.
 *
 * @param value - The string to validate.
 * @returns `true` if valid, `false` otherwise.
 */
export function isValidUuid(value: unknown): value is string {
  if (typeof value !== "string") return false;
  return UUID_REGEX.test(value);
}

/**
 * Validate a UUID and throw `ApiConfigurationError` if invalid.
 *
 * @param value - The UUID string to validate.
 * @param fieldName - Field name for the error message (e.g. "tenantId").
 * @returns The validated UUID string (unchanged).
 * @throws ApiConfigurationError if the value is not a valid UUID.
 */
export function requireValidUuid(value: string, fieldName: string): string {
  if (!isValidUuid(value)) {
    throw new ApiConfigurationError(
      `قيمة غير صالحة لـ ${fieldName}: يجب أن تكون UUID صحيح`
    );
  }
  return value;
}

// ---------------------------------------------------------------------------
// Email validation
// ---------------------------------------------------------------------------

/**
 * Practical email validation — NOT a complex regex.
 *
 * Rules:
 * 1. Must contain exactly one `@`.
 * 2. Local part (before `@`) must be non-empty and ≤ 64 chars.
 * 3. Domain part (after `@`) must be non-empty, ≤ 253 chars, and
 *    contain at least one `.` with a non-empty TLD.
 * 4. No whitespace anywhere.
 *
 * This is intentionally simpler than RFC 5322 — it catches typos
 * without rejecting valid edge-case addresses.
 *
 * @param value - The email string to validate.
 * @returns `true` if valid, `false` otherwise.
 */
export function isValidEmail(value: unknown): value is string {
  if (typeof value !== "string") return false;
  if (/\s/.test(value)) return false;
  const atIndex = value.indexOf("@");
  const lastAtIndex = value.lastIndexOf("@");
  if (atIndex === -1 || atIndex !== lastAtIndex) return false;
  const local = value.slice(0, atIndex);
  const domain = value.slice(atIndex + 1);
  if (local.length === 0 || local.length > 64) return false;
  if (domain.length === 0 || domain.length > 253) return false;
  const dotIndex = domain.lastIndexOf(".");
  if (dotIndex === -1) return false;
  const tld = domain.slice(dotIndex + 1);
  if (tld.length === 0) return false;
  if (domain.startsWith(".") || domain.endsWith(".")) return false;
  if (domain.startsWith("-") || domain.endsWith("-")) return false;
  if (local.startsWith(".") || local.endsWith(".")) return false;
  return true;
}

/**
 * Maximum email length (matches backend @Size(max=255)).
 */
export const MAX_EMAIL_LENGTH = 255;

/**
 * Normalize an email: trim whitespace and convert to lowercase.
 *
 * @param email - The raw email string.
 * @returns Normalized email (trimmed + lowercased).
 */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

/**
 * Validate, normalize, and confirm an email meets length constraints.
 *
 * @param email - The raw email string.
 * @returns The normalized email (trimmed + lowercased).
 * @throws ApiConfigurationError if the email is empty, too long, or invalid.
 */
export function requireValidEmail(email: string): string {
  if (typeof email !== "string" || email.trim().length === 0) {
    throw new ApiConfigurationError("البريد الإلكتروني إلزامي");
  }
  const normalized = normalizeEmail(email);
  if (normalized.length > MAX_EMAIL_LENGTH) {
    throw new ApiConfigurationError(
      `البريد الإلكتروني يجب ألا يتجاوز ${MAX_EMAIL_LENGTH} حرفًا`
    );
  }
  if (!isValidEmail(normalized)) {
    throw new ApiConfigurationError("صيغة البريد الإلكتروني غير صالحة");
  }
  return normalized;
}

// ---------------------------------------------------------------------------
// Display name validation
// ---------------------------------------------------------------------------

/**
 * Maximum display name length (matches backend @Size(max=200)).
 */
export const MAX_DISPLAY_NAME_LENGTH = 200;

/**
 * Normalize a display name: trim whitespace. Empty string → null.
 *
 * @param displayName - The raw display name string (or null/undefined).
 * @returns Trimmed display name, or `null` if empty/null/undefined.
 */
export function normalizeDisplayName(
  displayName: string | null | undefined
): string | null {
  if (displayName == null) return null;
  const trimmed = displayName.trim();
  return trimmed.length === 0 ? null : trimmed;
}

/**
 * Validate a display name. Empty/null is allowed (optional field).
 *
 * @param displayName - The raw display name string (or null/undefined).
 * @returns The normalized display name (trimmed, or null if empty).
 * @throws ApiConfigurationError if the display name exceeds the max length.
 */
export function requireValidDisplayName(
  displayName: string | null | undefined
): string | null {
  const normalized = normalizeDisplayName(displayName);
  if (normalized !== null && normalized.length > MAX_DISPLAY_NAME_LENGTH) {
    throw new ApiConfigurationError(
      `اسم العرض يجب ألا يتجاوز ${MAX_DISPLAY_NAME_LENGTH} حرفًا`
    );
  }
  return normalized;
}
