"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import styles from "../../hr.module.css";
import visualStyles from "../../components/hr-g2-visual.module.css";

interface LeaveType {
  id: string;
  code: string;
  nameAr: string;
  nameEn: string;
  isPaid: boolean;
  requiresAttachment: boolean;
  defaultDaysPerYear: number | null;
  state: string;
}

export default function LeavePoliciesPage() {
  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canAdmin = capabilities.includes("HRM.LEAVE.POLICY_ADMIN");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [policies, setPolicies] = useState<LeaveType[]>([]);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      setPolicies(await hrG2Api.listAdminLeaveTypes() as LeaveType[]);
    } catch (err) { setError(err); } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canAdmin) return <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/policies" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const boolLabel = (value: boolean) => t(value ? "hrm.leavePolicies.yes" : "hrm.leavePolicies.no");
  const columns: HrColumn<LeaveType>[] = [
    { key: "code", header: t("hrm.leavePolicies.code") },
    { key: "nameAr", header: t("hrm.leavePolicies.nameAr") },
    { key: "nameEn", header: t("hrm.leavePolicies.nameEn") },
    { key: "isPaid", header: t("hrm.leavePolicies.paid"), render: (r) => boolLabel(r.isPaid) },
    { key: "requiresAttachment", header: t("hrm.leavePolicies.attachment"), render: (r) => boolLabel(r.requiresAttachment) },
    { key: "defaultDaysPerYear", header: t("hrm.leavePolicies.defaultDays"), render: (r) => r.defaultDaysPerYear != null ? String(r.defaultDaysPerYear) : t("hrm.leavePolicies.configureViaPolicy") },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/policies" translate={t}>
      <header className={visualStyles.g2PageHeader}>
        <div>
          <h1>{t("hrm.leavePolicies.title")}</h1>
          <p className={styles.kpiHint}>{t("hrm.leavePolicies.subtitle")}</p>
        </div>
      </header>
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
        <div data-testid="leave-policies-ready">
          <HrDataTable<LeaveType> caption={t("hrm.leavePolicies.caption")} columns={columns} rows={policies} rowKey={(r) => r.id} emptyTitle={t("hrm.leavePolicies.empty")} />
        </div>
      )}
    </HrWorkspace>
  );
}
