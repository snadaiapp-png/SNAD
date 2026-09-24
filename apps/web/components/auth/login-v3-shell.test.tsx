// @vitest-environment jsdom

import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

import { describe, expect, it } from "vitest";

describe("LoginV3Shell contract", () => {
  it("has a dedicated Login v3 shell module", () => {
    const shellPath = fileURLToPath(new URL("./login-v3-shell.tsx", import.meta.url));
    expect(existsSync(shellPath)).toBe(true);
  });
});
