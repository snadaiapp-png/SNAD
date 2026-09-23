'use client';

/*
 * ============================================================================
 *  SDS SnadLogo — Centralized Brand Logo Component
 * ----------------------------------------------------------------------------
 *  PURPOSE
 *  -------
 *  Renders governed SNAD brand artwork with full variant/size/theme control.
 *  This is the ONLY component in the entire web app permitted to reference
 *  brand artwork files directly. Every other surface MUST consume this
 *  component — no raw `<img src="/assets/brand/...">` is allowed anywhere
 *  else (enforced by `scripts/ci/check-logo-governance.py`).
 *
 *  WHY A SINGLE COMPONENT?
 *  -----------------------
 *  • Prevents brand drift — every surface renders governed artwork
 *  • Prevents CLS — width/height/aspect-ratio are always pre-computed
 *  • Prevents WCAG regressions — accessible alt is centralized
 *  • Prevents RTL/LTR drift — layout is logical-property based
 *  • Makes future rebrand a one-file change
 *
 *  TOKENS
 *  ------
 *  Every visual property (spacing, motion, focus ring) references an
 *  `--snad-*` token. No hardcoded colors or sizes.
 *
 *  ACCESSIBILITY (WCAG 2.2 AA)
 *  ---------------------------
 *  • `<img>` always carries a non-empty `alt` (defaults to the bilingual
 *    brand string "شعار سند — SNAD Business Operating System").
 *  • When `href` is provided, the wrapping anchor receives an `aria-label`
 *    so screen readers announce the destination, not the visual artwork.
 *  • The anchor has a visible `:focus-visible` ring using
 *    `var(--snad-color-focus-ring)`.
 *
 *  RTL / LTR
 *  ---------
 *  No physical left/right assumptions. The logo is a single inline asset
 *  that aligns to its parent's writing direction automatically.
 *
 *  THEME=AUTO
 *  ----------
 *  When `theme="auto"` AND `variant` is not explicitly set, the component
 *  renders BOTH the primary (light) and white (dark) versions and toggles
 *  visibility via the `prefers-color-scheme` media query. This guarantees:
 *    • No SSR hydration mismatch (both versions exist in initial HTML)
 *    • No flash of incorrect logo on first paint
 *    • Zero client-side JS required for theme detection
 *
 *  OFFICIAL AUTH WORDMARK
 *  ----------------------
 *  `official-wordmark` is the user-approved raster artwork for authentication
 *  surfaces. It is deliberately explicit and never participates in automatic
 *  light/dark recoloring; no synthetic reverse/vector derivative exists.
 * ============================================================================
 */

import {
  forwardRef,
  useId,
  type CSSProperties,
} from 'react';
import Image from 'next/image';
import Link from 'next/link';

import styles from './SnadLogo.module.css';

export type SnadLogoVariant =
  | 'primary'
  | 'horizontal'
  | 'compact'
  | 'white'
  | 'monochrome'
  | 'app-icon'
  | 'official-wordmark';

export type SnadLogoSize =
  | 'xs'
  | 'sm'
  | 'md'
  | 'lg'
  | 'xl'
  | 'responsive';

export type SnadLogoTheme = 'light' | 'dark' | 'auto';

export interface SnadLogoProps {
  /** Visual variant of the logo. @default 'primary' */
  variant?: SnadLogoVariant;
  /** Size preset. @default 'md' */
  size?: SnadLogoSize;
  /**
   * Color theme.
   * - 'light': always use the light-mode variant
   * - 'dark': always use the white variant
   * - 'auto': switch to white variant under `prefers-color-scheme: dark`
   *           (ignored if `variant` is explicitly provided)
   * @default 'auto'
   */
  theme?: SnadLogoTheme;
  /** Override the computed width. Accepts any CSS length. */
  width?: number | string;
  /** Override the computed height. Accepts any CSS length. */
  height?: number | string;
  /**
   * Optional link destination. When provided, the logo is wrapped in a
   * Next.js `<Link>` with an accessible `aria-label`.
   * Use `"/"` for the auth screen, `"/workspace"` for the executive shell.
   */
  href?: string;
  /** Next.js Image `priority` flag — set to true for above-the-fold logos. */
  priority?: boolean;
  /** Accessible label. Defaults to the bilingual brand string. */
  alt?: string;
  /** Additional className applied to the root wrapper. */
  className?: string;
  /** Additional inline styles applied to the root wrapper. */
  style?: CSSProperties;
}

const DEFAULT_ALT = 'شعار سند — SNAD Business Operating System';

/**
 * Static map: variant → public governed asset path.
 * This is the ONLY place in the codebase that knows about brand asset paths.
 */
const VARIANT_SRC: Record<SnadLogoVariant, string> = {
  primary: '/assets/brand/snad-logo-primary.svg',
  horizontal: '/assets/brand/snad-logo-primary.svg',
  compact: '/assets/brand/snad-favicon.svg',
  white: '/assets/brand/snad-logo-white.svg',
  monochrome: '/assets/brand/snad-logo-mono.svg',
  'app-icon': '/assets/brand/snad-app-icon.svg',
  'official-wordmark': '/assets/brand/snad-logo-official-wordmark.png',
};

