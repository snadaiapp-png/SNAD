import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

describe("Vercel deployment governance", () => {
  it("keeps main deployable and blocks exec branches from automatic deployment", () => {
    const configPath = resolve(process.cwd(), "vercel.json");
    const config = JSON.parse(readFileSync(configPath, "utf-8")) as {
      git?: { deploymentEnabled?: Record<string, boolean> | boolean };
    };

    expect(typeof config.git?.deploymentEnabled).toBe("object");
    const rules = config.git?.deploymentEnabled as Record<string, boolean>;

    expect(rules.main).toBe(true);
    expect(rules["exec/*"]).toBe(false);
  });
});
