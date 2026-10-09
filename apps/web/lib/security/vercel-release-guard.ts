/**
 * Defense in depth for Vercel production builds. Authorization of an actual
 * production release must happen upstream, before the deployment is created.
 * This check never authorizes a release or overrides the Vercel target.
 */
export function assertVercelProductionRef(
  targetEnvironment: string | undefined,
  gitRef: string | undefined,
): void {
  const target = (targetEnvironment ?? "").trim().toLowerCase();
  const branch = (gitRef ?? "").trim();
  if ((target === "production" || target === "prod") && branch !== "main") {
    throw new Error(
      `[SNAD_RELEASE_GUARD] Refusing Vercel production build from non-main Git ref: ${branch || "<missing>"}`,
    );
  }
}
