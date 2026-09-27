import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const webRoot = process.cwd();
const usersRoot = resolve(webRoot, "app/management/users");
const providerPath = resolve(webRoot, "lib/i18n/I18nProvider.tsx");
const centralNamespacePath = resolve(webRoot, "lib/i18n/users-l10n.ts");

function read(path: string) {
  return readFileSync(path, "utf8");
}

describe("Users module central i18n contract", () => {
  it("does not keep a route-local users dictionary", () => {
    expect(existsSync(resolve(usersRoot, "users-i18n.ts"))).toBe(false);
  });

  it("does not hardcode a local COPY dictionary in user detail", () => {
    const detailPage = read(resolve(usersRoot, "[userId]/page.tsx"));
    expect(detailPage).not.toContain("const COPY =");
    expect(detailPage).toContain("const { t } = useI18n()");
  });

  it("composes the users namespace through the central I18nProvider", () => {
    expect(existsSync(centralNamespacePath)).toBe(true);
    const namespace = read(centralNamespacePath);
    expect(namespace).toContain('"management.users.title"');
    expect(namespace).toContain('"management.users.detail.title"');
    expect(namespace).toContain("usersDictionary");

    const provider = read(providerPath);
    expect(provider).toContain('import { usersDictionary } from "./users-l10n"');
    expect(provider).toContain("const users = usersDictionary(locale)");
    expect(provider).toContain("users[key] ?? commercial[key] ?? base[key]");
  });
});
