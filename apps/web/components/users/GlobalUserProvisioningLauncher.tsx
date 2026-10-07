"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Button, Input } from "@/components/sds";
import { Modal } from "@/components/sds/Modal";
import { useAuth } from "@/lib/auth/auth-provider";
import { usersApi, type ModuleProvisioningContext } from "@/lib/api/users";
import { tenantAccessApi } from "@/lib/api/tenant-access";
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

function UserPlusGlyph() {
  return (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" focusable={false}>
      <circle cx="7.5" cy="6.5" r="3" />
      <path d="M2.5 16c.7-3 2.5-4.5 5-4.5 1.4 0 2.6.5 3.5 1.4" />
      <path d="M15 8.5v6M12 11.5h6" />
    </svg>
  );
}

export function moduleContextFromPathname(pathname: string): string {
  const segment = pathname.split("/").filter(Boolean)[0];
  return segment || "workspace";
}

export interface GlobalUserProvisioningLauncherProps {
  presentation?: "floating" | "header";
}

export function GlobalUserProvisioningLauncher({
  presentation = "floating",
}: GlobalUserProvisioningLauncherProps = {}) {
  const { state, user, me } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const { t } = useI18n();
  const messages = useMemo(() => usersMessages(t), [t]);
  const capabilities = me?.capabilities ?? [];
  const tenantId = user?.tenantId ?? null;
  const canCreate = capabilities.includes("USER.CREATE");
  const canRead = capabilities.includes("USER.READ");
  const canGrantRole = capabilities.includes("USER.GRANT_ROLE");
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
  const [provisioningContext, setProvisioningContext] = useState<ModuleProvisioningContext | null>(null);
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([]);
  const [contextLoading, setContextLoading] = useState(false);

  // The management/users workspace already owns its native create surface.
  // Everywhere else, including future route roots, receives this launcher
  // automatically through the root Providers tree.
  if (
    state !== "AUTHENTICATED" ||
    !tenantId ||
    !canCreate ||
    !canGrantRole ||
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
    setProvisioningContext(null);
    setSelectedRoleIds([]);
    setContextLoading(false);
    setError(null);
  };

  const close = () => {
    if (busy) return;
    setOpen(false);
    reset();
  };

  useEffect(() => {
    if (!open || !tenantId) return;
    let cancelled = false;
    setContextLoading(true);
    setError(null);
    usersApi.moduleProvisioningContext(tenantId, moduleContext)
      .then((context) => {
        if (cancelled) return;
        setProvisioningContext(context);
        setSelectedRoleIds([]);
      })
      .catch((caught) => {
        if (!cancelled) setError(toUserFacingMessage(caught));
      })
      .finally(() => {
        if (!cancelled) setContextLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, tenantId, moduleContext]);

  const toggleRole = (roleId: string) => {
    setSelectedRoleIds((current) =>
      current.includes(roleId)
        ? current.filter((candidate) => candidate !== roleId)
        : [...current, roleId],
    );
  };

  const grantSelectedModuleRoles = async (userId: string) => {
    if (!tenantId) return;
    for (const roleId of selectedRoleIds) {
      await tenantAccessApi.grantUserRole(tenantId, userId, roleId);
    }
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tenantId || busy) return;
    if (!provisioningContext || selectedRoleIds.length === 0) {
      setError("اختر دورًا واحدًا على الأقل من صلاحيات الموديول.");
      return;
    }

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
          await grantSelectedModuleRoles(existing.id);
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
      await grantSelectedModuleRoles(created.id);
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
      className={presentation === "header" ? styles.headerLauncher : styles.launcher}
      data-testid="global-user-provisioning"
      data-module-context={moduleContext}
      data-presentation={presentation}
    >
      <Button
        className={presentation === "header" ? styles.headerTrigger : styles.trigger}
        onClick={() => setOpen(true)}
        aria-label={messages.create}
      >
        <span className={styles.triggerIcon} aria-hidden="true">
          <UserPlusGlyph />
        </span>
        <span>{messages.create}</span>
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

          <fieldset className={styles.moduleAccess} disabled={contextLoading || busy}>
            <legend>صلاحيات الموديول</legend>
            {contextLoading ? (
              <p className={styles.help}>جارٍ تحميل الأدوار المعتمدة للموديول…</p>
            ) : provisioningContext ? (
              <>
                <p className={styles.help}>
                  {provisioningContext.localizedName || provisioningContext.name} — يتم إسناد أدوار هذا الموديول فقط، وتبقى الصلاحيات الأساسية في نظام المستخدمين المركزي دون تغيير.
                </p>
                {provisioningContext.roles.length === 0 ? (
                  <div className={styles.alert} role="alert">لا توجد أدوار module-only معتمدة لهذا الموديول.</div>
                ) : (
                  <div className={styles.roleList}>
                    {provisioningContext.roles.map((role) => (
                      <label key={role.roleId} className={styles.roleOption}>
                        <input
                          type="checkbox"
                          checked={selectedRoleIds.includes(role.roleId)}
                          onChange={() => toggleRole(role.roleId)}
                        />
                        <span>
                          <strong>{role.roleName}</strong>
                          <small>{role.capabilities.join(" · ")}</small>
                        </span>
                      </label>
                    ))}
                  </div>
                )}
              </>
            ) : null}
          </fieldset>
        </form>
      </Modal>
    </div>
  );
}
