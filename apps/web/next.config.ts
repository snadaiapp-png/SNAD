import type { NextConfig } from "next";

const vercelTargetEnvironment = (
  process.env.VERCEL_TARGET_ENV ||
  process.env.VERCEL_ENV ||
  ""
).toLowerCase();
const vercelGitCommitRef = (process.env.VERCEL_GIT_COMMIT_REF || "").trim();

if (
  (vercelTargetEnvironment === "production" || vercelTargetEnvironment === "prod") &&
  vercelGitCommitRef !== "" &&
  vercelGitCommitRef !== "main"
) {
  throw new Error(
    `[SNAD_RELEASE_GUARD] Refusing Vercel production build from non-main Git ref: ${vercelGitCommitRef}`,
  );
}

const securityHeaders = [
  {
    key: "Content-Security-Policy",
    value: "base-uri 'self'; frame-ancestors 'none'; object-src 'none'; form-action 'self'; upgrade-insecure-requests",
  },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  },
  { key: "Access-Control-Allow-Origin", value: "https://snad-app.vercel.app" },
  { key: "Vary", value: "Origin" },
];

const nextConfig: NextConfig = {
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
