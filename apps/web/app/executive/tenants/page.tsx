"use client";

import { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { scpApi, type PageResponse, type TenantRow } from "@/lib/api/scp-api";
import { executiveApi, type ManagedTenant } from "@/lib/api/executive-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button, Input } from "@/components/sds/executive";
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
import type { TenantDialog } from "./tenant-dialog-validation";
import { validateCreateTenant, validateEditTenant } from "./tenant-dialog-validation";

// Mutation dialogs are non-critical admin panels: the dialog module loads only
// after an operator opens one, keeping the initial /executive/tenants payload
// within the fail-closed performance budget. Capability gating, form state and
// every API call remain owned by this page component, so the authorization
// surface and fail-closed behavior are unchanged and nothing security-relevant
// is lazily gated.
const TenantDialogs = dynamic(() => import("./TenantDialogs"), { ssr: false });

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

type Translate = (key: string) => string;

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

      {dialog ? (
        <TenantDialogs
          dialog={dialog}
          busy={busy}
          dialogError={dialogError}
          createForm={createForm}
          editForm={editForm}
          reason={reason}
          setDialogError={setDialogError}
          setCreateForm={setCreateForm}
          setEditForm={setEditForm}
          setReason={setReason}
          closeDialog={() => setDialog(null)}
          onCreate={() => void createTenant()}
          onUpdate={(tenantId) => void updateTenant(tenantId)}
          onApplyStatus={(tenantId, targetStatus) => void applyStatus(tenantId, targetStatus)}
        />
      ) : null}
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
