// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { Input } from "../Input";

describe("SDS Input", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders a label associated with the input via htmlFor/id", () => {
    render(<Input label="Email address" id="email" />);
    const input = screen.getByLabelText("Email address");
    expect(input).toHaveAttribute("id", "email");
    expect(document.querySelector("label[for='email']")).not.toBeNull();
  });

  it("auto-generates a stable id when none is provided", () => {
    render(<Input label="Username" />);
    const input = screen.getByLabelText("Username");
    expect(input).toHaveAttribute("id");
    expect(input.id.length).toBeGreaterThan(0);
  });

  it("renders the hint below the input and links it via aria-describedby", () => {
    render(<Input label="Email" hint="We'll never share your email." />);
    const input = screen.getByLabelText("Email");
    const hint = screen.getByText("We'll never share your email.");
    const hintId = hint.getAttribute("id");
    expect(input.getAttribute("aria-describedby")).toContain(hintId);
  });

  it("renders the error and sets aria-invalid when an error is provided", () => {
    render(<Input label="Email" error="This field is required" />);
    const input = screen.getByLabelText("Email");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This field is required",
    );
  });

  it("prefers the error message over the hint (does not render both)", () => {
    render(
      <Input label="Email" hint="Hint text" error="Error text" />,
    );
    expect(screen.queryByText("Hint text")).toBeNull();
    expect(screen.getByText("Error text")).toBeInTheDocument();
  });

  it("sets aria-required when required=true", () => {
    render(<Input label="Email" required />);
    expect(screen.getByLabelText("Email")).toHaveAttribute(
      "aria-required",
      "true",
    );
  });

  it.each([
    ["text", "text"],
    ["email", "email"],
    ["password", "password"],
    ["search", "search"],
  ] as const)("renders type=%s correctly", (type, expected) => {
    const { rerender } = render(<Input label="Field" type={type} />);
    expect(screen.getByLabelText("Field")).toHaveAttribute("type", expected);
    rerender(<Input label="Field" type={type} />);
  });

  it("forwards arbitrary input attributes", () => {
    render(
      <Input
        label="Email"
        placeholder="you@example.com"
        autoComplete="email"
        maxLength={120}
      />,
    );
    const input = screen.getByLabelText("Email");
    expect(input).toHaveAttribute("placeholder", "you@example.com");
    expect(input).toHaveAttribute("autocomplete", "email");
    expect(input).toHaveAttribute("maxlength", "120");
  });

  it("renders leading and trailing icons", () => {
    render(
      <Input
        label="Search"
        leadingIcon={<span data-testid="leading">🔍</span>}
        trailingIcon={<span data-testid="trailing">×</span>}
      />,
    );
    expect(screen.getByTestId("leading")).toBeInTheDocument();
    expect(screen.getByTestId("trailing")).toBeInTheDocument();
  });

  it("sets disabled attribute when disabled=true", () => {
    render(<Input label="Email" disabled />);
    expect(screen.getByLabelText("Email")).toBeDisabled();
  });
});
