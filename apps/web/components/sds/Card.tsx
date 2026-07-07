'use client';

/*
 * ============================================================================
 *  SDS Card — Component
 * ----------------------------------------------------------------------------
 *  A composable surface container with optional header / body / footer slots.
 *  Built on SDS v2 tokens; RTL-aware via logical CSS properties.
 *
 *  Variants:
 *    default  — flat surface with default border (default)
 *    elevated — medium shadow, no border
 *    outlined — emphasized border, transparent surface
 *
 *  Slots:
 *    • header       — title, description, and/or actions
 *    • children     — body content
 *    • footer       — supplementary actions
 *
 *  Accessibility:
 *    • When `as` is set to "button" or "a", the card becomes interactive
 *      and exposes a focus-visible ring.
 *    • When interactive, pass an accessible `title` or `aria-label`.
 * ============================================================================
 */

import {
  forwardRef,
  type AnchorHTMLAttributes,
  type ButtonHTMLAttributes,
  type ReactNode,
} from 'react';

import styles from './Card.module.css';

export type CardVariant = 'default' | 'elevated' | 'outlined';

export interface CardProps {
  /** Visual style. @default 'default' */
  variant?: CardVariant;
  /** Optional header title. */
  title?: ReactNode;
  /** Optional header description rendered below the title. */
  description?: ReactNode;
  /** Optional actions rendered in the header (inline-end). */
  headerActions?: ReactNode;
  /** Optional footer content. */
  footer?: ReactNode;
  /** Remove body padding (useful for embedded tables / images). */
  bodyFlush?: boolean;
  /** Remove header padding. */
  headerFlush?: boolean;
  /** Remove footer padding. */
  footerFlush?: boolean;
  /** Render as an interactive element (button). */
  interactive?: boolean;
  /** Optional className applied to the root element. */
  className?: string;
  /** Optional className applied to the body slot. */
  bodyClassName?: string;
  /** Optional className applied to the header slot. */
  headerClassName?: string;
  /** Optional className applied to the footer slot. */
  footerClassName?: string;
  /** Card body content. */
  children?: ReactNode;
  /** Accessible label for interactive cards. */
  'aria-label'?: string;
}

type InteractiveCardProps = CardProps & {
  interactive: true;
} & ButtonHTMLAttributes<HTMLButtonElement>;

type StaticCardProps = CardProps & {
  interactive?: false;
};

export type CardComponentProps = InteractiveCardProps | StaticCardProps;

/**
 * SDS Card.
 *
 * @example
 * ```tsx
 * <Card
 *   variant="elevated"
 *   title="Opportunity Summary"
 *   description="3 active deals in pipeline"
 *   headerActions={<Button size="sm">Refresh</Button>}
 *   footer={<Button variant="ghost">Dismiss</Button>}
 * >
 *   <p>Card body content…</p>
 * </Card>
 * ```
 */
export const Card = forwardRef<HTMLDivElement, CardComponentProps>(
  function Card(props, ref) {
    const {
      variant = 'default',
      title,
      description,
      headerActions,
      footer,
      bodyFlush = false,
      headerFlush = false,
      footerFlush = false,
      interactive = false,
      className,
      bodyClassName,
      headerClassName,
      footerClassName,
      children,
      'aria-label': ariaLabel,
      ...rest
    } = props;

    const rootClassList = [
      styles.card,
      styles[variant],
      interactive ? styles.interactive : '',
      className ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    const hasHeader = Boolean(title || description || headerActions);

    const headerClassList = [
      styles.header,
      headerFlush ? styles.headerFlush : '',
      headerClassName ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    const bodyClassList = [
      styles.body,
      bodyFlush ? styles.bodyFlush : '',
      bodyClassName ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    const footerClassList = [
      styles.footer,
      footerFlush ? styles.footerFlush : '',
      footerClassName ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    const headerInner = (
      <>
        <div>
          {title != null ? (
            <h3 className={styles.headerTitle}>{title}</h3>
          ) : null}
          {description != null ? (
            <p className={styles.headerDescription}>{description}</p>
          ) : null}
        </div>
        {headerActions != null ? (
          <div className={styles.headerActions}>{headerActions}</div>
        ) : null}
      </>
    );

    if (interactive) {
      const buttonProps = rest as ButtonHTMLAttributes<HTMLButtonElement>;
      return (
        <button
          ref={ref as React.Ref<HTMLButtonElement>}
          type={buttonProps.type ?? 'button'}
          className={rootClassList}
          aria-label={ariaLabel}
          {...buttonProps}
        >
          {hasHeader ? (
            <div className={headerClassList}>{headerInner}</div>
          ) : null}
          {children != null ? (
            <div className={bodyClassList}>{children}</div>
          ) : null}
          {footer != null ? (
            <div className={footerClassList}>{footer}</div>
          ) : null}
        </button>
      );
    }

    return (
      <div ref={ref} className={rootClassList} aria-label={ariaLabel}>
        {hasHeader ? <div className={headerClassList}>{headerInner}</div> : null}
        {children != null ? (
          <div className={bodyClassList}>{children}</div>
        ) : null}
        {footer != null ? <div className={footerClassList}>{footer}</div> : null}
      </div>
    );
  },
);

Card.displayName = 'Card';

/**
 * Convenience anchor-card variant. Renders the card as an `<a>` so the whole
 * surface is a single navigation target. Honors RTL via logical CSS.
 */
export interface LinkCardProps
  extends CardProps,
    Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'title'> {}

export const LinkCard = forwardRef<HTMLAnchorElement, LinkCardProps>(
  function LinkCard(props, ref) {
    const {
      variant = 'default',
      title,
      description,
      headerActions,
      footer,
      bodyFlush = false,
      headerFlush = false,
      footerFlush = false,
      className,
      bodyClassName,
      headerClassName,
      footerClassName,
      children,
      'aria-label': ariaLabel,
      ...rest
    } = props;

    const rootClassList = [
      styles.card,
      styles[variant],
      styles.interactive,
      className ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    const hasHeader = Boolean(title || description || headerActions);

    const headerClassList = [
      styles.header,
      headerFlush ? styles.headerFlush : '',
      headerClassName ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    const bodyClassList = [
      styles.body,
      bodyFlush ? styles.bodyFlush : '',
      bodyClassName ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    const footerClassList = [
      styles.footer,
      footerFlush ? styles.footerFlush : '',
      footerClassName ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    const headerInner = (
      <>
        <div>
          {title != null ? (
            <h3 className={styles.headerTitle}>{title}</h3>
          ) : null}
          {description != null ? (
            <p className={styles.headerDescription}>{description}</p>
          ) : null}
        </div>
        {headerActions != null ? (
          <div className={styles.headerActions}>{headerActions}</div>
        ) : null}
      </>
    );

    return (
      <a
        ref={ref}
        className={rootClassList}
        aria-label={ariaLabel}
        {...(rest as AnchorHTMLAttributes<HTMLAnchorElement>)}
      >
        {hasHeader ? <div className={headerClassList}>{headerInner}</div> : null}
        {children != null ? (
          <div className={bodyClassList}>{children}</div>
        ) : null}
        {footer != null ? <div className={footerClassList}>{footer}</div> : null}
      </a>
    );
  },
);

LinkCard.displayName = 'LinkCard';
