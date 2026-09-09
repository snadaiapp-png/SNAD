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
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await executiveApi.createTenant(createForm);
      setDialog(null);
      setCreateForm(EMPTY_CREATE);
      setNotice("تم إنشاء الحساب بنجاح.");
      await load();
    } catch (reasonValue) {
      setError(scpErrorMessage(reasonValue));
    } finally {
      setBusy(false);
    }
  }

  async function openEdit(tenantId: string) {
    setBusy(true);
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
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await executiveApi.updateTenant(tenantId, editForm);
      setDialog(null);
      setNotice("تم تحديث بيانات الحساب.");
      await load();
    } catch (reasonValue) {
      setError(scpErrorMessage(reasonValue));
    } finally {
      setBusy(false);
    }
  }

  async function applyStatus(tenantId: string, targetStatus: string) {
    if (!reason.trim()) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await executiveApi.changeTenantStatus(tenantId, targetStatus, reason.trim());
      setDialog(null);
      setReason("");
      setNotice(
        targetStatus === "ARCHIVED"
          ? "تمت أرشفة الحساب مع الحفاظ على السجل والعلاقات."
          : targetStatus === "SUSPENDED"
            ? "تم تجميد الحساب."
            : "تمت إعادة تفعيل الحساب.",
      );
      await load();
    } catch (reasonValue) {
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
          <Button type="button" variant="primary" size="sm" onClick={() => setDialog({ kind: "create" })}>
            إنشاء حساب جديد
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
                          {canManage && !archived ? (
                            <>
                              <Button type="button" variant="secondary" size="sm" disabled={busy} onClick={() => void openEdit(tenant.id)}>
                                تحديث
                              </Button>
                              {canFreeze ? (
                                <Button type="button" variant="secondary" size="sm" onClick={() => { setReason(""); setDialog({ kind: "status", tenantId: tenant.id, targetStatus: "SUSPENDED" }); }}>
                                  تجميد
                                </Button>
                              ) : null}
                              {canReactivate ? (
                                <Button type="button" variant="secondary" size="sm" onClick={() => { setReason(""); setDialog({ kind: "status", tenantId: tenant.id, targetStatus: "ACTIVE" }); }}>
                                  إعادة التفعيل
                                </Button>
                              ) : null}
                              <Button type="button" variant="danger" size="sm" onClick={() => { setReason(""); setDialog({ kind: "status", tenantId: tenant.id, targetStatus: "ARCHIVED" }); }}>
                                حذف الحساب
                              </Button>
                              <Link href={`/executive/subscriptions?tenantId=${tenant.id}&intent=upgrade`}>
                                ترقية
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
        title="إنشاء حساب جديد"
        closeButtonLabel="إغلاق"
        footer={
          <>
            <Button variant="secondary" disabled={busy} onClick={() => setDialog(null)}>إلغاء</Button>
            <Button variant="primary" loading={busy} disabled={!createForm.name || !createForm.subdomain || !createForm.adminEmail || !createForm.adminDisplayName} onClick={() => void createTenant()}>
              إنشاء
            </Button>
          </>
        }
      >
        <div className={styles.filters}>
          <Input aria-label="اسم الحساب" placeholder="اسم الحساب" value={createForm.name} onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })} />
          <Input aria-label="النطاق الفرعي" placeholder="subdomain" value={createForm.subdomain} onChange={(e) => setCreateForm({ ...createForm, subdomain: e.target.value.toLowerCase() })} />
          <Input type="email" aria-label="بريد المسؤول" placeholder="admin@example.com" value={createForm.adminEmail} onChange={(e) => setCreateForm({ ...createForm, adminEmail: e.target.value })} />
          <Input aria-label="اسم المسؤول" placeholder="اسم المسؤول" value={createForm.adminDisplayName} onChange={(e) => setCreateForm({ ...createForm, adminDisplayName: e.target.value })} />
        </div>
      </Modal>

      <Modal
        isOpen={dialog?.kind === "edit"}
        onClose={() => !busy && setDialog(null)}
        title="تحديث بيانات الحساب"
        closeButtonLabel="إغلاق"
        footer={
          <>
            <Button variant="secondary" disabled={busy} onClick={() => setDialog(null)}>إلغاء</Button>
            <Button variant="primary" loading={busy} disabled={!editForm.name} onClick={() => dialog?.kind === "edit" && void updateTenant(dialog.tenantId)}>
              حفظ
            </Button>
          </>
        }
      >
        <div className={styles.filters}>
          <Input aria-label="اسم الحساب" value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
          <Input aria-label="الاسم القانوني" value={editForm.legalName} onChange={(e) => setEditForm({ ...editForm, legalName: e.target.value })} />
          <Input type="email" aria-label="بريد الفوترة" value={editForm.billingEmail} onChange={(e) => setEditForm({ ...editForm, billingEmail: e.target.value })} />
          <Input aria-label="الدولة" value={editForm.countryCode} maxLength={2} onChange={(e) => setEditForm({ ...editForm, countryCode: e.target.value.toUpperCase() })} />
          <Input aria-label="اللغة" value={editForm.locale} onChange={(e) => setEditForm({ ...editForm, locale: e.target.value })} />
          <Input aria-label="المنطقة الزمنية" value={editForm.timezone} onChange={(e) => setEditForm({ ...editForm, timezone: e.target.value })} />
          <Input aria-label="العملة" value={editForm.currencyCode} maxLength={3} onChange={(e) => setEditForm({ ...editForm, currencyCode: e.target.value.toUpperCase() })} />
          <p className={styles.appCardMeta}>النطاق الفرعي غير قابل للتعديل من هذه العملية لحماية هوية التوجيه.</p>
        </div>
      </Modal>

      <Modal
        isOpen={dialog?.kind === "status"}
        onClose={() => !busy && setDialog(null)}
        title={dialog?.kind === "status" && dialog.targetStatus === "ARCHIVED" ? "أرشفة الحساب" : dialog?.kind === "status" && dialog.targetStatus === "SUSPENDED" ? "تجميد الحساب" : "إعادة تفعيل الحساب"}
        closeButtonLabel="إغلاق"
        footer={
          <>
            <Button variant="secondary" disabled={busy} onClick={() => setDialog(null)}>إلغاء</Button>
            <Button
              variant={dialog?.kind === "status" && dialog.targetStatus === "ARCHIVED" ? "danger" : "primary"}
              loading={busy}
              disabled={!reason.trim()}
              onClick={() => dialog?.kind === "status" && void applyStatus(dialog.tenantId, dialog.targetStatus)}
            >
              تأكيد
            </Button>
          </>
        }
      >
        {dialog?.kind === "status" && dialog.targetStatus === "ARCHIVED" ? (
          <p>سيتم تحويل الحساب إلى ARCHIVED فقط. لن يتم حذف السجل أو الفواتير أو علاقات التدقيق.</p>
        ) : null}
        <Input aria-label="سبب الإجراء" placeholder="اكتب سبب الإجراء" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
      </Modal>
    </ScpPage>
  );
}

export function Pagination({
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
