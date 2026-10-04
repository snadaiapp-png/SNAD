"use client";

import { FormEvent, useState } from "react";
import { Button, Input } from "@/components/sds";
import { Modal } from "@/components/sds/Modal";
import type { UsersMessages } from "@/lib/i18n/users-l10n";

interface UserCreateDialogProps {
  open: boolean;
  busy: boolean;
  messages: UsersMessages;
  onClose: () => void;
  onSubmit: (input: { email: string; username: string; displayName?: string | null; mobileNumber?: string | null; mobileRegion?: string | null }) => Promise<void>;
}

export function UserCreateDialog({ open, busy, messages, onClose, onSubmit }: UserCreateDialogProps) {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [mobileNumber, setMobileNumber] = useState("");
  const [mobileRegion, setMobileRegion] = useState("");

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSubmit({ email, username, displayName, mobileNumber, mobileRegion });
    setEmail("");
    setUsername("");
    setDisplayName("");
    setMobileNumber("");
    setMobileRegion("");
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
          <Input type="email" label={messages.email} required value={email} onChange={(event) => setEmail(event.target.value)} />
          <Input label={messages.username} required value={username} onChange={(event) => setUsername(event.target.value)} />
          <Input label={messages.displayName} value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
          <Input label={messages.mobileNumber} value={mobileNumber} onChange={(event) => setMobileNumber(event.target.value)} />
          <Input label={messages.mobileRegion} value={mobileRegion} maxLength={2} onChange={(event) => setMobileRegion(event.target.value)} />
        </div>
      </form>
    </Modal>
  );
}
