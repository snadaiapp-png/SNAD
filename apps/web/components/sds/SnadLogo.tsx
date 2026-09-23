'use client';

/*
 * ============================================================================
 *  SDS SnadLogo — Centralized Brand Logo Component
 * ----------------------------------------------------------------------------
 *  PURPOSE
 *  -------
 *  Renders SNAD brand artwork with full variant/size/theme control.
 *  This is the ONLY component in the entire web app permitted to reference
 *  brand asset files directly. Every other surface MUST consume this
 *  component — no raw `<img src="/assets/brand/...">` is allowed elsewhere.
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

const DEFAULT_ALT = 'شعار سند — SNAD Business Operating System';

/** The only canonical brand-asset path map used by application components. */
const VARIANT_SRC: Record<SnadLogoVariant, string> = {
  primary: '/assets/brand/snad-logo-primary.svg',
  horizontal: '/assets/brand/snad-logo-primary.svg',
  compact: '/assets/brand/snad-favicon.svg',
  white: '/assets/brand/snad-logo-white.svg',
  monochrome: '/assets/brand/snad-logo-mono.svg',
  'app-icon': '/assets/brand/snad-app-icon.svg',
  'official-wordmark': '/assets/brand/snad-logo-official-wordmark.png',
};

const VARIANT_ASPECT: Record<SnadLogoVariant, number> = {
  primary: 280 / 80,
  horizontal: 280 / 80,
  compact: 32 / 32,
  white: 280 / 80,
  monochrome: 280 / 80,
  'app-icon': 512 / 512,
  'official-wordmark': 1162 / 337,
};

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
  if (variant !== undefined) return [variant];
  if (theme === 'auto') return ['primary', 'white'];
  if (theme === 'dark') return ['white'];
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
          aria-hidden={isHiddenDuplicate || isDecorative ? true : undefined}
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

    if (!href) return content;

    return (
      <Link href={href} className={styles.link} aria-label={alt}>
        {content}
      </Link>
    );
  },
);

SnadLogo.displayName = 'SnadLogo';
