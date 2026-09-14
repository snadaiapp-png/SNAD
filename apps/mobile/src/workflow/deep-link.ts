/**
 * R2-I Mobile Action/Decision Center — deep-link validation
 *
 * Mobile only ever follows HTTPS deep links. Anything else (custom
 * schemes, http://, malformed strings) is ignored — a non-HTTPS link
 * must never be handed to Linking.openURL or the navigation linker.
 */

const HTTPS_PREFIX = 'https://';

/**
 * Returns true only for well-formed absolute https:// URLs.
 */
export function isSafeDeepLink(url: unknown): url is string {
  if (typeof url !== 'string' || url.length === 0) return false;
  if (!url.toLowerCase().startsWith(HTTPS_PREFIX)) return false;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' && parsed.hostname.length > 0;
  } catch {
    return false;
  }
}

/**
 * Normalize a notification deep_link to a safe value (or null).
 */
export function safeNotificationDeepLink(deepLink: unknown): string | null {
  return isSafeDeepLink(deepLink) ? deepLink : null;
}
