import { afterEach, describe, expect, it } from "vitest";
import { NextRequest } from "next/server";

import {
  CRM_ROOT_ENTRY_COOKIE,
  RELEASE_GUARD_HEADER,
  isUnauthorizedProductionSource,
  proxy,
} from "./proxy";

const ORIGINAL_VERCEL_TARGET_ENV = process.env.VERCEL_TARGET_ENV;
const ORIGINAL_VERCEL_ENV = process.env.VERCEL_ENV;
const ORIGINAL_VERCEL_GIT_COMMIT_REF = process.env.VERCEL_GIT_COMMIT_REF;

function restore(name: string, value: string | undefined) {
  if (value === undefined) delete process.env[name];
  else process.env[name] = value;
}

afterEach(() => {
  restore("VERCEL_TARGET_ENV", ORIGINAL_VERCEL_TARGET_ENV);
  restore("VERCEL_ENV", ORIGINAL_VERCEL_ENV);
  restore("VERCEL_GIT_COMMIT_REF", ORIGINAL_VERCEL_GIT_COMMIT_REF);
});

describe("SNAD Next.js proxy", () => {
  it("redirects CRM root before the authenticated SPA boots and preserves root intent", () => {
    delete process.env.VERCEL_TARGET_ENV;
    delete process.env.VERCEL_ENV;
    delete process.env.VERCEL_GIT_COMMIT_REF;

    const response = proxy(new NextRequest("http://localhost:3000/crm"));

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost:3000/crm/overview");
    expect(response.cookies.get(CRM_ROOT_ENTRY_COOKIE)?.value).toBe("1");
  });

  it("classifies a non-main Vercel production source as unauthorized", () => {
    expect(
      isUnauthorizedProductionSource({
        VERCEL_TARGET_ENV: "production",
        VERCEL_ENV: "production",
        VERCEL_GIT_COMMIT_REF: "exec/w2-t7-executive-partner-control-plane",
      }),
    ).toBe(true);
  });

  it("fails closed with HTTP 503 when production is sourced from a non-main Git ref", () => {
    process.env.VERCEL_TARGET_ENV = "production";
    process.env.VERCEL_ENV = "production";
    process.env.VERCEL_GIT_COMMIT_REF = "exec/w2-t7-executive-partner-control-plane";

    const response = proxy(new NextRequest("https://snad-app.vercel.app/hr/performance/goals"));

    expect(response.status).toBe(503);
    expect(response.headers.get(RELEASE_GUARD_HEADER)).toBe("blocked-non-main-production-source");
    expect(response.headers.get("cache-control")).toContain("no-store");
  });

  it("allows application routing when Vercel production is sourced from main", () => {
    process.env.VERCEL_TARGET_ENV = "production";
    process.env.VERCEL_ENV = "production";
    process.env.VERCEL_GIT_COMMIT_REF = "main";

    const response = proxy(new NextRequest("https://snad-app.vercel.app/hr/performance/goals"));

    expect(response.status).toBe(200);
    expect(response.headers.get(RELEASE_GUARD_HEADER)).toBeNull();
  });
});
