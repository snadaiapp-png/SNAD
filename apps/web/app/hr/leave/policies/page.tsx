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
  if (!canAdmin) return <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/policies"><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<LeaveType>[] = [
    { key: "code", header: "Code" },
    { key: "nameAr", header: "Name (AR)" },
    { key: "nameEn", header: "Name (EN)" },
    { key: "isPaid", header: "Paid", render: (r) => String(r.isPaid) },
    { key: "requiresAttachment", header: "Attachment", render: (r) => String(r.requiresAttachment) },
    { key: "defaultDaysPerYear", header: "Default Days", render: (r) => r.defaultDaysPerYear != null ? String(r.defaultDaysPerYear) : "— (configure via policy)" },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/policies">
      <header><h1>Leave Policies</h1>
      <p className={styles.kpiHint}>Country-neutral configuration — no statutory entitlement values are hardcoded.</p>
      </header>
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
        <HrDataTable<LeaveType> caption="Leave Types (identity only — no statutory days)" columns={columns} rows={policies} rowKey={(r) => r.id} emptyTitle="No leave types" />
      )}
    </HrWorkspace>
  );
}
