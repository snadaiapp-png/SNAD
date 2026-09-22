/**
 * Production font/build stability regression.
 *
 * The application must not depend on next/font/google at build time because
 * that resolver failed across both Turbopack and webpack CI paths. Fonts are
 * loaded by the browser from the approved Google Fonts stylesheet, while CSS
 * tokens keep deterministic local fallbacks.
 */
import { readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";

const APP_ROOT = resolve(__dirname);

describe("web production font/build contract", () => {
  it("removes build-time Google font resolution and keeps the default build path", () => {
    const packageJson = JSON.parse(
      readFileSync(resolve(APP_ROOT, "../package.json"), "utf8"),
    ) as { scripts?: Record<string, string> };
    const layout = readFileSync(resolve(APP_ROOT, "layout.tsx"), "utf8");
    const tokens = readFileSync(resolve(APP_ROOT, "snad-tokens.css"), "utf8");

    expect(packageJson.scripts?.build).toBe("next build");
    expect(layout).not.toContain('next/font/google');
    expect(layout).toContain("https://fonts.googleapis.com/css2?");
    expect(layout).toContain("Noto+Sans+Arabic");
    expect(layout).toContain("Noto+Sans");
    expect(tokens).toContain('--snad-font-arabic: "Noto Sans Arabic"');
    expect(tokens).toContain('--snad-font-latin:  "Noto Sans"');
    expect(tokens).not.toContain("var(--font-snad-arabic)");
    expect(tokens).not.toContain("var(--font-snad-latin)");
  });
});
