// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { Badge } from "../Badge";

describe("SDS Badge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders children inside a span", () => {
    render(<Badge>Active</Badge>);
    const badge = screen.getByText("Active");
    expect(badge.tagName).toBe("SPAN");
  });

  it.each([
    ["default", "Default"],
    ["success", "Success"],
    ["warning", "Warning"],
    ["error", "Error"],
    ["info", "Info"],
    ["accent", "Accent"],
  ] as const)("applies the %s variant without error", (variant, label) => {
    const { container } = render(<Badge variant={variant}>{label}</Badge>);
    const span = container.querySelector("span");
    expect(span).not.toBeNull();
    expect(span?.className.length ?? 0).toBeGreaterThan(0);
  });

  it.each([
    ["sm", "Small"],
    ["md", "Medium"],
  ] as const)("applies the %s size without error", (size, label) => {
    const { container } = render(<Badge size={size}>{label}</Badge>);
    expect(container.querySelector("span")).not.toBeNull();
  });

  it("renders a status dot when withDot=true", () => {
    const { container } = render(
      <Badge variant="success" withDot>
        Active
      </Badge>,
    );
    // The dot is a <span> with aria-hidden="true" inside the badge.
    const dot = container.querySelector('span[aria-hidden="true"]');
    expect(dot).not.toBeNull();
  });

  it("does not render a dot when withDot is not set", () => {
    const { container } = render(<Badge>Active</Badge>);
    const dot = container.querySelector('span[aria-hidden="true"]');
    expect(dot).toBeNull();
  });

  it("forwards arbitrary HTML attributes", () => {
    render(
      <Badge data-testid="my-badge" title="Status: active">
        Active
      </Badge>,
    );
    const badge = screen.getByTestId("my-badge");
    expect(badge).toHaveAttribute("title", "Status: active");
  });
});
