"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { scpApi, type PageResponse, type TenantRow } from "@/lib/api/scp-api";
import { executiveApi, type ManagedTenant } from "@/lib/api/executive-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input, Modal } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpNotice,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import { useScpFormat } from "../_components/format";
import { useScpAccess } from "../_components/ScpAccess";
import { scpErrorMessage } from "../_components/scp-errors";
import styles from "../scp.module.css";

type TenantDialog =
  | { kind: "create" }
  | { kind: "edit"; tenantId: string }
  | { kind: "status"; tenantId: string; targetStatus: "SUSPENDED" | "ACTIVE" | "ARCHIVED"; sourceStatus: string }
  | null;

type CommercialAction =
  | "UPGRADE"
  | "RESUME"
  | "CREATE_SUBSCRIPTION"
  | "CREATE_SUCCESSOR"
  | "NONE"
  | "BLOCKED";

type CommercialTenantRow = TenantRow & {
  effectiveSubscriptionId?: string | null;
  billingState?: string | null;
  accessDecision?: string | null;
  commercialAction?: CommercialAction | string | null;
  anomalyCode?: string | null;
  loginAllowed?: boolean;
};

const EMPTY_CREATE = {
  name: "",
  subdomain: "",
  adminEmail: "",
  adminDisplayName: "",
  countryCode: "SA",
  locale: "ar-SA",
  timezone: "Asia/Riyadh",
  currencyCode: "SAR",
};

const EMPTY_EDIT = {
  name: "",
  legalName: "",
  billingEmail: "",
  countryCode: "",
  locale: "",
  timezone: "",
  currencyCode: "",
};

const SUBDOMAIN_PATTERN = /^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$/;
const COUNTRY_PATTERN = /^[A-Z]{2}$/;
const CURRENCY_PATTERN = /^[A-Z]{3}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const COUNTRY_CODES = (
  "AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW"
).split(" ");

const LOCALES = [
  "ar-SA", "ar-AE", "ar-EG", "ar-BH", "ar-KW", "ar-OM", "ar-QA", "ar-JO", "ar-MA",
  "en-US", "en-GB", "en-AU", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR",
  "tr-TR", "id-ID", "ms-MY", "hi-IN", "ur-PK", "zh-CN", "ja-JP", "ko-KR",
];

const FALLBACK_TIMEZONES = [
  "UTC", "Asia/Riyadh", "Asia/Dubai", "Asia/Kuwait", "Asia/Qatar", "Asia/Bahrain",
  "Asia/Muscat", "Asia/Amman", "Asia/Baghdad", "Asia/Beirut", "Africa/Cairo",
  "Africa/Casablanca", "Europe/London", "Europe/Paris", "Europe/Berlin",
  "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
  "Asia/Kolkata", "Asia/Singapore", "Asia/Tokyo", "Australia/Sydney",
];

const FALLBACK_CURRENCIES = [
  "SAR", "AED", "BHD", "KWD", "OMR", "QAR", "USD", "EUR", "GBP", "EGP", "JOD",
  "MAD", "TRY", "INR", "PKR", "CNY", "JPY", "AUD", "CAD", "SGD",
];

function supportedIntlValues(kind: "currency" | "timeZone", fallback: string[]): string[] {
  const intl = Intl as typeof Intl & { supportedValuesOf?: (key: "currency" | "timeZone") => string[] };
  try {
    const values = intl.supportedValuesOf?.(kind);
    return values && values.length > 0 ? values : fallback;
  } catch {
    return fallback;
  }
}

const TIMEZONES = supportedIntlValues("timeZone", FALLBACK_TIMEZONES);
const CURRENCY_CODES = supportedIntlValues("currency", FALLBACK_CURRENCIES);

function withCurrentOption(options: readonly string[], current: string): string[] {
  const value = current.trim();
  return value && !options.includes(value) ? [value, ...options] : [...options];
}

type Translate = (key: string) => string;

