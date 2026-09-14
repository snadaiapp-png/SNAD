/**
 * R2-I Mobile Action/Decision Center — API base configuration
 *
 * Single source of truth for the platform base URL used by the auth
 * and workflow clients. In Expo, the value is supplied through the
 * standard EXPO_PUBLIC_* environment mechanism (inlined by the Expo
 * CLI at bundle time); a plain http(s) URL is enforced and trailing
 * slashes are normalized.
 */

export const DEFAULT_API_BASE_URL = 'http://localhost:8080';

function readEnv(name: string): string | undefined {
  try {
    const raw = typeof process !== 'undefined' ? process.env?.[name] : undefined;
    if (typeof raw === 'string' && raw.trim().length > 0) return raw;
    return undefined;
  } catch {
    return undefined;
  }
}

/**
 * Resolve the platform base URL (no trailing slash, http/https only).
 * Falls back to the local development default when unset/invalid.
 */
export function getApiBaseUrl(): string {
  const raw = readEnv('EXPO_PUBLIC_API_BASE_URL') ?? readEnv('SANAD_API_BASE_URL');
  if (!raw) return DEFAULT_API_BASE_URL;
  const trimmed = raw.trim();
  if (!/^https?:\/\//i.test(trimmed)) return DEFAULT_API_BASE_URL;
  return trimmed.replace(/\/+$/, '');
}
