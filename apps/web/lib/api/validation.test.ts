/**
 * Tests for the validation module.
 *
 * Covers: UUID validation, email validation + normalization,
 * display name validation + normalization, edge cases.
 */

import { describe, it, expect } from "vitest";
import {
  isValidUuid,
  requireValidUuid,
  isValidEmail,
  requireValidEmail,
  normalizeEmail,
  MAX_EMAIL_LENGTH,
  normalizeDisplayName,
  requireValidDisplayName,
  MAX_DISPLAY_NAME_LENGTH,
} from "./validation";
import { ApiConfigurationError } from "./errors";

describe("validation — UUID", () => {
  describe("isValidUuid", () => {
    it("accepts a standard lowercase UUID", () => {
      expect(isValidUuid("11111111-1111-1111-1111-111111111111")).toBe(true);
    });
    it("accepts a standard uppercase UUID", () => {
      expect(isValidUuid("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")).toBe(true);
    });
    it("accepts mixed case UUID", () => {
      expect(isValidUuid("AbCdEf01-1234-5678-9AbC-DeF012345678")).toBe(true);
    });
    it("rejects empty string", () => {
      expect(isValidUuid("")).toBe(false);
    });
    it("rejects non-string", () => {
      expect(isValidUuid(null)).toBe(false);
      expect(isValidUuid(undefined)).toBe(false);
      expect(isValidUuid(123)).toBe(false);
    });
    it("rejects UUID without dashes", () => {
      expect(isValidUuid("11111111111111111111111111111111")).toBe(false);
    });
    it("rejects UUID with wrong segment lengths", () => {
      expect(isValidUuid("11111111-1111-1111-111-111111111111")).toBe(false);
    });
    it("rejects UUID with non-hex characters", () => {
      expect(isValidUuid("gggggggg-1111-1111-1111-111111111111")).toBe(false);
    });
    it("rejects random string", () => {
      expect(isValidUuid("not-a-uuid")).toBe(false);
    });
  });

  describe("requireValidUuid", () => {
    it("returns the UUID when valid", () => {
      const uuid = "11111111-1111-1111-1111-111111111111";
      expect(requireValidUuid(uuid, "tenantId")).toBe(uuid);
    });
    it("throws ApiConfigurationError when invalid", () => {
      expect(() => requireValidUuid("bad", "tenantId")).toThrow(ApiConfigurationError);
    });
    it("error message includes field name", () => {
      try {
        requireValidUuid("bad", "organizationId");
        expect.fail("should have thrown");
      } catch (err) {
        if (err instanceof ApiConfigurationError) {
          expect(err.message).toContain("organizationId");
        }
      }
    });
    it("error message is in Arabic", () => {
      try {
        requireValidUuid("bad", "userId");
        expect.fail("should have thrown");
      } catch (err) {
        if (err instanceof ApiConfigurationError) {
          expect(err.message).toMatch(/[\u0600-\u06FF]/);
        }
      }
    });
  });
});

describe("validation — email", () => {
  describe("isValidEmail", () => {
    it("accepts standard email", () => {
      expect(isValidEmail("user@example.com")).toBe(true);
    });
    it("accepts email with subdomain", () => {
      expect(isValidEmail("user@mail.example.com")).toBe(true);
    });
    it("accepts email with plus sign", () => {
      expect(isValidEmail("user+tag@example.com")).toBe(true);
    });
    it("rejects email without @", () => {
      expect(isValidEmail("userexample.com")).toBe(false);
    });
    it("rejects email with multiple @", () => {
      expect(isValidEmail("user@@example.com")).toBe(false);
    });
    it("rejects email with empty local part", () => {
      expect(isValidEmail("@example.com")).toBe(false);
    });
    it("rejects email with empty domain", () => {
      expect(isValidEmail("user@")).toBe(false);
    });
    it("rejects email with no dot in domain", () => {
      expect(isValidEmail("user@example")).toBe(false);
    });
    it("rejects email with whitespace", () => {
      expect(isValidEmail("user @example.com")).toBe(false);
    });
    it("rejects non-string", () => {
      expect(isValidEmail(null)).toBe(false);
    });
  });

  describe("normalizeEmail", () => {
    it("trims whitespace", () => {
      expect(normalizeEmail("  user@example.com  ")).toBe("user@example.com");
    });
    it("converts to lowercase", () => {
      expect(normalizeEmail("USER@EXAMPLE.COM")).toBe("user@example.com");
    });
  });

  describe("requireValidEmail", () => {
    it("returns normalized email when valid", () => {
      expect(requireValidEmail("  User@Example.COM  ")).toBe("user@example.com");
    });
    it("throws when email is empty", () => {
      expect(() => requireValidEmail("")).toThrow(ApiConfigurationError);
      expect(() => requireValidEmail("   ")).toThrow(ApiConfigurationError);
    });
    it("throws when email is too long", () => {
      const longEmail = "a".repeat(MAX_EMAIL_LENGTH) + "@example.com";
      expect(() => requireValidEmail(longEmail)).toThrow(ApiConfigurationError);
    });
    it("throws when email format is invalid", () => {
      expect(() => requireValidEmail("not-an-email")).toThrow(ApiConfigurationError);
    });
    it("accepts email within length and local-part limits", () => {
      const domain = "@example.com";
      const localLength = Math.min(64, MAX_EMAIL_LENGTH - domain.length);
      const email = "a".repeat(localLength) + domain;
      expect(() => requireValidEmail(email)).not.toThrow();
    });
  });
});

describe("validation — displayName", () => {
  describe("normalizeDisplayName", () => {
    it("trims whitespace", () => {
      expect(normalizeDisplayName("  User Name  ")).toBe("User Name");
    });
    it("returns null for empty string", () => {
      expect(normalizeDisplayName("")).toBeNull();
    });
    it("returns null for whitespace-only string", () => {
      expect(normalizeDisplayName("   ")).toBeNull();
    });
    it("returns null for null input", () => {
      expect(normalizeDisplayName(null)).toBeNull();
    });
    it("returns null for undefined input", () => {
      expect(normalizeDisplayName(undefined)).toBeNull();
    });
  });

  describe("requireValidDisplayName", () => {
    it("returns trimmed name when valid", () => {
      expect(requireValidDisplayName("  User Name  ")).toBe("User Name");
    });
    it("returns null for empty input", () => {
      expect(requireValidDisplayName("")).toBeNull();
      expect(requireValidDisplayName(null)).toBeNull();
    });
    it("throws when name exceeds max length", () => {
      const longName = "a".repeat(MAX_DISPLAY_NAME_LENGTH + 1);
      expect(() => requireValidDisplayName(longName)).toThrow(ApiConfigurationError);
    });
    it("accepts name at exactly max length boundary", () => {
      const name = "a".repeat(MAX_DISPLAY_NAME_LENGTH);
      expect(() => requireValidDisplayName(name)).not.toThrow();
    });
  });
});

describe("validation — constants", () => {
  it("MAX_EMAIL_LENGTH is 255 (matches backend @Size)", () => {
    expect(MAX_EMAIL_LENGTH).toBe(255);
  });
  it("MAX_DISPLAY_NAME_LENGTH is 200 (matches backend @Size)", () => {
    expect(MAX_DISPLAY_NAME_LENGTH).toBe(200);
  });
});