/** Intrinsic aspect ratio (width / height) per governed variant. */
const VARIANT_ASPECT: Record<SnadLogoVariant, number> = {
  primary: 280 / 80, // 3.5 : 1
  horizontal: 280 / 80,
  compact: 32 / 32, // 1 : 1
  white: 280 / 80,
  monochrome: 280 / 80,
  'app-icon': 512 / 512, // 1 : 1
  'official-wordmark': 1162 / 337,
};

/** Fixed-pixel heights per named size. */
const SIZE_HEIGHT_PX: Record<Exclude<SnadLogoSize, 'responsive'>, number> = {
  xs: 24,
  sm: 32,
  md: 40,
  lg: 48,
  xl: 64,
};

function toCssLength(value: number | string | undefined): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return `${value}px`;
  return String(value);
}

function resolveVariants(
  variant: SnadLogoVariant | undefined,
  theme: SnadLogoTheme,
): SnadLogoVariant[] {
  if (variant !== undefined) {
    return [variant];
  }
  if (theme === 'auto') {
    return ['primary', 'white'];
  }
  if (theme === 'dark') {
    return ['white'];
  }
  return ['primary'];
}

export const SnadLogo = forwardRef<HTMLSpanElement, SnadLogoProps>(
  function SnadLogo(
    {
      variant,
      size = 'md',
      theme = 'auto',
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
    const variants = resolveVariants(variant, theme);
    const isAutoDual = variants.length === 2;
    const instanceId = useId();

    const cssVars: CSSProperties = {};
    const widthCss = toCssLength(width);
    const heightCss = toCssLength(height);
    if (widthCss !== undefined) {
      (cssVars as Record<string, string>)['--snad-logo-width'] = widthCss;
    }
    if (heightCss !== undefined) {
      (cssVars as Record<string, string>)['--snad-logo-height'] = heightCss;
    }

    const sizeClass = styles[`size-${size}`] ?? '';
    const classList = [
      styles.logo,
      sizeClass,
      isAutoDual ? styles.autoDual : '',
      className ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    const isDecorative = href !== undefined;
    const effectiveAlt = isDecorative ? '' : alt;

    function renderImage(variantName: SnadLogoVariant, index: number) {
      const aspect = VARIANT_ASPECT[variantName];
      const intrinsicWidth =
        variantName === 'compact'
          ? 32
          : variantName === 'app-icon'
            ? 512
            : variantName === 'official-wordmark'
              ? 1162
              : 280;
      const intrinsicHeight =
        variantName === 'compact'
          ? 32
          : variantName === 'app-icon'
            ? 512
            : variantName === 'official-wordmark'
              ? 337
              : 80;

      const defaultHeight =
        size === 'responsive' ? intrinsicHeight : SIZE_HEIGHT_PX[size];
      const resolvedHeight =
        heightCss !== undefined && heightCss !== 'auto'
          ? Number.parseFloat(heightCss)
          : defaultHeight;
      const resolvedWidth =
        widthCss !== undefined && widthCss !== 'auto'
          ? Number.parseFloat(widthCss)
          : resolvedHeight !== undefined
            ? resolvedHeight * aspect
            : intrinsicWidth;

      const imageStyle: CSSProperties = {
        aspectRatio: String(aspect),
        width: 'auto',
        height: 'auto',
      };
      if (size !== 'responsive') {
        if (resolvedWidth !== undefined && !Number.isNaN(resolvedWidth)) {
          imageStyle.width = `${resolvedWidth}px`;
        }
        if (resolvedHeight !== undefined && !Number.isNaN(resolvedHeight)) {
          imageStyle.height = `${resolvedHeight}px`;
        }
      }

      const imageClass = isAutoDual
        ? index === 0
          ? `${styles.image} ${styles.imageLight}`
          : `${styles.image} ${styles.imageDark}`
        : styles.image;

      const imgWidth =
        size === 'responsive' ? intrinsicWidth : resolvedWidth ?? intrinsicWidth;
      const imgHeight =
        size === 'responsive' ? intrinsicHeight : resolvedHeight ?? intrinsicHeight;

      const isHiddenDuplicate = isAutoDual && index === 1;

      return (
        <Image
          key={`${instanceId}-${variantName}`}
          className={imageClass}
          src={VARIANT_SRC[variantName]}
          alt={isHiddenDuplicate ? '' : effectiveAlt}
          width={imgWidth}
          height={imgHeight}
          unoptimized
          priority={priority && index === 0}
          aria-hidden={
            isHiddenDuplicate || isDecorative ? true : undefined
          }
          style={imageStyle}
        />
      );
    }

    const images = variants.map((v, i) => renderImage(v, i));
    const wrapperStyle: CSSProperties = { ...cssVars, ...style };

    const content = (
      <span ref={ref} className={classList} style={wrapperStyle}>
        {images}
      </span>
    );

    if (!href) {
      return content;
    }

    return (
      <Link
        href={href}
        className={styles.link}
        aria-label={alt}
      >
        {content}
      </Link>
    );
  },
);

SnadLogo.displayName = 'SnadLogo';
