import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const authDir = join(process.cwd(), "components", "auth");

function read(name: string): string {
  return readFileSync(join(authDir, name), "utf8");
}

describe("Login v2 responsive control contract", () => {
  it("defines dynamic viewport, 48px inputs, and 44px password target with logical properties", () => {
    const css = read("auth-login-v2.module.css");

    expect(css).toContain("min-block-size: 100dvh");
    expect(css).toContain("min-block-size: 48px");
    expect(css).toContain("min-inline-size: 44px");
    expect(css).toContain("min-block-size: 44px");
    expect(css).not.toMatch(/\bleft\s*:/);
    expect(css).not.toMatch(/\bright\s*:/);
  });

  it("wires the Login v2 sizing layer into the login screen and form", () => {
    const form = read("login-form.tsx");
    const screen = read("login-screen.tsx");

    expect(form).toContain('import v2Styles from "./auth-login-v2.module.css"');
    expect(form).toContain("v2Styles.authInput");
    expect(form).toContain("v2Styles.passwordToggle");
    expect(form).toContain("v2Styles.capsLockStatus");
    expect(screen).toContain('import v2Styles from "./auth-login-v2.module.css"');
    expect(screen).toContain("v2Styles.loginPanel");
  });
});