function validateCreateTenant(form: typeof EMPTY_CREATE, t: Translate): string {
  if (!form.name.trim()) return t("scp.tenants.validation.nameRequired");
  if (!SUBDOMAIN_PATTERN.test(form.subdomain.trim().toLowerCase())) {
    return t("scp.tenants.validation.subdomainInvalid");
  }
  if (!EMAIL_PATTERN.test(form.adminEmail.trim())) {
    return t("scp.tenants.validation.adminEmailInvalid");
  }
  if (!form.adminDisplayName.trim()) return t("scp.tenants.validation.adminDisplayNameRequired");
  if (form.countryCode.trim() && !COUNTRY_PATTERN.test(form.countryCode.trim().toUpperCase())) {
    return t("scp.tenants.validation.countryInvalid");
  }
  if (form.currencyCode.trim() && !CURRENCY_PATTERN.test(form.currencyCode.trim().toUpperCase())) {
    return t("scp.tenants.validation.currencyInvalid");
  }
  return "";
}

function validateEditTenant(form: typeof EMPTY_EDIT, t: Translate): string {
  if (!form.name.trim()) return t("scp.tenants.validation.nameRequired");
  if (form.billingEmail.trim() && !EMAIL_PATTERN.test(form.billingEmail.trim())) {
    return t("scp.tenants.validation.billingEmailInvalid");
  }
  if (form.countryCode.trim() && !COUNTRY_PATTERN.test(form.countryCode.trim().toUpperCase())) {
    return t("scp.tenants.validation.countryInvalid");
  }
  if (form.currencyCode.trim() && !CURRENCY_PATTERN.test(form.currencyCode.trim().toUpperCase())) {
    return t("scp.tenants.validation.currencyInvalid");
  }
  return "";
}

function commercialControl(tenant: CommercialTenantRow, t: Translate) {
  switch (tenant.commercialAction) {
    case "UPGRADE":
      return tenant.effectiveSubscriptionId ? (
        <Link href={`/executive/subscriptions/${tenant.effectiveSubscriptionId}`}>
          {t("scp.tenants.upgrade")}
        </Link>
      ) : (
        <span className={styles.appCardMeta}>{t("scp.tenants.commercialBlocked")}</span>
      );
    case "RESUME":
      return (
        <Link href={`/executive/subscriptions?tenantId=${tenant.id}&intent=resume`}>
          {t("scp.tenants.resumeSubscription")}
        </Link>
      );
    case "CREATE_SUBSCRIPTION":
      return (
        <Link href={`/executive/subscriptions?tenantId=${tenant.id}&intent=create`}>
          {t("scp.tenants.createSubscription")}
        </Link>
      );
    case "CREATE_SUCCESSOR":
      return (
        <Link href={`/executive/subscriptions?tenantId=${tenant.id}&intent=create-successor`}>
          {t("scp.tenants.createSuccessor")}
        </Link>
      );
    case "NONE":
      return null;
    case "BLOCKED":
    default:
      return <span className={styles.appCardMeta}>{t("scp.tenants.commercialBlocked")}</span>;
  }
}

/**
 * Tenant directory and management surface. Mutation controls are capability
 * gated. Commercial access/actions are backend-derived and fail closed: the
 * UI never grants login or invents a continuation action from raw statuses.
 */
