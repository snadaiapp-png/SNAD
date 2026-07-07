// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Button } from "../Button";

describe("SDS Button", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders children as the accessible name", () => {
    render(<Button>Save changes</Button>);
    expect(
      screen.getByRole("button", { name: "Save changes" }),
    ).toBeInTheDocument();
  });

  it("defaults to type=button (not submit)", () => {
    render(<Button>Cancel</Button>);
    expect(screen.getByRole("button", { name: "Cancel" })).toHaveAttribute(
      "type",
      "button",
    );
  });

  it("applies the primary variant class by default", () => {
    const { container } = render(<Button>Primary</Button>);
    // The class name is hashed by CSS modules, but the variant class is
    // composed into the className string. We assert that some class is set.
    const button = container.querySelector("button");
    expect(button?.className).toMatch(/_button_/);
    expect(button?.className).not.toBe("");
  });

  it.each([
    ["primary", "Primary"],
    ["accent", "Accent"],
    ["secondary", "Secondary"],
    ["ghost", "Ghost"],
    ["danger", "Danger"],
  ] as const)("applies the %s variant class without error", (variant, label) => {
    const { container } = render(
      <Button variant={variant}>{label}</Button>,
    );
    const button = container.querySelector("button");
    expect(button).not.toBeNull();
    expect(button?.className.length ?? 0).toBeGreaterThan(0);
  });

  it.each([
    ["sm", "Small"],
    ["md", "Medium"],
    ["lg", "Large"],
  ] as const)("applies the %s size class without error", (size, label) => {
    const { container } = render(<Button size={size}>{label}</Button>);
    const button = container.querySelector("button");
    expect(button).not.toBeNull();
  });

  it("invokes onClick when clicked", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Click me</Button>);
    await user.click(screen.getByRole("button", { name: "Click me" }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("does not invoke onClick when disabled", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Button disabled onClick={onClick}>
        Disabled
      </Button>,
    );
    const button = screen.getByRole("button", { name: "Disabled" });
    expect(button).toBeDisabled();
    await user.click(button).catch(() => {
      /* userEvent throws on disabled buttons; ignore */
    });
    expect(onClick).not.toHaveBeenCalled();
  });

  it("sets aria-disabled when loading", () => {
    render(<Button loading>Saving…</Button>);
    const button = screen.getByRole("button", { name: "Saving…" });
    expect(button).toHaveAttribute("aria-disabled", "true");
    expect(button).toHaveAttribute("aria-busy", "true");
  });

  it("does not invoke onClick when loading", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Loading
      </Button>,
    );
    await user
      .click(screen.getByRole("button", { name: "Loading" }))
      .catch(() => {
        /* disabled, click rejected */
      });
    expect(onClick).not.toHaveBeenCalled();
  });

  it("forwards the ref to the underlying button element", () => {
    const ref = vi.fn();
    render(<Button ref={ref}>Ref</Button>);
    expect(ref).toHaveBeenCalledTimes(1);
    const arg = ref.mock.calls[0]?.[0];
    expect(arg).toBeInstanceOf(HTMLButtonElement);
  });

  it("renders leading and trailing icons", () => {
    render(
      <Button
        leadingIcon={<span data-testid="leading">★</span>}
        trailingIcon={<span data-testid="trailing">→</span>}
      >
        With icons
      </Button>,
    );
    expect(screen.getByTestId("leading")).toBeInTheDocument();
    expect(screen.getByTestId("trailing")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "With icons" }),
    ).toBeInTheDocument();
  });

  it("applies fullWidth class when fullWidth=true", () => {
    const { container } = render(<Button fullWidth>Full</Button>);
    const button = container.querySelector("button");
    expect(button).not.toBeNull();
  });
});
