"use client";

import Image from "next/image";
import Link from "next/link";
import { forwardRef, type CSSProperties } from "react";
import styles from "./SnadLogo.module.css";

export type SnadLogoVariant =
  | "primary"
  | "horizontal"
  | "compact"
  | "white"
  | "monochrome"
  | "app-icon";

export type SnadLogoSize =
  | "xs"
  | "sm"
  | "md"
  | "lg"
  | "xl"
  | "responsive";

export type SnadLogoTheme = "light" | "dark" | "auto";

export interface SnadLogoProps {
  variant?: SnadLogoVariant;
  size?: SnadLogoSize;
  theme?: SnadLogoTheme;
  width?: number | string;
  height?: number | string;
  href?: string;
  priority?: boolean;
  alt?: string;
  className?: string;
  style?: CSSProperties;
}

const DEFAULT_ALT = "شعار سند — SNAD Business Operating System";

/**
 * Canonical artwork exported directly from the owner-approved logo image.
 * No SVG reconstruction or substitute mark is permitted.
 */
const ASSETS: Record<SnadLogoVariant, {
  src: string;
  width: number;
  height: number;
}> = {
  primary: {
    src: "/assets/brand/snad-logo-official-primary.png",
    width: 1230,
    height: 630,
  },
  horizontal: {
    src: "/assets/brand/snad-logo-official-primary.png",
    width: 1230,
    height: 630,
  },
  compact: {
    src: "/assets/brand/snad-logo-official-wordmark.png",
    width: 940,
    height: 440,
  },
  white: {
    src: "/assets/brand/snad-logo-official-primary.png",
    width: 1230,
    height: 630,
  },
  monochrome: {
    src: "/assets/brand/snad-logo-official-wordmark.png",
    width: 940,
    height: 440,
  },
  "app-icon": {
    src: "/assets/brand/snad-logo-official-wordmark.png",
    width: 940,
    height: 440,
  },
};

const FIXED_HEIGHTS: Record<Exclude<SnadLogoSize, "responsive">, number> = {
  xs: 24,
  sm: 32,
  md: 40,
  lg: 56,
  xl: 72,
};

function cssLength(value: number | string | undefined): string | undefined {
  if (value === undefined) return undefined;
  return typeof value === "number" ? `${value}px` : value;
}

export const SnadLogo = forwardRef<HTMLSpanElement, SnadLogoProps>(
  function SnadLogo(
    {
      variant = "primary",
      size = "md",
      width,
      height,
      href,
      priority = false,
      alt = DEFAULT_ALT,
      className,
      style,
    },
    ref,
  ) {
    const asset = ASSETS[variant];
    const aspect = asset.width / asset.height;
    const widthOverride = cssLength(width);
    const heightOverride = cssLength(height);
    const fixedHeight = size === "responsive" ? asset.height : FIXED_HEIGHTS[size];

    const resolvedHeight =
      heightOverride && heightOverride !== "auto"
        ? Number.parseFloat(heightOverride)
        : fixedHeight;
    const resolvedWidth =
      widthOverride && widthOverride !== "auto"
        ? Number.parseFloat(widthOverride)
        : resolvedHeight * aspect;

    const cssVars = {
      ...(style ?? {}),
      ...(widthOverride
        ? ({ "--snad-logo-width": widthOverride } as CSSProperties)
        : {}),
      ...(heightOverride
        ? ({ "--snad-logo-height": heightOverride } as CSSProperties)
        : {}),
      "--snad-logo-aspect-ratio": String(aspect),
    } as CSSProperties;

    const classes = [
      styles.logo,
      styles[`size-${size}`],
      className ?? "",
    ].filter(Boolean).join(" ");

    const decorative = Boolean(href);
    const content = (
      <span ref={ref} className={classes} style={cssVars}>
        <Image
          className={styles.image}
          src={asset.src}
          alt={decorative ? "" : alt}
          width={Math.max(1, Math.round(resolvedWidth))}
          height={Math.max(1, Math.round(resolvedHeight))}
          priority={priority}
          aria-hidden={decorative || undefined}
          sizes={
            size === "responsive"
              ? "(max-width: 599px) 230px, (max-width: 899px) 300px, 360px"
              : `${Math.max(1, Math.round(resolvedWidth))}px`
          }
        />
      </span>
    );

    if (!href) return content;

    return (
      <Link href={href} className={styles.link} aria-label={alt}>
        {content}
      </Link>
    );
  },
);

SnadLogo.displayName = "SnadLogo";
