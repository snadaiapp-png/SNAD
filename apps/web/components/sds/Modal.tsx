'use client';

/*
 * ============================================================================
 *  SDS Modal — Component
 * ----------------------------------------------------------------------------
 *  A focus-trapped, ESC-closable dialog rendered in a React portal. Built on
 *  SDS v2 tokens; RTL-aware via logical CSS.
 *
 *  Props:
 *    isOpen      — when true, the modal is rendered
 *    onClose     — callback invoked when the user requests close
 *                   (ESC key, backdrop click, close button)
 *    title       — required for aria-labelledby
 *    subtitle    — optional secondary title
 *    children    — body content
 *    footer      — optional footer (typically action buttons)
 *    size        — sm | md | lg | full (default: md)
 *    closeOnBackdropClick — default true
 *    closeOnEsc  — default true
 *    bodyFlush   — remove body padding (default false)
 *
 *  Accessibility:
 *    • role="dialog", aria-modal="true", aria-labelledby={titleId}
 *    • Basic focus trap: on open, focus moves to the modal panel; on close,
 *      focus is restored to the previously focused element.
 *    • ESC closes (when closeOnEsc is true)
 *    • Click on backdrop closes (when closeOnBackdropClick is true)
 *    • z-index: --snad-z-modal (1400); backdrop at --snad-z-modal-backdrop (1300)
 *
 *  NOTE: This component renders via React.createPortal into document.body.
 *        It must be used inside a Client Component tree.
 * ============================================================================
 */

import {
  useCallback,
  useEffect,
  useId,
  useRef,
  type ReactNode,
} from 'react';
import { createPortal } from 'react-dom';

import styles from './Modal.module.css';

export type ModalSize = 'sm' | 'md' | 'lg' | 'full';

export interface ModalProps {
  /** Whether the modal is currently open. */
  isOpen: boolean;
  /** Callback invoked when the user requests to close the modal. */
  onClose: () => void;
  /** Modal title (required for accessibility). */
  title: ReactNode;
  /** Optional subtitle rendered below the title. */
  subtitle?: ReactNode;
  /** Modal body content. */
  children?: ReactNode;
  /** Optional footer (typically action buttons). */
  footer?: ReactNode;
  /** Modal size preset. @default 'md' */
  size?: ModalSize;
  /** Close when the user clicks the backdrop. @default true */
  closeOnBackdropClick?: boolean;
  /** Close when the user presses ESC. @default true */
  closeOnEsc?: boolean;
  /** Remove body padding (useful for embedded tables / scroll containers). */
  bodyFlush?: boolean;
  /** Optional className applied to the modal panel. */
  className?: string;
  /** Accessible label for the close button. @default 'Close' */
  closeButtonLabel?: string;
  /** Force the modal title id (otherwise auto-generated). */
  titleId?: string;
}

/**
 * SDS Modal.
 *
 * @example
 * ```tsx
 * <Modal
 *   isOpen={open}
 *   onClose={() => setOpen(false)}
 *   title="Confirm deletion"
 *   footer={
 *     <>
 *       <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
 *       <Button variant="danger" onClick={handleDelete}>Delete</Button>
 *     </>
 *   }
 * >
 *   <p>Are you sure you want to delete this record? This action cannot be undone.</p>
 * </Modal>
 * ```
 */
export function Modal({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  footer,
  size = 'md',
  closeOnBackdropClick = true,
  closeOnEsc = true,
  bodyFlush = false,
  className,
  closeButtonLabel = 'Close',
  titleId,
}: ModalProps): ReactNode {
  const generatedTitleId = useId();
  const resolvedTitleId = titleId ?? generatedTitleId;

  const modalRef = useRef<HTMLDivElement>(null);
  const previouslyFocusedRef = useRef<HTMLElement | null>(null);

  // ESC key handler.
  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (!closeOnEsc) return;
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
      }
    },
    [closeOnEsc, onClose],
  );

  // Focus management + body scroll lock.
  useEffect(() => {
    if (!isOpen) return;

    previouslyFocusedRef.current = document.activeElement as HTMLElement | null;

    // Move focus into the modal panel.
    const focusTimer = window.setTimeout(() => {
      modalRef.current?.focus();
    }, 0);

    // Lock body scroll.
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    // Attach ESC listener on document (capture phase so we get it first).
    document.addEventListener('keydown', handleKeyDown, true);

    return () => {
      window.clearTimeout(focusTimer);
      document.removeEventListener('keydown', handleKeyDown, true);
      document.body.style.overflow = previousOverflow;

      // Restore focus to the previously focused element.
      const prev = previouslyFocusedRef.current;
      if (prev && typeof prev.focus === 'function') {
        prev.focus();
      }
    };
  }, [isOpen, handleKeyDown]);

  if (!isOpen) return null;

  // Avoid SSR issues — only render portal when document is available.
  if (typeof document === 'undefined') return null;

  const handleBackdropClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (!closeOnBackdropClick) return;
    // Only close when the click was on the backdrop itself, not a child.
    if (event.target === event.currentTarget) {
      onClose();
    }
  };

  const modalClassList = [
    styles.modal,
    styles[size],
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');

  return createPortal(
    <div
      className={styles.overlay}
      onClick={handleBackdropClick}
      role="presentation"
    >
      <div
        ref={modalRef}
        className={modalClassList}
        role="dialog"
        aria-modal="true"
        aria-labelledby={resolvedTitleId}
        tabIndex={-1}
      >
        <header className={styles.header}>
          <div className={styles.titleGroup}>
            <h2 id={resolvedTitleId} className={styles.title}>
              {title}
            </h2>
            {subtitle != null ? (
              <p className={styles.subtitle}>{subtitle}</p>
            ) : null}
          </div>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
            aria-label={closeButtonLabel}
          >
            <span aria-hidden="true">×</span>
          </button>
        </header>

        <div
          className={[styles.body, bodyFlush ? styles.bodyFlush : '']
            .filter(Boolean)
            .join(' ')}
        >
          {children}
        </div>

        {footer != null ? (
          <footer className={styles.footer}>{footer}</footer>
        ) : null}
      </div>
    </div>,
    document.body,
  );
}

Modal.displayName = 'Modal';