export default function TenantsPage() {
  const { t } = useI18n();
  const { day } = useScpFormat();
  const { has } = useScpAccess();
  const canManage = has("EXECUTIVE_MANAGE");

  const [page, setPage] = useState<PageResponse<TenantRow> | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [pageIndex, setPageIndex] = useState(0);
  const [dialog, setDialog] = useState<TenantDialog>(null);
  const [dialogError, setDialogError] = useState("");
  const [reason, setReason] = useState("");
  const [createForm, setCreateForm] = useState(EMPTY_CREATE);
  const [editForm, setEditForm] = useState(EMPTY_EDIT);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setPage(await scpApi.tenants({
        search: search || undefined,
        status: status || undefined,
        page: pageIndex,
        size: 20,
        sort: "name",
        direction: "ASC",
      }));
    } catch (reasonValue) {
      setError(scpErrorMessage(reasonValue));
    } finally {
      setLoading(false);
    }
  }, [search, status, pageIndex]);

  useEffect(() => { void load(); }, [load]);

  async function createTenant() {
    const validationError = validateCreateTenant(createForm, t);
    if (validationError) {
      setDialogError(validationError);
      return;
    }
    setBusy(true);
    setDialogError("");
    setError("");
    setNotice("");
    try {
      await executiveApi.createTenant({
        name: createForm.name.trim(),
        subdomain: createForm.subdomain.trim().toLowerCase(),
        adminEmail: createForm.adminEmail.trim().toLowerCase(),
        adminDisplayName: createForm.adminDisplayName.trim(),
        countryCode: createForm.countryCode.trim().toUpperCase() || undefined,
        locale: createForm.locale.trim() || undefined,
        timezone: createForm.timezone.trim() || undefined,
        currencyCode: createForm.currencyCode.trim().toUpperCase() || undefined,
      });
      setDialog(null);
      setCreateForm(EMPTY_CREATE);
      setNotice(t("scp.tenants.notice.created"));
      await load();
    } catch (reasonValue) {
      setDialogError(scpErrorMessage(reasonValue));
    } finally {
      setBusy(false);
    }
  }

  async function openEdit(tenantId: string) {
    setBusy(true);
    setDialogError("");
    setError("");
    try {
      const tenant: ManagedTenant = await executiveApi.tenant(tenantId);
      setEditForm({
        name: tenant.name ?? "",
        legalName: tenant.legalName ?? "",
        billingEmail: tenant.billingEmail ?? "",
        countryCode: tenant.countryCode ?? "",
        locale: tenant.locale ?? "",
        timezone: tenant.timezone ?? "",
        currencyCode: tenant.currencyCode ?? "",
      });
      setDialog({ kind: "edit", tenantId });
    } catch (reasonValue) {
      setError(scpErrorMessage(reasonValue));
    } finally {
      setBusy(false);
    }
  }

  async function updateTenant(tenantId: string) {
    const validationError = validateEditTenant(editForm, t);
    if (validationError) {
      setDialogError(validationError);
      return;
    }
    setBusy(true);
    setDialogError("");
    setError("");
    setNotice("");
    try {
      await executiveApi.updateTenant(tenantId, {
        name: editForm.name.trim(),
        legalName: editForm.legalName.trim() || undefined,
        billingEmail: editForm.billingEmail.trim().toLowerCase() || undefined,
        countryCode: editForm.countryCode.trim().toUpperCase() || undefined,
        locale: editForm.locale.trim() || undefined,
        timezone: editForm.timezone.trim() || undefined,
        currencyCode: editForm.currencyCode.trim().toUpperCase() || undefined,
      });
      setDialog(null);
      setNotice(t("scp.tenants.notice.updated"));
      await load();
    } catch (reasonValue) {
      setDialogError(scpErrorMessage(reasonValue));
    } finally {
      setBusy(false);
    }
  }

  async function applyStatus(tenantId: string, targetStatus: string) {
    if (!reason.trim()) {
      setDialogError(t("scp.tenants.validation.reasonRequired"));
      return;
    }
    setBusy(true);
    setDialogError("");
    setError("");
    setNotice("");
    try {
      await executiveApi.changeTenantStatus(tenantId, targetStatus, reason.trim());
      setDialog(null);
      setReason("");
      setNotice(targetStatus === "ARCHIVED"
        ? t("scp.tenants.notice.archived")
        : targetStatus === "SUSPENDED"
          ? t("scp.tenants.notice.suspended")
          : t("scp.tenants.notice.reactivated"));
      await load();
    } catch (reasonValue) {
      setDialogError(scpErrorMessage(reasonValue));
    } finally {
      setBusy(false);
    }
  }

  function tenantLoginUrl(tenantId: string): string {
    // Use the already-proven frontend origin. Session isolation is handled by
    // a separate tenant refresh-cookie namespace in the BFF, so the control
    // plane session in the originating tab is never replaced.
    const url = new URL("/", window.location.origin);
    url.searchParams.set("tenantId", tenantId);
    url.searchParams.set("tenantLogin", "1");
    return url.toString();
  }

  async function handleTenantLoginLink(
    tenantId: string,
    action: "OPEN" | "COPY",
  ) {
    // Keep a live Window handle so the audited async step can navigate the tab.
    // Passing "noopener" to window.open can cause Chromium to return null even
    // when it successfully created the tab, which strands it on about:blank.
    const popup = action === "OPEN"
      ? window.open("about:blank", "_blank")
      : null;
    if (popup) {
      // `noopener` can intentionally make window.open() return null in Chromium
      // even when the tab was created. Keep the synchronous handle so we can
      // navigate it after the audit event, then sever opener immediately.
      popup.opener = null;
    }
    if (action === "OPEN" && !popup) {
      setError(t("scp.tenants.error.popupBlocked"));
      return;
    }
    if (popup) {
      // Isolate the new tab synchronously before any asynchronous work.
      popup.opener = null;
    }
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await executiveApi.recordTenantLoginLinkEvent(tenantId, action);
      const url = tenantLoginUrl(tenantId);
      if (action === "OPEN") popup!.location.href = url;
      else {
        await navigator.clipboard.writeText(url);
        setNotice(t("scp.tenants.notice.loginLinkCopied"));
      }
    } catch (reasonValue) {
      popup?.close();
      setError(scpErrorMessage(reasonValue));
    } finally {
      setBusy(false);
    }
  }

  if (loading && !page) {
    return <ScpPage title={t("scp.tenants.title")}><ScpSkeleton lines={8} /></ScpPage>;
  }

  return (
    <ScpPage title={t("scp.tenants.title")} subtitle={t("scp.tenants.subtitle")}>
      {canManage ? (
        <div className={styles.filters}>
          <Button type="button" variant="primary" size="sm" onClick={() => { setDialogError(""); setDialog({ kind: "create" }); }}>
            {t("scp.tenants.create")}
          </Button>
        </div>
      ) : null}

      <form className={styles.filters} onSubmit={(event) => { event.preventDefault(); setPageIndex(0); void load(); }}>
        <Input type="search" value={search} placeholder={t("scp.tenants.searchPlaceholder")} onChange={(event) => setSearch(event.target.value)} aria-label={t("scp.tenants.searchPlaceholder")} />
        <select value={status} onChange={(event) => { setStatus(event.target.value); setPageIndex(0); }} aria-label={t("scp.tenants.statusFilter")}>
          <option value="">{t("scp.filters.allStatuses")}</option>
          {["PENDING", "TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED"].map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
        <Button type="submit" variant="primary" size="sm">{t("scp.filters.apply")}</Button>
      </form>

      {notice ? <ScpNotice>{notice}</ScpNotice> : null}
      {error ? <ScpError message={error} onRetry={load} /> : null}

      {page && page.content.length === 0 ? <ScpEmpty message={t("scp.state.empty")} /> : page ? (
        <div className={styles.panel}>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <caption>{t("scp.tenants.count", { count: page.totalElements })}</caption>
              <thead><tr>
                <th scope="col">{t("scp.tenants.code")}</th>
                <th scope="col">{t("scp.tenants.name")}</th>
                <th scope="col">{t("scp.tenants.status")}</th>
                <th scope="col">{t("scp.tenants.country")}</th>
                <th scope="col">{t("scp.tenants.subscription")}</th>
                <th scope="col">{t("scp.tenants.createdAt")}</th>
                <th scope="col">{t("scp.common.actions")}</th>
              </tr></thead>
              <tbody>
                {page.content.map((rawTenant) => {
                  const tenant = rawTenant as CommercialTenantRow;
                  const archived = tenant.status === "ARCHIVED";
                  // Login eligibility is backend-derived (canonical commercial
                  // resolver) and fail-closed: the UI never grants login from
                  // raw subscription statuses.
                  const canUseLoginLink = canManage
                    && tenant.status === "ACTIVE"
                    && tenant.subscriptionStatus !== "TERMINATED"
                    && tenant.loginAllowed === true;
                  const canFreeze = tenant.status === "ACTIVE" || tenant.status === "PAST_DUE";
                  const canActivate = ["PENDING", "TRIAL", "PAST_DUE", "SUSPENDED"].includes(tenant.status);
                  return (
                    <tr key={tenant.id}>
                      <td data-label={t("scp.tenants.code")}>{tenant.code || tenant.id}</td>
                      <td data-label={t("scp.tenants.name")}>{tenant.name}</td>
                      <td data-label={t("scp.tenants.status")}><ScpStatusPill value={tenant.status} /></td>
                      <td data-label={t("scp.tenants.country")}>{tenant.countryCode || "—"}</td>
                      <td data-label={t("scp.tenants.subscription")}>
                        {tenant.subscriptionStatus ? <ScpStatusPill value={tenant.subscriptionStatus} /> : "—"}
                      </td>
                      <td data-label={t("scp.tenants.createdAt")}>{day(tenant.createdAt)}</td>
                      <td data-label={t("scp.common.actions")}>
                        <div className={styles.filters}>
                          <Link href={`/executive/subscriptions?tenantId=${tenant.id}`}>
                            {t("scp.tenants.viewSubscriptions")}
                          </Link>
                          {canUseLoginLink ? (
                            <>
                              <Button type="button" variant="secondary" size="sm" disabled={busy} onClick={() => void handleTenantLoginLink(tenant.id, "OPEN")}>
                                {t("scp.tenants.openLogin")}
                              </Button>
                              <Button type="button" variant="secondary" size="sm" disabled={busy} onClick={() => void handleTenantLoginLink(tenant.id, "COPY")}>
                                {t("scp.tenants.copyLogin")}
                              </Button>
                            </>
                          ) : null}
                          {canManage && !archived ? (
                            <>
                              <Button type="button" variant="secondary" size="sm" disabled={busy} onClick={() => void openEdit(tenant.id)}>
                                {t("scp.tenants.update")}
                              </Button>
                              {canFreeze ? (
                                <Button type="button" variant="secondary" size="sm" onClick={() => { setDialogError(""); setReason(""); setDialog({ kind: "status", tenantId: tenant.id, targetStatus: "SUSPENDED", sourceStatus: tenant.status }); }}>
                                  {t("scp.tenants.freeze")}
                                </Button>
                              ) : null}
                              {canActivate ? (
                                <Button type="button" variant="secondary" size="sm" onClick={() => { setDialogError(""); setReason(""); setDialog({ kind: "status", tenantId: tenant.id, targetStatus: "ACTIVE", sourceStatus: tenant.status }); }}>
                                  {tenant.status === "PENDING" || tenant.status === "TRIAL"
                                    ? t("scp.tenants.activate")
                                    : t("scp.tenants.reactivate")}
                                </Button>
                              ) : null}
                              <Button type="button" variant="danger" size="sm" onClick={() => { setDialogError(""); setReason(""); setDialog({ kind: "status", tenantId: tenant.id, targetStatus: "ARCHIVED", sourceStatus: tenant.status }); }}>
                                {t("scp.tenants.archive")}
                              </Button>
                              {commercialControl(tenant, t)}
                            </>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination page={page} onPage={setPageIndex} />
        </div>
      ) : null}

      <Modal
        isOpen={dialog?.kind === "create"}
        onClose={() => !busy && setDialog(null)}
        title={t("scp.tenants.createDialogTitle")}
        closeButtonLabel={t("common.close")}
        footer={<>
          <Button variant="secondary" disabled={busy} onClick={() => setDialog(null)}>{t("form.action.cancel")}</Button>
          <Button variant="primary" loading={busy} disabled={!createForm.name || !createForm.subdomain || !createForm.adminEmail || !createForm.adminDisplayName} onClick={() => void createTenant()}>{t("form.action.create")}</Button>
        </>}
      >
        <div className={styles.filters}>
          <Input label={t("scp.tenants.form.name")} aria-label={t("scp.tenants.form.name")} required placeholder={t("scp.tenants.form.namePlaceholder")} value={createForm.name} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, name: e.target.value }); }} />
          <Input label={t("scp.tenants.form.subdomain")} aria-label={t("scp.tenants.form.subdomain")} required placeholder="acme" hint={t("scp.tenants.form.subdomainHint")} value={createForm.subdomain} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, subdomain: e.target.value.toLowerCase().replace(/\s+/g, "") }); }} />
          <Input type="email" label={t("scp.tenants.form.adminEmail")} aria-label={t("scp.tenants.form.adminEmail")} required placeholder="admin@example.com" value={createForm.adminEmail} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, adminEmail: e.target.value }); }} />
          <Input label={t("scp.tenants.form.adminDisplayName")} aria-label={t("scp.tenants.form.adminDisplayName")} required placeholder={t("scp.tenants.form.adminDisplayNamePlaceholder")} value={createForm.adminDisplayName} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, adminDisplayName: e.target.value }); }} />
          <label>
            <span>{t("scp.tenants.form.countryCode")}</span>
            <select
              aria-label={t("scp.tenants.form.countryCode")}
              value={createForm.countryCode}
              onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, countryCode: e.target.value }); }}
            >
              <option value="">—</option>
              {COUNTRY_CODES.map((code) => <option key={code} value={code}>{code}</option>)}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.countryHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.locale")}</span>
            <select
              aria-label={t("scp.tenants.form.locale")}
              value={createForm.locale}
              onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, locale: e.target.value }); }}
            >
              <option value="">—</option>
              {LOCALES.map((locale) => <option key={locale} value={locale}>{locale}</option>)}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.localeHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.timezone")}</span>
            <select
              aria-label={t("scp.tenants.form.timezone")}
              value={createForm.timezone}
              onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, timezone: e.target.value }); }}
            >
              <option value="">—</option>
              {TIMEZONES.map((timezone) => <option key={timezone} value={timezone}>{timezone}</option>)}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.timezoneHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.currencyCode")}</span>
            <select
              aria-label={t("scp.tenants.form.currencyCode")}
              value={createForm.currencyCode}
              onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, currencyCode: e.target.value }); }}
            >
              <option value="">—</option>
              {CURRENCY_CODES.map((currency) => <option key={currency} value={currency}>{currency}</option>)}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.currencyHint")}</span>
          </label>
          {dialogError ? <ScpError message={dialogError} /> : null}
        </div>
      </Modal>

      <Modal
        isOpen={dialog?.kind === "edit"}
        onClose={() => !busy && setDialog(null)}
        title={t("scp.tenants.editDialogTitle")}
        closeButtonLabel={t("common.close")}
        footer={<>
          <Button variant="secondary" disabled={busy} onClick={() => setDialog(null)}>{t("form.action.cancel")}</Button>
          <Button variant="primary" loading={busy} disabled={!editForm.name} onClick={() => dialog?.kind === "edit" && void updateTenant(dialog.tenantId)}>{t("form.action.save")}</Button>
        </>}
      >
        <div className={styles.filters}>
          <Input label={t("scp.tenants.form.name")} aria-label={t("scp.tenants.form.name")} required value={editForm.name} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, name: e.target.value }); }} />
          <Input label={t("scp.tenants.form.legalName")} aria-label={t("scp.tenants.form.legalName")} value={editForm.legalName} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, legalName: e.target.value }); }} />
          <Input type="email" label={t("scp.tenants.form.billingEmail")} aria-label={t("scp.tenants.form.billingEmail")} value={editForm.billingEmail} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, billingEmail: e.target.value }); }} />
          <label>
            <span>{t("scp.tenants.form.countryCode")}</span>
            <select
              aria-label={t("scp.tenants.form.countryCode")}
              value={editForm.countryCode}
              onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, countryCode: e.target.value }); }}
            >
              <option value="">—</option>
              {withCurrentOption(COUNTRY_CODES, editForm.countryCode).map((code) => (
                <option key={code} value={code}>{code}</option>
              ))}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.countryHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.locale")}</span>
            <select
              aria-label={t("scp.tenants.form.locale")}
              value={editForm.locale}
              onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, locale: e.target.value }); }}
            >
              <option value="">—</option>
              {withCurrentOption(LOCALES, editForm.locale).map((locale) => (
                <option key={locale} value={locale}>{locale}</option>
              ))}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.localeHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.timezone")}</span>
            <select
              aria-label={t("scp.tenants.form.timezone")}
              value={editForm.timezone}
              onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, timezone: e.target.value }); }}
            >
              <option value="">—</option>
              {withCurrentOption(TIMEZONES, editForm.timezone).map((timezone) => (
                <option key={timezone} value={timezone}>{timezone}</option>
              ))}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.timezoneHint")}</span>
          </label>
          <label>
            <span>{t("scp.tenants.form.currencyCode")}</span>
            <select
              aria-label={t("scp.tenants.form.currencyCode")}
              value={editForm.currencyCode}
              onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, currencyCode: e.target.value }); }}
            >
              <option value="">—</option>
              {withCurrentOption(CURRENCY_CODES, editForm.currencyCode).map((currency) => (
                <option key={currency} value={currency}>{currency}</option>
              ))}
            </select>
            <span className={styles.appCardMeta}>{t("scp.tenants.form.currencyHint")}</span>
          </label>
          <p className={styles.appCardMeta}>{t("scp.tenants.form.subdomainImmutableNote")}</p>
          {dialogError ? <ScpError message={dialogError} /> : null}
        </div>
      </Modal>

      <Modal
        isOpen={dialog?.kind === "status"}
        onClose={() => !busy && setDialog(null)}
        title={dialog?.kind === "status" && dialog.targetStatus === "ARCHIVED"
          ? t("scp.tenants.archiveDialogTitle")
          : dialog?.kind === "status" && dialog.targetStatus === "SUSPENDED"
            ? t("scp.tenants.freezeDialogTitle")
            : dialog?.kind === "status" && (dialog.sourceStatus === "PENDING" || dialog.sourceStatus === "TRIAL")
              ? t("scp.tenants.activateDialogTitle")
              : t("scp.tenants.reactivateDialogTitle")}
        closeButtonLabel={t("common.close")}
        footer={<>
          <Button variant="secondary" disabled={busy} onClick={() => setDialog(null)}>{t("form.action.cancel")}</Button>
          <Button variant={dialog?.kind === "status" && dialog.targetStatus === "ARCHIVED" ? "danger" : "primary"} loading={busy} disabled={!reason.trim()} onClick={() => dialog?.kind === "status" && void applyStatus(dialog.tenantId, dialog.targetStatus)}>{t("form.action.confirm")}</Button>
        </>}
      >
        {dialog?.kind === "status" && dialog.targetStatus === "ARCHIVED" ? <p>{t("scp.tenants.archiveWarning")}</p> : null}
        <Input label={t("scp.tenants.form.reason")} aria-label={t("scp.tenants.form.reason")} required placeholder={t("scp.tenants.form.reasonPlaceholder")} value={reason} maxLength={500} onChange={(e) => { setDialogError(""); setReason(e.target.value); }} />
        {dialogError ? <ScpError message={dialogError} /> : null}
      </Modal>
    </ScpPage>
  );
}

function Pagination({ page, onPage }: { page: PageResponse<unknown>; onPage: (index: number) => void }) {
  const { t } = useI18n();
  return (
    <nav className={styles.filters} aria-label={t("scp.common.pagination")}>
      <Button variant="secondary" size="sm" disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>{t("scp.common.previous")}</Button>
      <span className={styles.appCardMeta}>{t("scp.common.pageOf", { page: page.page + 1, total: Math.max(page.totalPages, 1) })}</span>
      <Button variant="secondary" size="sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)}>{t("scp.common.next")}</Button>
    </nav>
  );
}