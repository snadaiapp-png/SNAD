"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrActionBar, HrKpiCard, HrKpiGrid, HrMobileRecordList, HrOperationalPanel, HrProductHeader } from "../../components/hr-product-surface";
import styles from "../../hr.module.css";

interface LeaveType { id: string; code: string; nameAr: string; nameEn: string; isPaid: boolean; requiresAttachment: boolean; defaultDaysPerYear: number | null; state: string; }

export default function LeavePoliciesPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canAdmin = capabilities.includes("HRM.LEAVE.POLICY_ADMIN");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [policies, setPolicies] = useState<LeaveType[]>([]);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { setPolicies(await hrG2Api.listAdminLeaveTypes() as LeaveType[]); }
    catch (err) { setError(err); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canAdmin) return <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/policies" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const paidCount = policies.filter((policy) => policy.isPaid).length;
  const attachmentCount = policies.filter((policy) => policy.requiresAttachment).length;
  const boolLabel = (value: boolean) => t(value ? "hrm.leavePolicies.yes" : "hrm.leavePolicies.no");
  const columns: HrColumn<LeaveType>[] = [
    { key: "code", header: t("hrm.leavePolicies.code") },
    { key: "nameAr", header: t("hrm.leavePolicies.nameAr") },
    { key: "nameEn", header: t("hrm.leavePolicies.nameEn") },
    { key: "isPaid", header: t("hrm.leavePolicies.paid"), render: (r) => boolLabel(r.isPaid) },
    { key: "requiresAttachment", header: t("hrm.leavePolicies.attachment"), render: (r) => boolLabel(r.requiresAttachment) },
    { key: "defaultDaysPerYear", header: t("hrm.leavePolicies.defaultDays"), render: (r) => r.defaultDaysPerYear != null ? String(r.defaultDaysPerYear) : t("hrm.leavePolicies.configureViaPolicy") },
  ];

  return <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/policies" translate={t}>
    <div data-testid="leave-policies-product-surface">
      <HrProductHeader eyebrow={t("hrm.g2.landing.hrOperations")} title={t("hrm.leavePolicies.title")} subtitle={t("hrm.leavePolicies.subtitle")} />
      <HrKpiGrid label={t("hrm.leavePolicies.title")}>
        <div data-testid="leave-policies-total-count"><HrKpiCard label={t("hrm.leavePolicies.caption")} value={policies.length} /></div>
        <div data-testid="leave-policies-paid-count"><HrKpiCard label={t("hrm.leavePolicies.paid")} value={paidCount} /></div>
        <div data-testid="leave-policies-attachment-count"><HrKpiCard label={t("hrm.leavePolicies.attachment")} value={attachmentCount} tone={attachmentCount > 0 ? "attention" : "neutral"} /></div>
      </HrKpiGrid>
      <div data-testid="leave-policies-admin-state"><HrActionBar label={t("hrm.leavePolicies.title")}><span className={styles.kpiHint}>{t("hrm.leavePolicies.subtitle")}</span></HrActionBar></div>
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : <div data-testid="leave-policies-ready"><div data-testid="leave-policies-records-panel"><HrOperationalPanel label={t("hrm.leavePolicies.caption")} title={t("hrm.leavePolicies.caption")} description={t("hrm.leavePolicies.subtitle")}>
        <HrDataTable<LeaveType> caption={t("hrm.leavePolicies.caption")} columns={columns} rows={policies} rowKey={(r) => r.id} emptyTitle={t("hrm.leavePolicies.empty")} />
        <HrMobileRecordList label={t("hrm.leavePolicies.caption")}>{policies.map((policy) => <article key={policy.id} className={styles.statCard}><strong>{locale === "ar" ? policy.nameAr : policy.nameEn}</strong><span>{policy.code}</span><span>{t("hrm.leavePolicies.paid")}: {boolLabel(policy.isPaid)}</span><span>{t("hrm.leavePolicies.attachment")}: {boolLabel(policy.requiresAttachment)}</span></article>)}</HrMobileRecordList>
      </HrOperationalPanel></div></div>}
    </div>
  </HrWorkspace>;
}
