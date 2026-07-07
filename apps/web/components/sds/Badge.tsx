'use client';

/*
 * ============================================================================
 *  SDS Badge — Component
 * ----------------------------------------------------------------------------
 *  A small status indicator. Built on SDS v2 tokens; RTL-aware.
 *
 *  Variants:
 *    default, success, warning, error, info, accent
 *
 *  Sizes:
 *    sm, md
 *
 *  Renders as a `<span>` by default. Pass `as="div"` for a block badge.
 * ============================================================================
 */

import { forwardRef, type HTMLAttributes, type ReactNode } from 'react';

import styles from './Badge.module.css';

export type BadgeVariant =
  | 'default'
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'accent';

export type BadgeSize = 'sm' | 'md';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  /** Visual style. @default 'default' */
  variant?: BadgeVariant;
  /** Size preset. @default 'md' */
  size?: BadgeSize;
  /** Show a leading status dot (colored to match the variant). */
  withDot?: boolean;
  /** Optional className applied to the root element. */
  className?: string;
  /** Badge content. */
  children?: ReactNode;
}

/**
 * SDS Badge.
 *
 * @example
 * ```tsx
 * <Badge variant="success" withDot>Active</Badge>
 * <Badge variant="warning">Trial</Badge>
 * <Badge variant="error" size="sm">Blocked</Badge>
 * ```
 */
export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(function Badge(
  {
    variant = 'default',
    size = 'md',
    withDot = false,
    className,
    children,
    ...rest
  },
  ref,
) {
  const classList = [styles.badge, styles[variant], styles[size], className ?? '']
    .filter(Boolean)
    .join(' ');

  return (
    <span ref={ref} className={classList} {...rest}>
      {withDot ? <span aria-hidden="true" className={styles.dot} /> : null}
      {children}
    </span>
  );
});

Badge.displayName = 'Badge';
