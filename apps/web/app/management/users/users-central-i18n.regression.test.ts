import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const webRoot = process.cwd();
const usersRoot = resolve(webRoot, "app/management/users");
const providerPath = resolve(webRoot, "lib/i18n/I18nProvider.tsx");
const centralNamespacePath = resolve(webRoot, "lib/i18n/users-l10n.ts");
const augmenterPath = resolve(usersRoot, "_client/users-i18n-augmenter.tsx");
const layoutPath = resolve(usersRoot, "layout.tsx");

function read(path: string) {
  return readFileSync(path, "utf8");
}

describe("Users module central i18n contract", () => {
  it("does not keep a route-local users dictionary or hardcoded COPY blocks", () => {
    expect(existsSync(resolve(usersRoot, "users-i18n.ts"))).toBe(false);

    const directoryPage = read(resolve(usersRoot, "page.tsx"));
    const detailPage = read(resolve(usersRoot, "[userId]/page.tsx"));
    expect(directoryPage).not.toContain("const COPY =");
    expect(detailPage).not.toContain("const COPY =");
    expect(directoryPage).toContain("const { t } = useI18n()");
    expect(detailPage).toContain("const { t } = useI18n()");
  });

  it("keeps the users namespace centralized", () => {
    expect(existsSync(centralNamespacePath)).toBe(true);
    const namespace = read(centralNamespacePath);
    expect(namespace).toContain('"management.users.title"');
    expect(namespace).toContain('"management.users.detail.title"');
    expect(namespace).toContain("usersDictionary");
  });

  it("keeps users translations out of the global client bundle", () => {
    const provider = read(providerPath);
    expect(provider).not.toContain('import { usersDictionary } from "./users-l10n"');
    expect(provider).not.toContain("const users = usersDictionary(locale)");
    expect(provider).not.toContain("users[key] ?? commercial[key] ?? base[key]");
  });

  it("augments i18n only inside /management/users", () => {
    expect(existsSync(augmenterPath)).toBe(true);
    expect(existsSync(layoutPath)).toBe(true);

    const augmenter = read(augmenterPath);
    expect(augmenter).toContain('import { usersDictionary } from "@/lib/i18n/users-l10n"');
    expect(augmenter).toContain("I18nContext.Provider");
    expect(augmenter).toContain("useI18n");

    const layout = read(layoutPath);
    expect(layout).toContain("UsersI18nAugmenter");
  });
});
