import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

describe("Vercel deployment governance", () => {
  it("keeps governed branches off Git auto-deploy", () => {
    const configPath = resolve(process.cwd(), "vercel.json");
    const config = JSON.parse(readFileSync(configPath, "utf-8")) as {
      git?: { deploymentEnabled?: Record<string, boolean> | boolean };
    };

    expect(typeof config.git?.deploymentEnabled).toBe("object");
    const rules = config.git?.deploymentEnabled as Record<string, boolean>;

    expect(rules.main).toBe(false);
    expect(rules["exec/*"]).toBe(false);
    expect(rules["r0c13/*"]).toBe(false);
  });
});
