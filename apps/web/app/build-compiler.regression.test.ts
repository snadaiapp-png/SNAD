/**
 * Web build compiler regression.
 *
 * Next 16 defaults to Turbopack. The current next/font/google resolver has
 * produced deterministic CI failures resolving
 * @vercel/turbopack-next/internal/font/google/font for the SNAD Noto font
 * loaders. Production/CI builds are therefore pinned to the supported webpack
 * compiler path until the Turbopack font resolver is explicitly re-certified.
 */
import { readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";

describe("web production build compiler", () => {
  it("pins next build to webpack while next/font/google is in use", () => {
    const packageJson = JSON.parse(
      readFileSync(resolve(__dirname, "../package.json"), "utf8"),
    ) as { scripts?: Record<string, string> };
    const layout = readFileSync(resolve(__dirname, "layout.tsx"), "utf8");

    expect(layout).toContain('from "next/font/google"');
    expect(packageJson.scripts?.build).toBe("next build --webpack");
  });
});
