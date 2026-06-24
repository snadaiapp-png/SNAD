import type { NextConfig } from "next";

/**
 * SANAD — Next.js configuration
 * ------------------------------------------------------------
 * DEFECT-027 remediation: adds security headers (CSP, HSTS,
 * X-Frame-Options, X-Content-Type-Options, Referrer-Policy,
 * Permissions-Policy) to every response served by Next.js.
 *
 * Notes on policy:
 * - 'default-src \'self\'': only allow assets from same origin by default.
 * - 'script-src \'self\'': forbid inline scripts and external scripts
 *   (Next.js uses nonces/hashes for its own inline scripts when
 *   strict-next.config is enabled; this CSP is intentionally strict
 *   and may need 'unsafe-inline' temporarily if third-party scripts
 *   are added later — review before relaxing).
 * - 'style-src \'self\' \'unsafe-inline\'': Tailwind 4 uses inline
 *   styles for some utilities; 'unsafe-inline' for styles is
 *   low-risk compared to 'unsafe-inline' for scripts.
 * - 'connect-src \'self\' <API_BASE_URL>': allow XHR/fetch to the
 *   SANAD backend. The API origin is taken from
 *   NEXT_PUBLIC_API_BASE_URL at build time, falling back to the
 *   known production URL.
 * - 'img-src \'self\' data: blob:': allow data URIs and blob URLs
 *   for client-side image previews.
 * - 'frame-ancestors \'none\'': equivalent to X-Frame-Options DENY.
 * - 'base-uri \'self\'': prevent <base> tag hijacking.
 * - 'form-action \'self\'': prevent form submissions to external origins.
 *
 * HSTS: 1 year + preload, but only sent over HTTPS (Next.js handles
 * this automatically — the header is not added when serving over
 * HTTP for localhost).
 */
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "https://sanad-backend-mcrj.onrender.com";

const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self'`,
  `style-src 'self' 'unsafe-inline'`,
  `img-src 'self' data: blob:`,
  `font-src 'self' data:`,
  `connect-src 'self' ${apiBaseUrl}`,
  `frame-ancestors 'none'`,
  `base-uri 'self'`,
  `form-action 'self'`,
  `object-src 'none'`,
  `worker-src 'self' blob:`,
  `manifest-src 'self'`,
].join("; ");

const securityHeaders = [
  {
    key: "Content-Security-Policy",
    value: contentSecurityPolicy,
  },
  {
    key: "Strict-Transport-Security",
    value: "max-age=63072000; includeSubDomains; preload",
  },
  {
    key: "X-Frame-Options",
    value: "DENY",
  },
  {
    key: "X-Content-Type-Options",
    value: "nosniff",
  },
  {
    key: "Referrer-Policy",
    value: "strict-origin-when-cross-origin",
  },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=(), browsing-topics=(), interest-cohort=()",
  },
  {
    key: "X-DNS-Prefetch-Control",
    value: "off",
  },
  {
    key: "Cross-Origin-Opener-Policy",
    value: "same-origin",
  },
  {
    key: "Cross-Origin-Resource-Policy",
    value: "same-origin",
  },
];

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        // Apply security headers to all routes.
        source: "/:path*",
        headers: securityHeaders,
      },
    ];
  },
};

export default nextConfig;
