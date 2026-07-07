// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SnadLogo } from "../SnadLogo";

vi.mock("next/image", () => ({
  default: ({ alt = "", priority: _priority, ...props }: React.ImgHTMLAttributes<HTMLImageElement> & { priority?: boolean }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img alt={alt} {...props} />
  ),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

afterEach(cleanup);

describe("SDS SnadLogo", () => {
  it("renders the owner-approved primary artwork by default", () => {
    render(<SnadLogo />);
    const image = screen.getByAltText("شعار سند — SNAD Business Operating System");
    expect(image).toHaveAttribute("src", "/assets/brand/snad-logo-official-primary.webp");
  });

  it.each([
    ["primary", "/assets/brand/snad-logo-official-primary.webp"],
    ["horizontal", "/assets/brand/snad-logo-official-primary.webp"],
    ["white", "/assets/brand/snad-logo-official-primary.webp"],
    ["compact", "/assets/brand/snad-logo-official-wordmark.webp"],
    ["monochrome", "/assets/brand/snad-logo-official-wordmark.webp"],
    ["app-icon", "/assets/brand/snad-logo-official-wordmark.webp"],
  ] as const)("uses canonical artwork for %s", (variant, expected) => {
    const { container } = render(<SnadLogo variant={variant} />);
    expect(container.querySelector("img")).toHaveAttribute("src", expected);
  });

  it("renders exactly one image to avoid duplicate theme downloads", () => {
    const { container } = render(<SnadLogo theme="auto" />);
    expect(container.querySelectorAll("img")).toHaveLength(1);
  });

  it("makes the image decorative when the link provides the accessible name", () => {
    const { container } = render(<SnadLogo href="/workspace" alt="العودة لمساحة العمل" />);
    const link = container.querySelector("a");
    const image = container.querySelector("img");
    expect(link).toHaveAttribute("href", "/workspace");
    expect(link).toHaveAttribute("aria-label", "العودة لمساحة العمل");
    expect(image).toHaveAttribute("alt", "");
    expect(image).toHaveAttribute("aria-hidden", "true");
  });

  it("reserves intrinsic dimensions and an aspect ratio", () => {
    const { container } = render(<SnadLogo variant="primary" size="md" />);
    const image = container.querySelector("img");
    expect(Number(image?.getAttribute("width"))).toBeGreaterThan(0);
    expect(Number(image?.getAttribute("height"))).toBeGreaterThan(0);
    expect(container.querySelector("span")?.getAttribute("style") ?? "").toContain("--snad-logo-aspect-ratio");
  });

  it("forwards refs and custom classes", () => {
    const ref = vi.fn();
    const { container } = render(<SnadLogo ref={ref} className="custom-logo" />);
    expect(ref).toHaveBeenCalledTimes(1);
    expect(container.querySelector("span")).toHaveClass("custom-logo");
  });
});
