"use client";

import { useState, type FormEvent } from "react";
import styles from "./auth.module.css";
import { AuthErrorAlert } from "./auth-error-alert";
import type { UserFacingError } from "@/lib/api/user-facing-errors";

interface CredentialRotationFormProps {
  onChangeCredential: (currentPassword: string, newPassword: string) => Promise<void>;
  processing: boolean;
  error: UserFacingError | null;
  userEmail?: string;
}

export function CredentialRotationForm({
  onChangeCredential,
  processing,
  error,
  userEmail,
}: CredentialRotationFormProps) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const isProcessing = processing || submitting;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (isProcessing) {
      return;
    }

    if (!currentPassword) {
      setValidationError("كلمة المرور الحالية مطلوبة.");
      return;
    }
    if (newPassword.length < 8) {
      setValidationError("يجب أن تكون كلمة المرور الجديدة 8 أحرف على الأقل.");
      return;
    }
    if (newPassword !== confirmation) {
      setValidationError("كلمتا المرور الجديدتان غير متطابقتين.");
      return;
    }
    if (newPassword === currentPassword) {
      setValidationError("كلمة المرور الجديدة يجب أن تكون مختلفة عن الحالية.");
      return;
    }

    setValidationError(null);
    setSubmitting(true);

    try {
      await onChangeCredential(currentPassword, newPassword);
    } catch {
      // AuthProvider maps and exposes the safe user-facing error.
      // Do not rethrow from the browser event handler.
    } finally {
      setSubmitting(false);
    }
  }

  const displayError = validationError
    ? { title: "تحقق من البيانات", message: validationError, kind: "validation" as const }
    : error;

  return (
    <div className={styles.authShell}>
      <AuthIntelligenceVisualStatic />
      <div className={styles.loginPanel}>
        <div className={styles.loginCard}>
          <div className={styles.loginBrandMark}>SNAD</div>
          <h1 className={styles.loginWelcomeTitle}>تحديث كلمة المرور</h1>
          <p className={styles.loginWelcomeSubtitle}>
            يلزم تغيير كلمة المرور لإكمال تسجيل الدخول.
            {userEmail && <br />}
            {userEmail && (
              <span dir="ltr" style={{ display: "inline-block", marginTop: "0.25rem" }}>
                {userEmail}
              </span>
            )}
          </p>

          {displayError && <AuthErrorAlert error={displayError} />}

          <form onSubmit={handleSubmit} noValidate>
            <PasswordField
              id="current-password"
              label="كلمة المرور الحالية"
              autoComplete="current-password"
              value={currentPassword}
              onChange={setCurrentPassword}
              show={showCurrent}
              onToggle={() => setShowCurrent(!showCurrent)}
              disabled={isProcessing}
            />

            <PasswordField
              id="new-password"
              label="كلمة المرور الجديدة"
              autoComplete="new-password"
              value={newPassword}
              onChange={setNewPassword}
              show={showNew}
              onToggle={() => setShowNew(!showNew)}
              disabled={isProcessing}
            />

            <PasswordField
              id="confirm-password"
              label="تأكيد كلمة المرور الجديدة"
              autoComplete="new-password"
              value={confirmation}
              onChange={setConfirmation}
              show={showConfirm}
              onToggle={() => setShowConfirm(!showConfirm)}
              disabled={isProcessing}
            />

            <button
              type="submit"
              className={styles.authSubmit}
              disabled={isProcessing}
              aria-busy={isProcessing}
            >
              {isProcessing ? "جارٍ التحديث…" : "تحديث كلمة المرور"}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

interface PasswordFieldProps {
  id: string;
  label: string;
  autoComplete: string;
  value: string;
  onChange: (value: string) => void;
  show: boolean;
  onToggle: () => void;
  disabled: boolean;
}

function PasswordField({
  id,
  label,
  autoComplete,
  value,
  onChange,
  show,
  onToggle,
  disabled,
}: PasswordFieldProps) {
  return (
    <div className={styles.authField}>
      <label htmlFor={id} className={styles.authLabel}>
        {label}
      </label>
      <div className={styles.authInputWrapper}>
        <input
          id={id}
          type={show ? "text" : "password"}
          autoComplete={autoComplete}
          dir="ltr"
          className={styles.authInput}
          placeholder="••••••••"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          required
        />
        <button
          type="button"
          className={styles.passwordToggle}
          onClick={onToggle}
          aria-label={show ? `إخفاء ${label}` : `إظهار ${label}`}
          tabIndex={0}
        >
          {show ? (
            <svg width="18" height="18" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path d="M3.28 2.22a.75.75 0 00-1.06 1.06l14.5 14.5a.75.75 0 101.06-1.06l-1.745-1.745A10.029 10.029 0 0020 10c0-1.2-.21-2.35-.6-3.4a.75.75 0 00-1.42.5c.32.9.52 1.88.52 2.9 0 1.17-.26 2.29-.72 3.3l-1.76-1.76A4.5 4.5 0 007.5 5.5c0-.23.02-.46.05-.69L3.28 2.22zM7.5 10a2.5 2.5 0 003.54 2.54l-3.04-3.04A2.5 2.5 0 007.5 10z" />
              <path d="M10 3.5c-.73 0-1.44.08-2.13.23l1.6 1.6A4.5 4.5 0 0115.5 10c0 .23-.02.46-.05.69l1.76 1.76A8.5 8.5 0 0010 3.5z" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path d="M10 12.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z" />
              <path fillRule="evenodd" d="M.664 10.59a.75.75 0 010-1.18l.165-.12A8.5 8.5 0 0110 1.5c1.84 0 3.56.58 4.97 1.58l.16-.12a.75.75 0 01.93 1.18l-.16.12A8.5 8.5 0 0110 18.5a8.5 8.5 0 01-5.17-1.76l-.16.12a.75.75 0 01-.93-1.18l.16-.12A8.46 8.46 0 01.664 10.59zM3.5 10a6.5 6.5 0 1113 0 6.5 6.5 0 01-13 0z" clipRule="evenodd" />
            </svg>
          )}
        </button>
      </div>
    </div>
  );
}

/** Static import to avoid circular dependency warning */
import { AuthIntelligenceVisual as AuthIntelligenceVisualStatic } from "./auth-intelligence-visual";
