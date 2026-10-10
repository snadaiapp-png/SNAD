"use client";

import { FormEvent, useState } from "react";
import { ApiInputValidationError, type ApiInputField } from "@/lib/api/errors";
import { toUserFacingMessage } from "@/lib/api/user-facing-errors";
import { normalizeUserCreationInput } from "@/lib/users/user-create-validation";
import { Button, Input } from "@/components/sds";
import { Modal } from "@/components/sds/Modal";
import type { UsersMessages } from "@/lib/i18n/users-l10n";

interface UserCreateDialogProps {
  open: boolean;
  busy: boolean;
  messages: UsersMessages;
  onClose: () => void;
  error?: string | null;
  onSubmit: (input: { email: string; username: string | null; displayName?: string | null; mobileNumber?: string | null; mobileRegion?: string | null; initialCredential?: string }) => Promise<boolean>;
}

function createInitialCredential(): string {
  if (process.env.NODE_ENV !== "production") {
    return "12345678";
  }
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
  const values = new Uint32Array(20);
  crypto.getRandomValues(values);
  return Array.from(values, (value) => alphabet[value % alphabet.length]).join("");
}

export function UserCreateDialog({ open, busy, messages, onClose, error, onSubmit }: UserCreateDialogProps) {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [mobileNumber, setMobileNumber] = useState("");
  const [mobileRegion, setMobileRegion] = useState("SA");
  const [localError, setLocalError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<ApiInputField, string>>>({});
  const [initialCredential, setInitialCredential] = useState(createInitialCredential);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLocalError(null);
    setFieldErrors({});
    try {
      const normalized = normalizeUserCreationInput({
        email,
        username,
        displayName,
        mobileNumber,
        mobileRegion,
        initialCredential,
      });
      const created = await onSubmit(normalized);
      if (!created) return;

      setEmail("");
      setUsername("");
      setDisplayName("");
      setMobileNumber("");
      setMobileRegion("SA");
      setInitialCredential(createInitialCredential());
    } catch (caught) {
      const message = toUserFacingMessage(caught);
      setLocalError(message);
      if (caught instanceof ApiInputValidationError) {
        setFieldErrors({ [caught.field]: message });
        requestAnimationFrame(() => {
          document.getElementById(`tenant-user-${caught.field}`)?.focus();
        });
      }
    }
  };

  return (
    <Modal
      isOpen={open}
      onClose={busy ? () => undefined : onClose}
      title={messages.createTitle}
      closeButtonLabel={messages.close}
      footer={
        <div style={{ display: "flex", gap: "var(--snad-space-2, 8px)", justifyContent: "flex-end" }}>
          <Button variant="secondary" disabled={busy} onClick={onClose}>{messages.cancel}</Button>
          <Button type="submit" form="tenant-user-create-form" loading={busy}>{messages.submitCreate}</Button>
        </div>
      }
    >
      <form id="tenant-user-create-form" onSubmit={submit}>
        <div style={{ display: "grid", gap: "var(--snad-space-4, 16px)" }}>
          {localError || error ? <div role="alert" tabIndex={-1}>{localError || error}</div> : null}
          <Input id="tenant-user-email" type="email" label={messages.email} required maxLength={255} error={fieldErrors.email} value={email} onChange={(event) => { setEmail(event.target.value); setFieldErrors((current) => ({ ...current, email: undefined })); }} />
          <Input id="tenant-user-username" label={messages.username} required minLength={3} maxLength={100} error={fieldErrors.username} value={username} onChange={(event) => { setUsername(event.target.value); setFieldErrors((current) => ({ ...current, username: undefined })); }} />
          <Input id="tenant-user-displayName" label={messages.displayName} maxLength={200} error={fieldErrors.displayName} value={displayName} onChange={(event) => { setDisplayName(event.target.value); setFieldErrors((current) => ({ ...current, displayName: undefined })); }} />
          <Input id="tenant-user-mobileNumber" type="tel" inputMode="tel" label={messages.mobileNumber} hint={messages.mobileNumberHint} error={fieldErrors.mobileNumber} placeholder="05XXXXXXXX" value={mobileNumber} onChange={(event) => { setMobileNumber(event.target.value); setFieldErrors((current) => ({ ...current, mobileNumber: undefined })); }} />
          <Input id="tenant-user-mobileRegion" label={messages.mobileRegion} hint={messages.mobileRegionHint} error={fieldErrors.mobileRegion} value={mobileRegion} maxLength={2} autoCapitalize="characters" onChange={(event) => { setMobileRegion(event.target.value.toUpperCase()); setFieldErrors((current) => ({ ...current, mobileRegion: undefined })); }} />
          <Input
            type="password"
            label={messages.initialCredential}
            required
            minLength={8}
            maxLength={256}
            autoComplete="new-password"
            id="tenant-user-initialCredential"
            error={fieldErrors.initialCredential}
            value={initialCredential}
            onChange={(event) => { setInitialCredential(event.target.value); setFieldErrors((current) => ({ ...current, initialCredential: undefined })); }}
          />
          <p>{messages.initialCredentialHelp}</p>
        </div>
      </form>
    </Modal>
  );
}
