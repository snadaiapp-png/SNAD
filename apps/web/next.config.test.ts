/**
 * SANAD — DEFECT-027: security headers test
 * ------------------------------------------------------------
 * Verifies that next.config.ts declares the expected security
 * headers (CSP, HSTS, X-Frame-Options, X-Content-Type-Options,
 * Referrer-Policy, Permissions-Policy, COOP, CORP) on every path.
 *
 * We invoke the async headers() function exported by next.config.ts
 * and assert on the returned shape. This is a static guarantee that
 * survives refactors.
 */

import { describe, it, expect } from "vitest";
import nextConfig from "./next.config";

interface HeaderEntry {
  key: string;
  value: string;
}

interface HeadersResultEntry {
  source: string;
  headers: HeaderEntry[];
}

type HeadersResult = HeadersResultEntry[];

// next.config exports an object whose headers() returns Promise<HeadersResult[]>;
// we treat it as an opaque callable to avoid coupling to Next's types.
type NextConfigWithHeaders = { headers: () => Promise<HeadersResult> };

function getConfig(): NextConfigWithHeaders {
  return nextConfig as unknown as NextConfigWithHeaders;
}

function getAllPathHeaders(result: HeadersResult): HeaderEntry[] {
  const entry = result.find((e) => e.source === "/:path*");
  if (!entry) {
    throw new Error("Expected an entry with source '/:path*' but none was found.");
  }
  return entry.headers;
}

describe("DEFECT-027: next.config.ts security headers", () => {
  it("declares an async headers() function returning security headers", async () => {
    const cfg = getConfig();
    expect(typeof cfg.headers).toBe("function");
    const result = await cfg.headers();
    expect(Array.isArray(result)).toBe(true);
    expect(result.length).toBeGreaterThan(0);

    const allPathsEntry = result.find((entry) => entry.source === "/:path*");
    expect(allPathsEntry).toBeDefined();
    expect(Array.isArray(allPathsEntry!.headers)).toBe(true);
  });

  it("includes a Content-Security-Policy header", async () => {
    const result = await getConfig().headers();
    const headers = getAllPathHeaders(result);
    const csp = headers.find((h) => h.key === "Content-Security-Policy");
    expect(csp).toBeDefined();
    expect(csp!.value).toContain("default-src 'self'");
    expect(csp!.value).toContain("frame-ancestors 'none'");
    expect(csp!.value).toContain("object-src 'none'");
    expect(csp!.value).toContain("connect-src 'self'");
  });

  it("includes a Strict-Transport-Security header with preload", async () => {
    const result = await getConfig().headers();
    const headers = getAllPathHeaders(result);
    const hsts = headers.find((h) => h.key === "Strict-Transport-Security");
    expect(hsts).toBeDefined();
    expect(hsts!.value).toContain("max-age=63072000");
    expect(hsts!.value).toContain("includeSubDomains");
    expect(hsts!.value).toContain("preload");
  });

  it("includes X-Frame-Options: DENY (clickjacking protection)", async () => {
    const result = await getConfig().headers();
    const headers = getAllPathHeaders(result);
    const xfo = headers.find((h) => h.key === "X-Frame-Options");
    expect(xfo).toBeDefined();
    expect(xfo!.value).toBe("DENY");
  });

  it("includes X-Content-Type-Options: nosniff", async () => {
    const result = await getConfig().headers();
    const headers = getAllPathHeaders(result);
    const xcto = headers.find((h) => h.key === "X-Content-Type-Options");
    expect(xcto).toBeDefined();
    expect(xcto!.value).toBe("nosniff");
  });

  it("includes a Referrer-Policy header", async () => {
    const result = await getConfig().headers();
    const headers = getAllPathHeaders(result);
    const rp = headers.find((h) => h.key === "Referrer-Policy");
    expect(rp).toBeDefined();
    expect(rp!.value).toBe("strict-origin-when-cross-origin");
  });

  it("includes a Permissions-Policy header that disables sensitive APIs", async () => {
    const result = await getConfig().headers();
    const headers = getAllPathHeaders(result);
    const pp = headers.find((h) => h.key === "Permissions-Policy");
    expect(pp).toBeDefined();
    expect(pp!.value).toContain("camera=()");
    expect(pp!.value).toContain("microphone=()");
    expect(pp!.value).toContain("geolocation=()");
  });

  it("includes Cross-Origin-Opener-Policy: same-origin", async () => {
    const result = await getConfig().headers();
    const headers = getAllPathHeaders(result);
    const coop = headers.find((h) => h.key === "Cross-Origin-Opener-Policy");
    expect(coop).toBeDefined();
    expect(coop!.value).toBe("same-origin");
  });

  it("includes Cross-Origin-Resource-Policy: same-origin", async () => {
    const result = await getConfig().headers();
    const headers = getAllPathHeaders(result);
    const corp = headers.find((h) => h.key === "Cross-Origin-Resource-Policy");
    expect(corp).toBeDefined();
    expect(corp!.value).toBe("same-origin");
  });
});
