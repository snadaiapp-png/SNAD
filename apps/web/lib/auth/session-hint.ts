/**
 * Non-sensitive browser hint used only to decide whether silent session
 * restoration is worth attempting. It never proves authentication.
 */
export const SESSION_HINT_COOKIE = "sanad_session_hint";
export const TENANT_SESSION_HINT_COOKIE_PREFIX = "sanad_tenant_session_hint_";

export type SessionHintScope = "default" | "tenant";

const TENANT_ID_PATTERN = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;

export function sessionHintCookie(
  scope: SessionHintScope = "default",
  tenantId?: string | null,
): string {
  if (scope !== "tenant") return SESSION_HINT_COOKIE;
  const normalized = tenantId?.trim().toLowerCase() ?? "";
  if (!TENANT_ID_PATTERN.test(normalized)) return "";
  return `${TENANT_SESSION_HINT_COOKIE_PREFIX}${normalized}`;
}

export function hasSessionHint(
  cookieHeader?: string,
  scope: SessionHintScope = "default",
  tenantId?: string | null,
): boolean {
  const source = cookieHeader ?? (typeof document === "undefined" ? "" : document.cookie);
  const cookie = sessionHintCookie(scope, tenantId);
  if (!cookie) return false;
  return source
    .split(";")
    .map((part) => part.trim())
    .some((part) => part === `${cookie}=1`);
}
