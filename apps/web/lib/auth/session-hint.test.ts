import { describe, expect, it } from "vitest";
import { hasSessionHint } from "./session-hint";

describe("session hint", () => {
  it("detects only the explicit non-sensitive marker", () => {
    expect(hasSessionHint("theme=dark; sanad_session_hint=1; locale=ar")).toBe(true);
    expect(hasSessionHint("sanad_session_hint=0")).toBe(false);
    expect(hasSessionHint("sanad_refresh=secret")).toBe(false);
    expect(hasSessionHint("")).toBe(false);
  });
});
