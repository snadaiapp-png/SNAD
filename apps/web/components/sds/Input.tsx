'use client';

/*
 * ============================================================================
 *  SDS Input — Component
 * ----------------------------------------------------------------------------
 *  A labeled text input supporting `text`, `email`, `password`, and `search`
 *  types, with hint and error states. Built on SDS v2 tokens; RTL-aware.
 *
 *  Accessibility:
 *    • <label> is associated with <input> via htmlFor / id (auto-generated
 *      if not provided).
 *    • Hint and error text are linked via aria-describedby.
 *    • aria-invalid is set to "true" when an error message is provided.
 *    • Minimum 44x44 touch target enforced in CSS.
 *
 *  Forward ref: exposes the underlying <input> element.
 * ============================================================================
 */

import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
} from 'react';

import styles from './Input.module.css';

export type TextInputType =
  | 'text'
  | 'email'
  | 'password'
  | 'search'
  | 'tel'
  | 'url'
  | 'number';

export interface InputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  /** Input type. @default 'text' */
  type?: TextInputType;
  /** Visible label above the input. Required for accessibility when no
      `aria-label` is supplied. */
  label?: ReactNode;
  /** Hint text shown below the input (always visible). */
  hint?: ReactNode;
  /** Error message. When provided, sets aria-invalid="true". */
  error?: ReactNode;
  /** Show the required indicator (asterisk) on the label. */
  required?: boolean;
  /** Optional leading icon (e.g. search icon). */
  leadingIcon?: ReactNode;
  /** Optional trailing icon (e.g. clear / show-password button). */
  trailingIcon?: ReactNode;
  /** Optional className applied to the wrapper. */
  className?: string;
  /** Optional className applied to the input element. */
  inputClassName?: string;
  /** Force the input id (otherwise auto-generated). */
  id?: string;
}

/**
 * SDS Input.
 *
 * @example
 * ```tsx
 * <Input
 *   label="Email"
 *   type="email"
 *   placeholder="you@example.com"
 *   hint="We'll never share your email."
 *   required
 * />
 * ```
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    type = 'text',
    label,
    hint,
    error,
    required = false,
    leadingIcon,
    trailingIcon,
    className,
    inputClassName,
    id,
    disabled,
    'aria-describedby': ariaDescribedBy,
    ...rest
  },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;

  const hasError = Boolean(error);

  const describedBy = [
    hint ? hintId : null,
    hasError ? errorId : null,
    ariaDescribedBy ?? null,
  ]
    .filter(Boolean)
    .join(' ');

  const wrapperClassList = [styles.wrapper, className ?? '']
    .filter(Boolean)
    .join(' ');

  const inputClassList = [
    styles.input,
    leadingIcon ? styles.hasLeadingIcon : '',
    trailingIcon ? styles.hasTrailingIcon : '',
    inputClassName ?? '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={wrapperClassList}>
      {label != null ? (
        <label htmlFor={inputId} className={styles.label}>
          {label}
          {required ? (
            <span aria-hidden="true" className={styles.labelRequired} />
          ) : null}
        </label>
      ) : null}

      <div className={styles.inputWrapper}>
        {leadingIcon != null ? (
          <span aria-hidden="true" className={styles.leadingIcon}>
            {leadingIcon}
          </span>
        ) : null}

        <input
          ref={ref}
          id={inputId}
          type={type}
          className={inputClassList}
          aria-invalid={hasError || undefined}
          aria-describedby={describedBy || undefined}
          aria-required={required || undefined}
          disabled={disabled}
          required={required}
          {...rest}
        />

        {trailingIcon != null ? (
          <span aria-hidden="true" className={styles.trailingIcon}>
            {trailingIcon}
          </span>
        ) : null}
      </div>

      {hint != null && !hasError ? (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      ) : null}

      {hasError ? (
        <p id={errorId} className={styles.error} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
});

Input.displayName = 'Input';
