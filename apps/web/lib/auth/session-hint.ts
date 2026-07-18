/**
 * Non-sensitive browser hint used only to decide whether silent session
 * restoration is worth attempting. It never proves authentication.
 */
export const SESSION_HINT_COOKIE = "sanad_session_hint";

export function hasSessionHint(cookieHeader?: string): boolean {
  const source = cookieHeader ?? (typeof document === "undefined" ? "" : document.cookie);
  return source
    .split(";")
    .map((part) => part.trim())
    .some((part) => part === `${SESSION_HINT_COOKIE}=1`);
}
