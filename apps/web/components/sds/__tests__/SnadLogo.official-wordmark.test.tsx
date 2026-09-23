// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SnadLogo } from "../SnadLogo";

vi.mock("next/image", () => ({
  default: ({ alt = "", ...props }: React.ImgHTMLAttributes<HTMLImageElement>) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img alt={alt} {...props} />
  ),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

afterEach(() => cleanup());

describe("SnadLogo official wordmark", () => {
  it("renders the governed official PNG path", () => {
    const { container } = render(<SnadLogo variant="official-wordmark" />);
    expect(container.querySelector("img")?.getAttribute("src"))
      .toBe("/assets/brand/snad-logo-official-wordmark.png");
  });

  it("preserves the approved 1162:337 aspect ratio", () => {
    const { container } = render(<SnadLogo variant="official-wordmark" />);
    const style = container.querySelector("img")?.getAttribute("style") ?? "";
    expect(style).toMatch(/aspect-ratio:\s*3\.44/);
  });

  it("does not enter theme-auto dual rendering when explicitly selected", () => {
    const { container } = render(
      <SnadLogo variant="official-wordmark" theme="auto" />,
    );
    expect(container.querySelectorAll("img")).toHaveLength(1);
  });
});
