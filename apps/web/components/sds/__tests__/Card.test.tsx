// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { Card, LinkCard } from "../Card";

describe("SDS Card", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders children in the body slot", () => {
    render(
      <Card>
        <p>Hello, card body!</p>
      </Card>,
    );
    expect(screen.getByText("Hello, card body!")).toBeInTheDocument();
  });

  it("renders the title as an h3 inside the header slot", () => {
    render(<Card title="My Card">Body</Card>);
    const heading = screen.getByRole("heading", { level: 3 });
    expect(heading).toHaveTextContent("My Card");
  });

  it("renders the description below the title", () => {
    render(
      <Card title="My Card" description="A short description">
        Body
      </Card>,
    );
    expect(screen.getByText("A short description")).toBeInTheDocument();
  });

  it("renders headerActions in the header", () => {
    render(
      <Card
        title="My Card"
        headerActions={<button type="button">Refresh</button>}
      >
        Body
      </Card>,
    );
    expect(
      screen.getByRole("button", { name: "Refresh" }),
    ).toBeInTheDocument();
  });

  it("renders footer content", () => {
    render(
      <Card footer={<button type="button">Dismiss</button>}>Body</Card>,
    );
    expect(
      screen.getByRole("button", { name: "Dismiss" }),
    ).toBeInTheDocument();
  });

  it("renders as a button when interactive=true", () => {
    render(
      <Card interactive aria-label="Click the card">
        Body
      </Card>,
    );
    expect(
      screen.getByRole("button", { name: "Click the card" }),
    ).toBeInTheDocument();
  });

  it("renders as an anchor when using LinkCard", () => {
    render(
      <LinkCard href="/details" aria-label="Go to details">
        Body
      </LinkCard>,
    );
    const link = screen.getByRole("link", { name: "Go to details" });
    expect(link).toHaveAttribute("href", "/details");
  });

  it.each([
    ["default", "Default"],
    ["elevated", "Elevated"],
    ["outlined", "Outlined"],
  ] as const)("applies the %s variant without error", (variant, label) => {
    const { container } = render(<Card variant={variant}>{label}</Card>);
    expect(container.firstChild).not.toBeNull();
  });

  it("omits the header slot when no title/description/actions are provided", () => {
    const { container } = render(<Card>Body only</Card>);
    expect(container.querySelector("h3")).toBeNull();
  });
});
