'use client';

/*
 * ============================================================================
 *  SDS Button — Component
 * ----------------------------------------------------------------------------
 *  A reusable, accessible button primitive that consumes SDS v2 design tokens
 *  exclusively. Built to WCAG 2.2 AA:
 *
 *    • Minimum 44x44 px touch target for all sizes (sm+, see Button.module.css)
 *    • Visible focus ring via :focus-visible + --snad-color-focus-ring
 *    • Disabled state is exposed via aria-disabled (and visually via :disabled)
 *    • Loading state hides text but preserves it for screen readers
 *
 *  RTL-aware: no left/right assumptions in the component code; CSS uses
 *  logical properties (margin-inline-*, padding-inline-*, inset-inline-*).
 *
 *  Variants:
 *    primary   — brand petroleum green, white text (default action)
 *    accent    — royal gold, charcoal text (highlight / premium)
 *    secondary — warm-gray surface, charcoal text (alternative action)
 *    ghost     — transparent surface, charcoal text (low-emphasis)
 *    danger    — error red, white text (destructive)
 *
 *  Sizes:
 *    sm, md, lg — all preserve the 44x44 minimum touch target.
 * ============================================================================
 */

import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';

import styles from './Button.module.css';

export type ButtonVariant =
  | 'primary'
  | 'accent'
  | 'secondary'
  | 'ghost'
  | 'danger';

export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Visual style. @default 'primary' */
  variant?: ButtonVariant;
  /** Size preset. @default 'md' */
  size?: ButtonSize;
  /** Show a spinner and disable interaction. @default false */
  loading?: boolean;
  /** Stretch to fill the parent's inline axis. @default false */
  fullWidth?: boolean;
  /** Optional leading icon (rendered before the children). */
  leadingIcon?: ReactNode;
  /** Optional trailing icon (rendered after the children). */
  trailingIcon?: ReactNode;
  /** Accessible label used when the visible text is hidden (e.g. icon-only). */
  'aria-label'?: string;
}

/**
 * SDS Button.
 *
 * Use this component for all primary interactive actions in the SNAD web app.
 * Prefer the SDS `Button` over raw `<button>` elements so the design system
 * can guarantee consistent visual identity and accessibility behavior.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    fullWidth = false,
    leadingIcon,
    trailingIcon,
    disabled,
    className,
    children,
    type = 'button',
    onClick,
    'aria-label': ariaLabel,
    ...rest
  },
  ref,
) {
  const isDisabled = disabled || loading;

  const classList = [
    styles.button,
    styles[variant],
    styles[size],
    fullWidth ? styles.fullWidth : '',
    loading ? styles.loading : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');

  const handleClick: React.MouseEventHandler<HTMLButtonElement> = (event) => {
    if (isDisabled) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    onClick?.(event);
  };

  return (
    <button
      ref={ref}
      type={type}
      className={classList}
      aria-disabled={isDisabled || undefined}
      aria-busy={loading || undefined}
      aria-label={ariaLabel}
      disabled={isDisabled}
      onClick={handleClick}
      {...rest}
    >
      {leadingIcon ? (
        <span aria-hidden="true" className={styles.icon}>
          {leadingIcon}
        </span>
      ) : null}
      {children ? <span className={styles.label}>{children}</span> : null}
      {trailingIcon ? (
        <span aria-hidden="true" className={styles.icon}>
          {trailingIcon}
        </span>
      ) : null}
    </button>
  );
});

Button.displayName = 'Button';
