'use client';

/*
 * ============================================================================
 *  SDS SnadLogo — Centralized Brand Logo Component
 * ----------------------------------------------------------------------------
 *  PURPOSE
 *  -------
 *  Renders the official SNAD | سند logo with full variant/size/theme control.
 *  This is the ONLY component in the entire web app permitted to import or
 *  reference brand SVG files directly. Every other surface MUST consume this
 *  component — no raw `<img src="/assets/brand/...">` is allowed anywhere else
 *  (enforced by `scripts/ci/check-logo-governance.py`).
 *
 *  WHY A SINGLE COMPONENT?
 *  -----------------------
 *  • Prevents brand drift — every surface renders the same artwork
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

/*
 * Re-export the useTheme hook type so consumers can pass a resolved theme
 * to SnadLogo without importing from a separate path. The hook itself lives
 * at `@/lib/hooks/useTheme` to keep this component free of client-side
 * hydration concerns (SnadLogo must remain SSR-safe with zero client JS
 * for the default `theme="auto"` rendering).
 */

export type SnadLogoVariant =
  | 'primary'
  | 'horizontal'
  | 'compact'
  | 'white'
  | 'monochrome'
  | 'app-icon';

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
  /**
   * Additional inline styles applied to the root wrapper. Useful for
   * one-off layout overrides (e.g. `marginInlineEnd`). For repeated styles,
   * prefer adding a CSS class to `SnadLogo.module.css`.
   */
  style?: CSSProperties;
}

const DEFAULT_ALT = 'شعار سند — SNAD Business Operating System';

/**
 * Static map: variant → public SVG path.
 * This is the ONLY place in the codebase that knows about brand asset paths.
 */
const VARIANT_SRC: Record<SnadLogoVariant, string> = {
  primary: '/assets/brand/snad-logo-primary.svg',
  horizontal: '/assets/brand/snad-logo-primary.svg',
  compact: '/assets/brand/snad-favicon.svg',
  white: '/assets/brand/snad-logo-white.svg',
  monochrome: '/assets/brand/snad-logo-mono.svg',
  'app-icon': '/assets/brand/snad-app-icon.svg',
};

/**
 * Intrinsic aspect ratio (width / height) per variant. Used to compute a
 * matching width from a given height (and vice versa) so the box model is
 * fully determined before the SVG finishes loading — preventing CLS.
 */
const VARIANT_ASPECT: Record<SnadLogoVariant, number> = {
  primary: 280 / 80, // 3.5 : 1
  horizontal: 280 / 80,
  compact: 32 / 32, // 1 : 1
  white: 280 / 80,
  monochrome: 280 / 80,
  'app-icon': 512 / 512, // 1 : 1
};

/**
 * Fixed-pixel heights per named size. The `responsive` size is handled in
 * CSS via `clamp()` and does not appear in this map.
 */
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

/**
 * Resolve which variants to render.
 *
 * Returns an array of variant names. For `theme="auto"` without an explicit
 * variant, both the `primary` and `white` variants are rendered and CSS
 * toggles their visibility based on `prefers-color-scheme`. For every other
 * configuration, a single variant is returned.
 */
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

/**
 * SDS SnadLogo.
 *
 * @example
 * ```tsx
 * // Auth screen (large, responsive)
 * <SnadLogo variant="primary" size="responsive" />
 *
 * // Executive shell (compact, links to /workspace)
 * <SnadLogo variant="compact" size="md" href="/workspace" />
 *
 * // Dark-mode aware (auto-switches to white)
 * <SnadLogo theme="auto" size="sm" href="/" />
 * ```
 */
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

    // Compute the explicit inline CSS variable overrides for width/height.
    const cssVars: CSSProperties = {};
    const widthCss = toCssLength(width);
    const heightCss = toCssLength(height);
    if (widthCss !== undefined) {
      (cssVars as Record<string, string>)['--snad-logo-width'] = widthCss;
    }
    if (heightCss !== undefined) {
      (cssVars as Record<string, string>)['--snad-logo-height'] = heightCss;
    }

    // Compose class list. The size class is always applied; the auto-dual
    // modifier enables CSS-driven visibility toggling between two stacked
    // images for theme="auto".
    const sizeClass = styles[`size-${size}`] ?? '';
    const classList = [
      styles.logo,
      sizeClass,
      isAutoDual ? styles.autoDual : '',
      className ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    // When `href` is provided, the wrapping `<Link>` carries the accessible
    // name via `aria-label`. The inner `<img>` becomes decorative and MUST
    // use `alt=""` + `aria-hidden="true"` so screen readers do not
    // double-announce (WAI-ARIA APG: "Providing accessible names for links
    // that wrap non-text content").
    const isDecorative = href !== undefined;
    const effectiveAlt = isDecorative ? '' : alt;

    // For each variant to render, compute the explicit width/height so the
    // browser reserves the correct box before the SVG arrives (CLS safety).
    // When the caller overrides width or height explicitly, we respect that;
    // when not, we derive the missing dimension from the variant's intrinsic
    // aspect ratio and the size preset's height. For `responsive` size, we
    // fall back to the variant's intrinsic pixel dimensions and let CSS
    // clamp() scale the rendered image.
    function renderImage(variantName: SnadLogoVariant, index: number) {
      const aspect = VARIANT_ASPECT[variantName];
      const intrinsicWidth =
        variantName === 'compact'
          ? 32
          : variantName === 'app-icon'
            ? 512
            : 280;
      const intrinsicHeight =
        variantName === 'compact'
          ? 32
          : variantName === 'app-icon'
            ? 512
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
      // For fixed sizes, pin the box dimensions inline so CLS is zero. For
      // the responsive size, the dimensions are driven by CSS variables
      // (set by the size class on the wrapper) — we only set the
      // aspect-ratio inline to guarantee the box shape.
      if (size !== 'responsive') {
        if (resolvedWidth !== undefined && !Number.isNaN(resolvedWidth)) {
          imageStyle.width = `${resolvedWidth}px`;
        }
        if (resolvedHeight !== undefined && !Number.isNaN(resolvedHeight)) {
          imageStyle.height = `${resolvedHeight}px`;
        }
      }

      // The auto-dual pair stacks two images; the second one (white) is
      // visually hidden by default and revealed under dark mode via CSS.
      const imageClass = isAutoDual
        ? index === 0
          ? `${styles.image} ${styles.imageLight}`
          : `${styles.image} ${styles.imageDark}`
        : styles.image;

      // Next.js Image requires `width` and `height` props even when
      // `unoptimized` is set. We pass the variant's intrinsic dimensions
      // (or the computed dimensions for fixed sizes) — the CSS width/height
      // on the wrapper drives the actual rendered size for `responsive`.
      const imgWidth =
        size === 'responsive' ? intrinsicWidth : resolvedWidth ?? intrinsicWidth;
      const imgHeight =
        size === 'responsive' ? intrinsicHeight : resolvedHeight ?? intrinsicHeight;

      // The second image in an auto-dual pair is visually hidden by CSS
      // until `prefers-color-scheme: dark` activates it. We mark it
      // `aria-hidden` and use an empty `alt` so AT never announces the
      // hidden duplicate.
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

    // Merge the consumer-supplied `style` with the CSS-variable overrides
    // computed above. Consumer style wins for conflicting keys (e.g. if the
    // caller passes `style={{ ['--snad-logo-width' as never]: '200px' }}`).
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
        // The visual artwork is decorative — the anchor's accessible name
        // comes from `aria-label`. We hide the inner img from AT to avoid
        // double-announcement.
      >
        {content}
      </Link>
    );
  },
);

SnadLogo.displayName = 'SnadLogo';
