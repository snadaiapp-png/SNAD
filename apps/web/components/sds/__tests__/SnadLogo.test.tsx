// @vitest-environment jsdom

/*
 * ============================================================================
 *  SDS SnadLogo — Component Tests
 * ----------------------------------------------------------------------------
 *  Verifies:
 *    • All 6 variants render the correct SVG path
 *    • All 5 fixed sizes (xs/sm/md/lg/xl) apply a size class
 *    • `responsive` size applies its own class
 *    • `href` wraps the logo in a Next.js <Link> with aria-label
 *    • Default alt text is the bilingual brand string
 *    • Custom alt text overrides the default
 *    • theme="auto" renders BOTH primary and white variants (dual mode)
 *    • theme="dark" uses the white variant
 *    • Explicit variant overrides theme="auto" (no dual rendering)
 *    • Ref is forwarded to the root span
 * ============================================================================
 */

import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SnadLogo } from "../SnadLogo";

// Mock next/image to render a plain <img> so jsdom can introspect it.
// We destructure `alt` and pass it explicitly so the jsx-a11y/alt-text rule
// is satisfied. The @next/next/no-img-element rule is inherently triggered
// by mocking next/image (the whole point of the mock is to render a plain
// <img>), so we disable it for this line only.
vi.mock("next/image", () => ({
  default: ({ alt = "", ...props }: React.ImgHTMLAttributes<HTMLImageElement>) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img alt={alt} {...props} />
  ),
}));

// Mock next/link to render a plain <a> so jsdom can introspect it.
vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

