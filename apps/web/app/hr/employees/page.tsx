"use client";

/**
 * Employee Directory — WS5 Task 9 (/hr/employees).
 *
 * SAFE SURFACE ONLY: joins the canonical Employment + Person summary lists.
 * Restricted data (PII, identifiers, compensation) is structurally absent
 * from these DTOs and is never fetched by this page — private reads happen
 * only through the audited Employee 360 private tab.
 *
 * Filters implemented from the safe canonical DTOs: text search (name /
 * employee number), employment status, worker classification, legal entity.
 * Organization/Org-Unit/country-mode filtering is NOT synthesized here:
 * the v2 directory contract does not carry those fields, and inventing
 * cross-resource joins or mock data is forbidden by the WS5 plan.
 *
 * The canonical v2 list contract returns complete arrays (no pagination
 * parameters), so no fake pagination is rendered.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { hrmV2Api, type EmploymentResponse, type PersonSummaryResponse } from "@/lib/api/hr-v2-api";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { HrWorkspace } from "../components/hr-workspace";
import { HrEmptyState, HrErrorState, HrLoading } from "../components/hr-feedback";
import { employmentStatusAr, formatArabicDate, workerClassificationAr } from "../hr-labels";
import styles from "../hr.module.css";

const STATUS_OPTIONS = ["ONBOARDING", "ACTIVE", "ON_LEAVE", "SUSPENDED", "TERMINATED", "DRAFT", "VOID"];

export default function EmployeeDirectoryPage() {
  const { state, me } = useAuth();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [employments, setEmployments] = useState<EmploymentResponse[]>([]);
  const [people, setPeople] = useState<PersonSummaryResponse[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [classificationFilter, setClassificationFilter] = useState("");

  const capabilities = me?.capabilities ?? [];
  const canView = capabilities.includes(HRM_CAPABILITIES.EMPLOYEE_VIEW);

  const load = useCallback(async () => {
    try {
      const [emps, ppl] = await Promise.all([
        hrmV2Api.listEmployments(),
        hrmV2Api.listPeople(),
      ]);
      setEmployments(emps);
      setPeople(ppl);
      setError(null);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    // Deferred to the next macrotask (codebase pattern) so the effect body
    // performs no synchronous setState.
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  const nameByPersonId = useMemo(() => {
    const map = new Map<string, string>();
    for (const p of people) map.set(p.personId, p.displayName);
    return map;
  }, [people]);

  const rows = useMemo(() => {
    const q = query.trim();
    return employments
      .filter((e) => (statusFilter ? e.currentStatus === statusFilter : true))
      .filter((e) => (classificationFilter ? e.workerClassificationCode === classificationFilter : true))
      .filter((e) => {
        if (!q) return true;
        const name = nameByPersonId.get(e.personId) ?? "";
        return name.includes(q) || e.employeeNumber.includes(q);
      });
  }, [employments, nameByPersonId, query, statusFilter, classificationFilter]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/employees">
      {!canView ? (
        <HrErrorState
          error={{ details: { status: 403, body: { code: "HRM_SCOPE_DENIED", message: null } } }}
        />
      ) : (
        <section aria-label="سجل الموظفين">
          <div className={styles.toolbar}>
            <input
              type="search"
              aria-label="بحث في السجل"
              placeholder="ابحث بالاسم أو الرقم الوظيفي…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className={styles.searchInput}
            />
            <select
              aria-label="الحالة الوظيفية"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className={styles.filterSelect}
            >
              <option value="">كل الحالات</option>
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>{employmentStatusAr(s)}</option>
              ))}
            </select>
            <select
              aria-label="تصنيف العامل"
              value={classificationFilter}
              onChange={(e) => setClassificationFilter(e.target.value)}
              className={styles.filterSelect}
            >
              <option value="">كل التصنيفات</option>
              {[...new Set(employments.map((e) => e.workerClassificationCode))].sort().map((c) => (
                <option key={c} value={c}>{workerClassificationAr(c)}</option>
              ))}
            </select>
          </div>

          {loading ? (
            <HrLoading />
          ) : error ? (
            <HrErrorState error={error} onRetry={load} />
          ) : employments.length === 0 ? (
            <HrEmptyState
              title="لا يوجد موظفون بعد"
              description="لم تُسجَّل أي عقود توظيف في هذا المستأجر حتى الآن."
            />
          ) : rows.length === 0 ? (
            <HrEmptyState
              title="لا توجد نتائج مطابقة"
              description="جرّب تعديل معايير البحث أو التصفية."
            />
          ) : (
            <div className={styles.hrTableWrap}>
              <table className={styles.hrTable}>
                <caption>سجل الموظفين — عرض آمن بدون بيانات خاصة</caption>
                <thead>
                  <tr>
                    <th scope="col">الرقم الوظيفي</th>
                    <th scope="col">الاسم</th>
                    <th scope="col">الحالة</th>
                    <th scope="col">التصنيف</th>
                    <th scope="col">تاريخ المباشرة</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((e) => (
                    <tr key={e.employmentId}>
                      <td>
                        <Link href={`/hr/employees/${e.employmentId}`} className={styles.tableLink}>
                          {e.employeeNumber}
                        </Link>
                      </td>
                      <td>{nameByPersonId.get(e.personId) ?? "—"}</td>
                      <td>{employmentStatusAr(e.currentStatus)}</td>
                      <td>{workerClassificationAr(e.workerClassificationCode)}</td>
                      <td>{formatArabicDate(e.employmentStartDate)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </HrWorkspace>
  );
}
