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
  | { kind: "status"; tenantId: string; targetStatus: "SUSPENDED" | "ACTIVE" | "ARCHIVED" }
  | null;

const EMPTY_CREATE = {
  name: "",
  subdomain: "",
  adminEmail: "",
  adminDisplayName: "",
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

/**
 * Tenant directory and management surface. All mutation controls are gated by
 * the exact broad backend authority EXECUTIVE_MANAGE. Deletion is a soft
 * delete only: the tenant transitions to ARCHIVED and no physical DELETE is
 * issued by this surface.
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
      setPage(
        await scpApi.tenants({
          search: search || undefined,
          status: status || undefined,
          page: pageIndex,
          size: 20,
          sort: "name",
          direction: "ASC",
        }),
      );
    } catch (reasonValue) {
      setError(scpErrorMessage(reasonValue));
    } finally {
      setLoading(false);
    }
  }, [search, status, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

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
      setNotice(
        targetStatus === "ARCHIVED"
          ? t("scp.tenants.notice.archived")
          : targetStatus === "SUSPENDED"
            ? t("scp.tenants.notice.suspended")
            : t("scp.tenants.notice.reactivated"),
      );
      await load();
    } catch (reasonValue) {
      setDialogError(scpErrorMessage(reasonValue));
    } finally {
      setBusy(false);
    }
  }

  function tenantLoginUrl(tenantId: string): string {
    const url = new URL("/", window.location.origin);
    url.searchParams.set("tenantId", tenantId);
    return url.toString();
  }

  async function handleTenantLoginLink(tenantId: string, action: "OPEN" | "COPY") {
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
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await executiveApi.recordTenantLoginLinkEvent(tenantId, action);
      const url = tenantLoginUrl(tenantId);
      if (action === "OPEN") {
        popup!.location.href = url;
      } else {
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
    return (
      <ScpPage title={t("scp.tenants.title")}>
        <ScpSkeleton lines={8} />
      </ScpPage>
    );
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

      <form
        className={styles.filters}
        onSubmit={(event) => {
          event.preventDefault();
          setPageIndex(0);
          void load();
        }}
      >
        <Input
          type="search"
          value={search}
          placeholder={t("scp.tenants.searchPlaceholder")}
          onChange={(event) => setSearch(event.target.value)}
          aria-label={t("scp.tenants.searchPlaceholder")}
        />
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPageIndex(0);
          }}
          aria-label={t("scp.tenants.statusFilter")}
        >
          <option value="">{t("scp.filters.allStatuses")}</option>
          {["PENDING", "TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED"].map(
            (value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ),
          )}
        </select>
        <Button type="submit" variant="primary" size="sm">
          {t("scp.filters.apply")}
        </Button>
      </form>

      {notice ? <ScpNotice>{notice}</ScpNotice> : null}
      {error ? <ScpError message={error} onRetry={load} /> : null}

      {page && page.content.length === 0 ? (
        <ScpEmpty message={t("scp.state.empty")} />
      ) : page ? (
        <div className={styles.panel}>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <caption>{t("scp.tenants.count", { count: page.totalElements })}</caption>
              <thead>
                <tr>
                  <th scope="col">{t("scp.tenants.code")}</th>
                  <th scope="col">{t("scp.tenants.name")}</th>
                  <th scope="col">{t("scp.tenants.status")}</th>
                  <th scope="col">{t("scp.tenants.country")}</th>
                  <th scope="col">{t("scp.tenants.subscription")}</th>
                  <th scope="col">{t("scp.tenants.createdAt")}</th>
                  <th scope="col">{t("scp.common.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((tenant) => {
                  const archived = tenant.status === "ARCHIVED";
                  const canUseLoginLink = canManage
                    && tenant.status === "ACTIVE"
                    && tenant.subscriptionStatus !== "TERMINATED";
                  const canFreeze = tenant.status === "ACTIVE" || tenant.status === "PAST_DUE";
                  const canReactivate = tenant.status === "SUSPENDED";
                  return (
                    <tr key={tenant.id}>
                      <td data-label={t("scp.tenants.code")}>{tenant.code || tenant.id}</td>
                      <td data-label={t("scp.tenants.name")}>{tenant.name}</td>
                      <td data-label={t("scp.tenants.status")}>
                        <ScpStatusPill value={tenant.status} />
                      </td>
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
                                <Button type="button" variant="secondary" size="sm" onClick={() => { setDialogError(""); setReason(""); setDialog({ kind: "status", tenantId: tenant.id, targetStatus: "SUSPENDED" }); }}>
                                  {t("scp.tenants.freeze")}
                                </Button>
                              ) : null}
                              {canReactivate ? (
                                <Button type="button" variant="secondary" size="sm" onClick={() => { setDialogError(""); setReason(""); setDialog({ kind: "status", tenantId: tenant.id, targetStatus: "ACTIVE" }); }}>
                                  {t("scp.tenants.reactivate")}
                                </Button>
                              ) : null}
                              <Button type="button" variant="danger" size="sm" onClick={() => { setDialogError(""); setReason(""); setDialog({ kind: "status", tenantId: tenant.id, targetStatus: "ARCHIVED" }); }}>
                                {t("scp.tenants.archive")}
                              </Button>
                              <Link href={`/executive/subscriptions?tenantId=${tenant.id}&intent=upgrade`}>
                                {t("scp.tenants.upgrade")}
                              </Link>
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
        footer={
          <>
            <Button variant="secondary" disabled={busy} onClick={() => setDialog(null)}>{t("form.action.cancel")}</Button>
            <Button variant="primary" loading={busy} disabled={!createForm.name || !createForm.subdomain || !createForm.adminEmail || !createForm.adminDisplayName} onClick={() => void createTenant()}>
              {t("form.action.create")}
            </Button>
          </>
        }
      >
        <div className={styles.filters}>
          <Input label={t("scp.tenants.form.name")} aria-label={t("scp.tenants.form.name")} required placeholder={t("scp.tenants.form.namePlaceholder")} value={createForm.name} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, name: e.target.value }); }} />
          <Input label={t("scp.tenants.form.subdomain")} aria-label={t("scp.tenants.form.subdomain")} required placeholder="acme" hint={t("scp.tenants.form.subdomainHint")} value={createForm.subdomain} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, subdomain: e.target.value.toLowerCase().replace(/\s+/g, "") }); }} />
          <Input type="email" label={t("scp.tenants.form.adminEmail")} aria-label={t("scp.tenants.form.adminEmail")} required placeholder="admin@example.com" value={createForm.adminEmail} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, adminEmail: e.target.value }); }} />
          <Input label={t("scp.tenants.form.adminDisplayName")} aria-label={t("scp.tenants.form.adminDisplayName")} required placeholder={t("scp.tenants.form.adminDisplayNamePlaceholder")} value={createForm.adminDisplayName} onChange={(e) => { setDialogError(""); setCreateForm({ ...createForm, adminDisplayName: e.target.value }); }} />
          {dialogError ? <ScpError message={dialogError} /> : null}
        </div>
      </Modal>

      <Modal
        isOpen={dialog?.kind === "edit"}
        onClose={() => !busy && setDialog(null)}
        title={t("scp.tenants.editDialogTitle")}
        closeButtonLabel={t("common.close")}
        footer={
          <>
            <Button variant="secondary" disabled={busy} onClick={() => setDialog(null)}>{t("form.action.cancel")}</Button>
            <Button variant="primary" loading={busy} disabled={!editForm.name} onClick={() => dialog?.kind === "edit" && void updateTenant(dialog.tenantId)}>
              {t("form.action.save")}
            </Button>
          </>
        }
      >
        <div className={styles.filters}>
          <Input label={t("scp.tenants.form.name")} aria-label={t("scp.tenants.form.name")} required value={editForm.name} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, name: e.target.value }); }} />
          <Input label={t("scp.tenants.form.legalName")} aria-label={t("scp.tenants.form.legalName")} value={editForm.legalName} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, legalName: e.target.value }); }} />
          <Input type="email" label={t("scp.tenants.form.billingEmail")} aria-label={t("scp.tenants.form.billingEmail")} value={editForm.billingEmail} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, billingEmail: e.target.value }); }} />
          <Input label={t("scp.tenants.form.countryCode")} aria-label={t("scp.tenants.form.countryCode")} hint={t("scp.tenants.form.countryHint")} value={editForm.countryCode} maxLength={2} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, countryCode: e.target.value.toUpperCase() }); }} />
          <Input label={t("scp.tenants.form.locale")} aria-label={t("scp.tenants.form.locale")} hint={t("scp.tenants.form.localeHint")} value={editForm.locale} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, locale: e.target.value }); }} />
          <Input label={t("scp.tenants.form.timezone")} aria-label={t("scp.tenants.form.timezone")} hint={t("scp.tenants.form.timezoneHint")} value={editForm.timezone} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, timezone: e.target.value }); }} />
          <Input label={t("scp.tenants.form.currencyCode")} aria-label={t("scp.tenants.form.currencyCode")} hint={t("scp.tenants.form.currencyHint")} value={editForm.currencyCode} maxLength={3} onChange={(e) => { setDialogError(""); setEditForm({ ...editForm, currencyCode: e.target.value.toUpperCase() }); }} />
          <p className={styles.appCardMeta}>{t("scp.tenants.form.subdomainImmutableNote")}</p>
          {dialogError ? <ScpError message={dialogError} /> : null}
        </div>
      </Modal>

      <Modal
        isOpen={dialog?.kind === "status"}
        onClose={() => !busy && setDialog(null)}
        title={dialog?.kind === "status" && dialog.targetStatus === "ARCHIVED" ? t("scp.tenants.archiveDialogTitle") : dialog?.kind === "status" && dialog.targetStatus === "SUSPENDED" ? t("scp.tenants.freezeDialogTitle") : t("scp.tenants.reactivateDialogTitle")}
        closeButtonLabel={t("common.close")}
        footer={
          <>
            <Button variant="secondary" disabled={busy} onClick={() => setDialog(null)}>{t("form.action.cancel")}</Button>
            <Button
              variant={dialog?.kind === "status" && dialog.targetStatus === "ARCHIVED" ? "danger" : "primary"}
              loading={busy}
              disabled={!reason.trim()}
              onClick={() => dialog?.kind === "status" && void applyStatus(dialog.tenantId, dialog.targetStatus)}
            >
              {t("form.action.confirm")}
            </Button>
          </>
        }
      >
        {dialog?.kind === "status" && dialog.targetStatus === "ARCHIVED" ? (
          <p>{t("scp.tenants.archiveWarning")}</p>
        ) : null}
        <Input label={t("scp.tenants.form.reason")} aria-label={t("scp.tenants.form.reason")} required placeholder={t("scp.tenants.form.reasonPlaceholder")} value={reason} maxLength={500} onChange={(e) => { setDialogError(""); setReason(e.target.value); }} />
        {dialogError ? <ScpError message={dialogError} /> : null}
      </Modal>
    </ScpPage>
  );
}

function Pagination({
  page,
  onPage,
}: {
  page: PageResponse<unknown>;
  onPage: (index: number) => void;
}) {
  const { t } = useI18n();
  return (
    <nav className={styles.filters} aria-label={t("scp.common.pagination")}>
      <Button
        variant="secondary"
        size="sm"
        disabled={page.page === 0}
        onClick={() => onPage(page.page - 1)}
      >
        {t("scp.common.previous")}
      </Button>
      <span className={styles.appCardMeta}>
        {t("scp.common.pageOf", { page: page.page + 1, total: Math.max(page.totalPages, 1) })}
      </span>
      <Button
        variant="secondary"
        size="sm"
        disabled={page.page + 1 >= page.totalPages}
        onClick={() => onPage(page.page + 1)}
      >
        {t("scp.common.next")}
      </Button>
    </nav>
  );
}