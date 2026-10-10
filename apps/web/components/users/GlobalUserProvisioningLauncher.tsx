"use client";

import { FormEvent, useRef, useState } from "react";
import { Button, Input } from "@/components/sds";
import { Modal } from "@/components/sds/Modal";
import { useAuth } from "@/lib/auth/auth-provider";
import { usersApi, type ModuleProvisioningContext } from "@/lib/api/users";
import { ApiInputValidationError, type ApiInputField } from "@/lib/api/errors";
import { normalizeUserCreationInput } from "@/lib/users/user-create-validation";
import { capabilityDisplayName } from "@/lib/i18n/iam-display-l10n";
import { toUserFacingMessage } from "@/lib/api/user-facing-errors";
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

export function moduleContextFromLocation(pathname: string, search: string): string {
  const scoped = new URLSearchParams(search).get("module")?.trim().toLowerCase();
  return scoped || moduleContextFromPathname(pathname);
}

export interface GlobalUserProvisioningLauncherProps {
  presentation?: "floating" | "header" | "menu";
  menuItemClassName?: string;
  menuIconClassName?: string;
  menuLabelClassName?: string;
}

export function GlobalUserProvisioningLauncher({
  presentation = "floating",
  menuItemClassName,
  menuIconClassName,
  menuLabelClassName,
}: GlobalUserProvisioningLauncherProps = {}) {
  const { state, user, me } = useAuth();
  const pathname =
    typeof window === "undefined" ? "/" : window.location.pathname;
  const search =
    typeof window === "undefined" ? "" : window.location.search;
  const messages = {
    create: "إضافة مستخدم",
    createTitle: "إضافة مستخدم جديد",
    subtitle: "إدارة مستخدمي مساحة العمل",
    close: "إغلاق",
    cancel: "إلغاء",
    submitCreate: "إنشاء المستخدم",
    email: "البريد الإلكتروني",
    username: "اسم المستخدم",
    displayName: "الاسم المعروض",
    mobileNumber: "رقم الجوال",
    mobileNumberHint: "يمكن إدخال الرقم السعودي محليًا 05XXXXXXXX أو دوليًا +9665XXXXXXXX.",
    mobileRegion: "رمز الدولة (ISO)",
    mobileRegionHint: "مثال: SA للسعودية.",
    initialCredential: "كلمة المرور المؤقتة",
    initialCredentialHelp: "يستطيع المستخدم تسجيل الدخول بها مرة أولى ثم يجب تغييرها قبل استخدام المنصة.",
  } as const;
  const capabilities = me?.capabilities ?? [];
  const tenantId = user?.tenantId ?? null;
  const canCreate = capabilities.includes("USER.CREATE");
  const canRead = capabilities.includes("USER.READ");
  const canGrantRole = capabilities.includes("USER.GRANT_ROLE");
  const canManageOverrides = capabilities.includes("AUTHORIZATION.OVERRIDE.MANAGE");
  const canGrantCapabilities = canGrantRole || canManageOverrides;
  const moduleContext = moduleContextFromLocation(pathname, search);
  const managementUsersRoute =
    pathname === "/management/users" || pathname.startsWith("/management/users/");
  const scopedManagementUsers = managementUsersRoute && Boolean(new URLSearchParams(search).get("module"));

  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [mobileNumber, setMobileNumber] = useState("");
  const [mobileRegion, setMobileRegion] = useState("SA");
  const [initialCredential, setInitialCredential] = useState(createInitialCredential);
  const [provisioningContext, setProvisioningContext] = useState<ModuleProvisioningContext | null>(null);
  const [selectedCapabilityCodes, setSelectedCapabilityCodes] = useState<string[]>([]);
  const [contextLoading, setContextLoading] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<ApiInputField, string>>>({});
  const errorRef = useRef<HTMLDivElement | null>(null);

  // The management/users workspace already owns its native create surface.
  // Everywhere else, including future route roots, receives this launcher
  // automatically through the root Providers tree.
  if (
    state !== "AUTHENTICATED" ||
    !tenantId ||
    !canCreate ||
    !canGrantCapabilities ||
    (managementUsersRoute && !scopedManagementUsers)
  ) {
    return null;
  }

  const reset = () => {
    setEmail("");
    setUsername("");
    setDisplayName("");
    setMobileNumber("");
    setMobileRegion("SA");
    setInitialCredential(createInitialCredential());
    setProvisioningContext(null);
    setSelectedCapabilityCodes([]);
    setContextLoading(false);
    setError(null);
    setFieldErrors({});
  };

  const close = () => {
    if (busy) return;
    setOpen(false);
    reset();
  };

  const openProvisioning = async () => {
    if (!tenantId || busy) return;
    setOpen(true);
    setContextLoading(true);
    setProvisioningContext(null);
    setSelectedCapabilityCodes([]);
    setError(null);
    setFieldErrors({});
    try {
      const context = await usersApi.moduleProvisioningContext(tenantId, moduleContext);
      setProvisioningContext(context);
    } catch (caught) {
      const message = toUserFacingMessage(caught);
      setError(message);
      if (caught instanceof ApiInputValidationError) {
        setFieldErrors({ [caught.field]: message });
        requestAnimationFrame(() => {
          document.getElementById(`global-user-${caught.field}`)?.focus();
        });
      } else {
        requestAnimationFrame(() => errorRef.current?.scrollIntoView({ block: "nearest" }));
      }
    } finally {
      setContextLoading(false);
    }
  };

  const toggleCapability = (capabilityCode: string) => {
    setSelectedCapabilityCodes((current) =>
      current.includes(capabilityCode)
        ? current.filter((candidate) => candidate !== capabilityCode)
        : [...current, capabilityCode],
    );
  };

  const grantSelectedModuleAccess = async (userId: string) => {
    if (!tenantId) return;

    // Keep each permission batch inside one backend transaction. Platform
    // authorization operators use the override-authorized endpoint; tenant
    // administrators use the USER.GRANT_ROLE endpoint. Both validate module
    // namespace ownership server-side before mutating authorization state.
    if (canManageOverrides) {
      await usersApi.grantModuleCapabilityOverrides(
        tenantId,
        userId,
        moduleContext,
        selectedCapabilityCodes,
      );
      return;
    }

    await usersApi.grantModuleCapabilities(
      tenantId,
      userId,
      moduleContext,
      selectedCapabilityCodes,
    );
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tenantId || busy) return;
    if (!provisioningContext) {
      if (!error) setError("تعذر تحميل صلاحيات الموديول. أعد المحاولة.");
      return;
    }
    if (selectedCapabilityCodes.length === 0) {
      setError("اختر صلاحية واحدة على الأقل من صلاحيات الموديول.");
      return;
    }

    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      const normalizedEmail = email.trim().toLowerCase();

      // Idempotent UX: if the actor may read Users, reuse the canonical
      // tenant user instead of attempting to create a duplicate record.
      if (canRead) {
        const existing = (await usersApi.list(tenantId)).find(
          (candidate) => candidate.email.trim().toLowerCase() === normalizedEmail,
        );
        if (existing) {
          await grantSelectedModuleAccess(existing.id);
          setOpen(false);
          reset();
          window.history.pushState({}, "",
            `/management/users/${existing.id}?returnTo=${encodeURIComponent(`${pathname}${search}`)}`,
          );
          return;
        }
      }

      const normalizedInput = normalizeUserCreationInput({
        email,
        username,
        displayName,
        mobileNumber,
        mobileRegion,
        initialCredential,
      });
      const created = await usersApi.create(tenantId, normalizedInput);
      await grantSelectedModuleAccess(created.id);
      setOpen(false);
      reset();
      window.history.pushState({}, "", 
        `/management/users/${created.id}?returnTo=${encodeURIComponent(`${pathname}${search}`)}`,
      );
    } catch (caught) {
      setError(toUserFacingMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className={presentation === "header" ? styles.headerLauncher : presentation === "menu" ? styles.menuLauncher : styles.launcher}
      data-testid="global-user-provisioning"
      data-module-context={moduleContext}
      data-presentation={presentation}
    >
      {presentation === "menu" ? (
        <button
          type="button"
          className={menuItemClassName}
          onClick={openProvisioning}
          aria-label={messages.create}
          data-testid="module-user-provisioning-menu-item"
        >
          <span className={menuIconClassName} aria-hidden="true">
            <UserPlusGlyph />
          </span>
          <span className={menuLabelClassName}>{messages.create}</span>
        </button>
      ) : (
        <Button
          className={presentation === "header" ? styles.headerTrigger : styles.trigger}
          onClick={openProvisioning}
          aria-label={messages.create}
        >
          <span className={styles.triggerIcon} aria-hidden="true">
            <UserPlusGlyph />
          </span>
          <span>{messages.create}</span>
        </Button>
      )}

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
            <Button type="submit" form="global-user-provisioning-form" loading={busy} disabled={busy || contextLoading || !provisioningContext}>
              {messages.submitCreate}
            </Button>
          </div>
        }
      >
        <form id="global-user-provisioning-form" className={styles.form} onSubmit={submit}>
          {error ? <div ref={errorRef} className={styles.alert} role="alert" tabIndex={-1}>{error}</div> : null}
          <Input id="global-user-email" type="email" label={messages.email} required maxLength={255} error={fieldErrors.email} value={email} onChange={(event) => { setEmail(event.target.value); setFieldErrors((current) => ({ ...current, email: undefined })); }} />
          <Input id="global-user-username" label={messages.username} required minLength={3} maxLength={100} error={fieldErrors.username} value={username} onChange={(event) => { setUsername(event.target.value); setFieldErrors((current) => ({ ...current, username: undefined })); }} />
          <Input id="global-user-displayName" label={messages.displayName} maxLength={200} error={fieldErrors.displayName} value={displayName} onChange={(event) => { setDisplayName(event.target.value); setFieldErrors((current) => ({ ...current, displayName: undefined })); }} />
          <Input
            id="global-user-mobileNumber"
            type="tel"
            inputMode="tel"
            label={messages.mobileNumber}
            hint={messages.mobileNumberHint}
            error={fieldErrors.mobileNumber}
            placeholder="05XXXXXXXX"
            value={mobileNumber}
            onChange={(event) => { setMobileNumber(event.target.value); setFieldErrors((current) => ({ ...current, mobileNumber: undefined })); }}
          />
          <Input
            id="global-user-mobileRegion"
            label={messages.mobileRegion}
            hint={messages.mobileRegionHint}
            error={fieldErrors.mobileRegion}
            value={mobileRegion}
            maxLength={2}
            pattern="[A-Za-z]{2}"
            autoCapitalize="characters"
            onChange={(event) => { setMobileRegion(event.target.value.toUpperCase()); setFieldErrors((current) => ({ ...current, mobileRegion: undefined })); }}
          />
          <Input
            type="password"
            label={messages.initialCredential}
            required
            minLength={8}
            maxLength={256}
            autoComplete="new-password"
            id="global-user-initialCredential"
            error={fieldErrors.initialCredential}
            value={initialCredential}
            onChange={(event) => { setInitialCredential(event.target.value); setFieldErrors((current) => ({ ...current, initialCredential: undefined })); }}
          />
          <p className={styles.help}>{messages.initialCredentialHelp}</p>

          <fieldset className={styles.moduleAccess} disabled={contextLoading || busy}>
            <legend>صلاحيات الموديول</legend>
            {contextLoading ? (
              <p className={styles.help}>جارٍ تحميل صلاحيات الموديول…</p>
            ) : provisioningContext ? (
              <>
                <p className={styles.help}>
                  {provisioningContext.localizedName || provisioningContext.name} — تظهر هنا صلاحيات هذا الموديول فقط، ولا تُعرض صلاحيات الموديولات الأخرى.
                </p>
                {provisioningContext.declaredCapabilities.length === 0 ? (
                    <div className={styles.alert} role="alert">لا توجد صلاحيات نشطة مسجلة لهذا الموديول.</div>
                  ) : (
                    <div className={styles.roleList}>
                      {provisioningContext.declaredCapabilities.map((capabilityCode) => (
                        <label key={capabilityCode} className={styles.roleOption}>
                          <input
                            type="checkbox"
                            checked={selectedCapabilityCodes.includes(capabilityCode)}
                            onChange={() => toggleCapability(capabilityCode)}
                          />
                          <span>
                            <strong>{capabilityDisplayName(capabilityCode, null, "ar")}</strong>
                            <small>{capabilityCode}</small>
                          </span>
                        </label>
                      ))}
                    </div>
                  )
                }
              </>
            ) : null}
          </fieldset>
        </form>
      </Modal>
    </div>
  );
}
