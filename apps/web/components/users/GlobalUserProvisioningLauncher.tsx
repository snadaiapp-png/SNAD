"use client";

import { FormEvent, useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Button, Input } from "@/components/sds";
import { Modal } from "@/components/sds/Modal";
import { useAuth } from "@/lib/auth/auth-provider";
import { usersApi } from "@/lib/api/users";
import { toUserFacingMessage } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { usersMessages } from "@/lib/i18n/users-l10n";
import styles from "./GlobalUserProvisioningLauncher.module.css";

function createInitialCredential(): string {
  if (process.env.NODE_ENV !== "production") return "12345678";
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
  const values = new Uint32Array(20);
  crypto.getRandomValues(values);
  return Array.from(values, (value) => alphabet[value % alphabet.length]).join("");
}

export function moduleContextFromPathname(pathname: string): string {
  const segment = pathname.split("/").filter(Boolean)[0];
  return segment || "workspace";
}

export function GlobalUserProvisioningLauncher() {
  const { state, user, me } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const { t } = useI18n();
  const messages = useMemo(() => usersMessages(t), [t]);
  const capabilities = me?.capabilities ?? [];
  const tenantId = user?.tenantId ?? null;
  const canCreate = capabilities.includes("USER.CREATE");
  const canRead = capabilities.includes("USER.READ");
  const moduleContext = moduleContextFromPathname(pathname);

  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [mobileNumber, setMobileNumber] = useState("");
  const [mobileRegion, setMobileRegion] = useState("");
  const [initialCredential, setInitialCredential] = useState(createInitialCredential);

  // The management/users workspace already owns its native create surface.
  // Everywhere else, including future route roots, receives this launcher
  // automatically through the root Providers tree.
  if (
    state !== "AUTHENTICATED" ||
    !tenantId ||
    !canCreate ||
    pathname === "/management/users" ||
    pathname.startsWith("/management/users/")
  ) {
    return null;
  }

  const reset = () => {
    setEmail("");
    setUsername("");
    setDisplayName("");
    setMobileNumber("");
    setMobileRegion("");
    setInitialCredential(createInitialCredential());
    setError(null);
  };

  const close = () => {
    if (busy) return;
    setOpen(false);
    reset();
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tenantId || busy) return;

    setBusy(true);
    setError(null);
    try {
      const normalizedEmail = email.trim().toLowerCase();

      // Idempotent UX: if the actor may read Users, reuse the canonical
      // tenant user instead of attempting to create a duplicate record.
      if (canRead) {
        const existing = (await usersApi.list(tenantId)).find(
          (candidate) => candidate.email.trim().toLowerCase() === normalizedEmail,
        );
        if (existing) {
          setOpen(false);
          reset();
          router.push(
            `/management/users/${existing.id}?returnTo=${encodeURIComponent(pathname)}`,
          );
          return;
        }
      }

      const created = await usersApi.create(tenantId, {
        email,
        username,
        displayName,
        mobileNumber,
        mobileRegion,
        initialCredential,
      });
      setOpen(false);
      reset();
      router.push(
        `/management/users/${created.id}?returnTo=${encodeURIComponent(pathname)}`,
      );
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className={styles.launcher}
      data-testid="global-user-provisioning"
      data-module-context={moduleContext}
    >
      <Button
        className={styles.trigger}
        onClick={() => setOpen(true)}
        aria-label={messages.create}
      >
        {messages.create}
      </Button>

      <Modal
        isOpen={open}
        onClose={close}
        title={messages.createTitle}
        subtitle={messages.subtitle}
        closeButtonLabel={messages.close}
        size="md"
        footer={
          <div className={styles.footer}>
            <Button variant="secondary" disabled={busy} onClick={close}>
              {messages.cancel}
            </Button>
            <Button type="submit" form="global-user-provisioning-form" loading={busy}>
              {messages.submitCreate}
            </Button>
          </div>
        }
      >
        <form id="global-user-provisioning-form" className={styles.form} onSubmit={submit}>
          {error ? <div className={styles.alert} role="alert">{error}</div> : null}
          <Input type="email" label={messages.email} required value={email} onChange={(event) => setEmail(event.target.value)} />
          <Input label={messages.username} required value={username} onChange={(event) => setUsername(event.target.value)} />
          <Input label={messages.displayName} value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
          <Input label={messages.mobileNumber} value={mobileNumber} onChange={(event) => setMobileNumber(event.target.value)} />
          <Input label={messages.mobileRegion} value={mobileRegion} maxLength={2} onChange={(event) => setMobileRegion(event.target.value)} />
          <Input
            type="password"
            label={messages.initialCredential}
            required
            minLength={8}
            maxLength={256}
            autoComplete="new-password"
            value={initialCredential}
            onChange={(event) => setInitialCredential(event.target.value)}
          />
          <p className={styles.help}>{messages.initialCredentialHelp}</p>
        </form>
      </Modal>
    </div>
  );
}
