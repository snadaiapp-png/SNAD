/**
 * Non-sensitive browser hint used only to decide whether silent session
 * restoration is worth attempting. It never proves authentication.
 */
export const SESSION_HINT_COOKIE = "sanad_session_hint";
export const TENANT_SESSION_HINT_COOKIE = "sanad_tenant_session_hint";

export type SessionHintScope = "default" | "tenant";

export function sessionHintCookie(scope: SessionHintScope = "default"): string {
  return scope === "tenant" ? TENANT_SESSION_HINT_COOKIE : SESSION_HINT_COOKIE;
}

export function hasSessionHint(
  cookieHeader?: string,
  scope: SessionHintScope = "default",
): boolean {
  const source = cookieHeader ?? (typeof document === "undefined" ? "" : document.cookie);
  const cookie = sessionHintCookie(scope);
  return source
    .split(";")
    .map((part) => part.trim())
    .some((part) => part === `${cookie}=1`);
}
