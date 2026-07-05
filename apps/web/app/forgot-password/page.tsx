import { redirect } from "next/navigation";

/**
 * Backward-compatible redirect from the legacy /forgot-password route
 * to the canonical /auth/forgot-password route.
 *
 * Any old bookmarks, emails, or links pointing to /forgot-password will
 * seamlessly forward to the new location with HTTP 308 (permanent redirect),
 * preserving SEO and link integrity.
 */
export default function LegacyForgotPasswordPage(): never {
  redirect("/auth/forgot-password");
}
