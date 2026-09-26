"use client";

/**
 * HR operational dashboard — WS5 Task 10 Step 5 + G2 visual closure launcher.
 *
 * Summaries derive exclusively from the canonical v2 API (no mock data).
 * Capability checks remain UX-only; backend authorization is authoritative.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { hrmV2Api, type AssignmentResponse, type EmploymentResponse, type OverrideRequestResponse, type PositionResponse } from "@/lib/api/hr-v2-api";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "./components/hr-workspace";
import { HrG2Launcher } from "./components/hr-g2-launcher";
import { HrErrorState, HrLoading } from "./components/hr-feedback";
import styles from "./hr.module.css";

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function HrPage() {
  const { state, me } = useAuth();
  const { locale, t } = useI18n();

  const capabilities = me?.capabilities ?? [];
  // A G2-only identity is still an HRM identity and must reach the HR landing
  // page. This is UX discoverability only; every API remains backend-authorized.
  const canSeeHr = capabilities.some((capability) => capability.startsWith("HRM."));

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [employments, setEmployments] = useState<EmploymentResponse[] | null>(null);
  const [positions, setPositions] = useState<PositionResponse[] | null>(null);
  const [assignments, setAssignments] = useState<AssignmentResponse[] | null>(null);
  const [overrides, setOverrides] = useState<OverrideRequestResponse[] | null>(null);

  const load = useCallback(async () => {
    const caps = capabilities;
    const canEmployee = caps.includes(HRM_CAPABILITIES.EMPLOYEE_VIEW);
    const canStructure = caps.includes(HRM_CAPABILITIES.ORG_STRUCTURE_VIEW);
    const canAssignment = caps.includes(HRM_CAPABILITIES.ASSIGNMENT_VIEW);
    const canOverride = caps.includes(HRM_CAPABILITIES.COMPLIANCE_OVERRIDE_REQUEST);
    try {
      const [emps, pos, asg, ovr] = await Promise.allSettled([
        canEmployee ? hrmV2Api.listEmployments() : Promise.resolve(null),
        canStructure ? hrmV2Api.listPositions() : Promise.resolve(null),
        canAssignment ? hrmV2Api.listAssignments() : Promise.resolve(null),
        canOverride ? hrmV2Api.listComplianceOverrides() : Promise.resolve(null),
      ]);
      setEmployments(emps.status === "fulfilled" ? emps.value : null);
      setPositions(pos.status === "fulfilled" ? pos.value : null);
      setAssignments(asg.status === "fulfilled" ? asg.value : null);
      setOverrides(ovr.status === "fulfilled" ? ovr.value : null);
      if (emps.status === "rejected" && pos.status === "rejected" && asg.status === "rejected" && ovr.status === "rejected") {
        throw emps.reason;
      }
      setError(null);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
    // `capabilities` is read from the auth snapshot at call time.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  const statusCounts = useMemo(() => {
    const c = { ACTIVE: 0, ONBOARDING: 0, ON_LEAVE: 0, SUSPENDED: 0 };
    for (const e of employments ?? []) {
      if (e.currentStatus in c) c[e.currentStatus as keyof typeof c] += 1;
    }
    return c;
  }, [employments]);

  const occupancy = useMemo(() => {
    if (!positions || !assignments) return null;
    const today = todayIso();
    const occupied = new Set<string>();
    for (const a of assignments) {
      if (!a.positionId) continue;
      const effective = a.effectiveFrom <= today && (a.effectiveTo === null || a.effectiveTo >= today);
      if (a.status === "ACTIVE" && effective) occupied.add(a.positionId);
    }
    return { occupied: occupied.size, vacant: positions.length - occupied.size };
  }, [positions, assignments]);

  const pendingOverrides = (overrides ?? []).filter((o) => o.status === "PENDING").length;
  const foundationLinks = [
    { href: "/hr/employees", ar: "سجل الموظفين", en: "Employee records", capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
    { href: "/hr/org-structure", ar: "الهيكل التنظيمي", en: "Organization structure", capability: HRM_CAPABILITIES.ORG_STRUCTURE_VIEW },
    { href: "/hr/compliance", ar: "الالتزام", en: "Compliance", capability: HRM_CAPABILITIES.EMPLOYEE_VIEW },
  ].filter((link) => capabilities.includes(link.capability));

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr" translate={t}>
      {!canSeeHr ? (
        <HrErrorState
          error={{ details: { status: 403, body: { code: "HRM_SCOPE_DENIED", message: null } } }}
        />
      ) : loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <section aria-label={locale === "ar" ? "ملخص الموارد البشرية" : "Human resources summary"} data-testid="hr-landing-ready">
          <div className={styles.dashboardGrid}>
            <div className={styles.statCard}>
              <span className={styles.statValue}>{statusCounts.ACTIVE}</span>
              <span className={styles.statLabel}>{locale === "ar" ? "توظيف نشِط" : "Active employment"}</span>
            </div>
            <div className={styles.statCard}>
              <span className={styles.statValue}>{statusCounts.ONBOARDING}</span>
              <span className={styles.statLabel}>{locale === "ar" ? "قيد التأهيل" : "Onboarding"}</span>
            </div>
            <div className={styles.statCard}>
              <span className={styles.statValue}>{statusCounts.ON_LEAVE + statusCounts.SUSPENDED}</span>
              <span className={styles.statLabel}>{locale === "ar" ? "في إجازة / موقوف" : "On leave / suspended"}</span>
            </div>
            {occupancy ? (
              <>
                <div className={styles.statCard}>
                  <span className={styles.statValue}>{occupancy.occupied}</span>
                  <span className={styles.statLabel}>{locale === "ar" ? "منصب مشغول" : "Occupied position"}</span>
                </div>
                <div className={styles.statCard}>
                  <span className={styles.statValue}>{occupancy.vacant}</span>
                  <span className={styles.statLabel}>{locale === "ar" ? "منصب شاغر" : "Vacant position"}</span>
                </div>
              </>
            ) : null}
            {overrides !== null ? (
              <div className={pendingOverrides > 0 ? `${styles.statCard} ${styles.statAlert}` : styles.statCard}>
                <span className={styles.statValue}>{pendingOverrides}</span>
                <span className={styles.statLabel}>{locale === "ar" ? "تجاوزات قيد المراجعة" : "Overrides under review"}</span>
              </div>
            ) : null}
          </div>

          <HrG2Launcher capabilities={capabilities} />

          {foundationLinks.length > 0 ? (
            <p className={styles.mutedNote}>
              {foundationLinks.map((link, index) => (
                <span key={link.href}>
                  {index > 0 ? " · " : null}
                  <Link href={link.href} className={styles.tableLink}>{locale === "ar" ? link.ar : link.en}</Link>
                </span>
              ))}
            </p>
          ) : null}
        </section>
      )}
    </HrWorkspace>
  );
}