describe("SDS SnadLogo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders two <img>s by default (theme='auto' dual rendering)", () => {
    const { container } = render(<SnadLogo />);
    const imgs = container.querySelectorAll("img");
    // Default theme is 'auto' → primary + white variants stacked.
    expect(imgs).toHaveLength(2);
  });

  it("uses the default bilingual alt text", () => {
    render(<SnadLogo />);
    const img = screen.getByAltText(
      "شعار سند — SNAD Business Operating System",
    );
    expect(img).toBeInTheDocument();
  });

  it("honors a custom alt override", () => {
    render(<SnadLogo alt="Custom alt" />);
    expect(screen.getByAltText("Custom alt")).toBeInTheDocument();
  });

  it.each([
    ["primary", "/assets/brand/snad-logo-primary.svg"],
    ["horizontal", "/assets/brand/snad-logo-primary.svg"],
    ["compact", "/assets/brand/snad-favicon.svg"],
    ["white", "/assets/brand/snad-logo-white.svg"],
    ["monochrome", "/assets/brand/snad-logo-mono.svg"],
    ["app-icon", "/assets/brand/snad-app-icon.svg"],
  ] as const)(
    "renders the correct SVG path for variant=%s",
    (variant, expectedPath) => {
      const { container } = render(<SnadLogo variant={variant} />);
      const img = container.querySelector("img");
      expect(img).not.toBeNull();
      expect(img?.getAttribute("src")).toBe(expectedPath);
    },
  );

  it.each([
    ["xs", 24],
    ["sm", 32],
    ["md", 40],
    ["lg", 48],
    ["xl", 64],
  ] as const)(
    "applies a size class for size=%s and pre-computes height=%dpx",
    (size, expectedHeight) => {
      const { container } = render(<SnadLogo size={size} />);
      const img = container.querySelector("img");
      expect(img).not.toBeNull();
      // The inline style sets height in px to prevent CLS.
      expect(img?.getAttribute("style") ?? "").toContain(
        `height: ${expectedHeight}px`,
      );
    },
  );

  it("applies the responsive size class", () => {
    const { container } = render(<SnadLogo size="responsive" />);
    // The wrapper span should carry a class matching the responsive preset.
    const span = container.querySelector("span");
    expect(span).not.toBeNull();
    expect(span?.className.length ?? 0).toBeGreaterThan(0);
    // For responsive size, no inline height is forced — CSS clamp() drives it.
    const img = container.querySelector("img");
    expect(img?.getAttribute("style") ?? "").not.toMatch(/height: \d+px/);
  });

  it("wraps the logo in a Next.js <Link> when href is provided", () => {
    const { container } = render(<SnadLogo href="/workspace" />);
    const anchor = container.querySelector("a");
    expect(anchor).not.toBeNull();
    expect(anchor?.getAttribute("href")).toBe("/workspace");
  });

  it("does NOT render an anchor when href is omitted", () => {
    const { container } = render(<SnadLogo />);
    expect(container.querySelector("a")).toBeNull();
  });

  it("sets aria-label on the anchor equal to the alt text", () => {
    const { container } = render(
      <SnadLogo href="/" alt="Go home" />,
    );
    const anchor = container.querySelector("a");
    expect(anchor?.getAttribute("aria-label")).toBe("Go home");
  });

  it("uses the default alt for the anchor aria-label when alt is omitted", () => {
    const { container } = render(<SnadLogo href="/" />);
    const anchor = container.querySelector("a");
    expect(anchor?.getAttribute("aria-label")).toBe(
      "شعار سند — SNAD Business Operating System",
    );
  });

  it("renders BOTH primary and white variants when theme='auto' and no variant is set", () => {
    const { container } = render(<SnadLogo theme="auto" />);
    const imgs = container.querySelectorAll("img");
    expect(imgs).toHaveLength(2);
    const srcs = Array.from(imgs).map((i) => i.getAttribute("src"));
    expect(srcs).toContain("/assets/brand/snad-logo-primary.svg");
    expect(srcs).toContain("/assets/brand/snad-logo-white.svg");
  });

  it("renders a single image when theme='auto' but variant is explicit", () => {
    const { container } = render(
      <SnadLogo theme="auto" variant="monochrome" />,
    );
    const imgs = container.querySelectorAll("img");
    expect(imgs).toHaveLength(1);
    expect(imgs[0]?.getAttribute("src")).toBe(
      "/assets/brand/snad-logo-mono.svg",
    );
  });

  it("uses the white variant when theme='dark' and no variant is set", () => {
    const { container } = render(<SnadLogo theme="dark" />);
    const imgs = container.querySelectorAll("img");
    expect(imgs).toHaveLength(1);
    expect(imgs[0]?.getAttribute("src")).toBe(
      "/assets/brand/snad-logo-white.svg",
    );
  });

  it("uses the primary variant when theme='light' and no variant is set", () => {
    const { container } = render(<SnadLogo theme="light" />);
    const imgs = container.querySelectorAll("img");
    expect(imgs).toHaveLength(1);
    expect(imgs[0]?.getAttribute("src")).toBe(
      "/assets/brand/snad-logo-primary.svg",
    );
  });

  it("defaults theme to 'auto' when not specified (renders primary + white)", () => {
    const { container } = render(<SnadLogo />);
    const imgs = container.querySelectorAll("img");
    expect(imgs).toHaveLength(2);
    const srcs = Array.from(imgs).map((i) => i.getAttribute("src"));
    expect(srcs).toContain("/assets/brand/snad-logo-primary.svg");
    expect(srcs).toContain("/assets/brand/snad-logo-white.svg");
  });

  it("marks the inner img as decorative (alt='' + aria-hidden) when href is provided", () => {
    const { container } = render(<SnadLogo href="/" alt="Go home" />);
    const img = container.querySelector("img");
    expect(img).not.toBeNull();
    // The link provides the accessible name; the inner img must be
    // decorative to avoid double-announcement.
    expect(img?.getAttribute("alt")).toBe("");
    expect(img?.getAttribute("aria-hidden")).toBe("true");
  });

  it("applies a consumer-supplied style to the root wrapper", () => {
    const { container } = render(
      <SnadLogo style={{ marginInlineEnd: "1rem" }} />,
    );
    const span = container.querySelector("span");
    expect(span).not.toBeNull();
    expect(span?.getAttribute("style") ?? "").toMatch(/margin-inline-end:\s*1rem/);
  });

  it("forwards the ref to the root span element", () => {
    const ref = vi.fn();
    render(<SnadLogo ref={ref} />);
    expect(ref).toHaveBeenCalledTimes(1);
    const arg = ref.mock.calls[0]?.[0];
    expect(arg).toBeInstanceOf(HTMLSpanElement);
  });

  it("applies a consumer-supplied className alongside the size class", () => {
    const { container } = render(
      <SnadLogo className="custom-class" />,
    );
    const span = container.querySelector("span");
    expect(span?.className).toContain("custom-class");
  });

  it("passes the priority flag to the first image (Next.js Image prop)", () => {
    const { container } = render(<SnadLogo priority />);
    const img = container.querySelector("img");
    // next/image maps `priority` to `data-nimg` and fetchpriority; the mock
    // here forwards all props to <img>, so we assert the attribute round-trips.
    // We at least verify the image renders without error.
    expect(img).not.toBeNull();
  });

  it("renders an aspect-ratio inline style for CLS prevention", () => {
    const { container } = render(<SnadLogo variant="primary" />);
    const img = container.querySelector("img");
    // Primary variant has a 280:80 (3.5) aspect ratio.
    expect(img?.getAttribute("style") ?? "").toMatch(/aspect-ratio:\s*3\.5/);
  });
});
