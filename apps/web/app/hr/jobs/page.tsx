"use client";

/**
 * Jobs workspace — WS5 Task 10 (/hr/jobs).
 *
 * Lists the canonical Jobs read model (title, grade, effective window,
 * status) with Arabic labels. Occupancy does not exist at the Job level;
 * vacancy derivation belongs to Positions.
 */

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { hrmV2Api, type JobResponse } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../components/hr-workspace";
import { HrEmptyState, HrErrorState, HrLoading } from "../components/hr-feedback";
import { formatArabicDate } from "../hr-labels";
import styles from "../hr.module.css";

const JOB_STATUS_AR: Record<string, string> = {
  ACTIVE: "ساري",
  SUPERSEDED: "مُستبدل",
};

export default function JobsPage() {
  const { state, me } = useAuth();
  const capabilities = me?.capabilities ?? [];

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [jobs, setJobs] = useState<JobResponse[]>([]);

  const load = useCallback(async () => {
    try {
      setJobs(await hrmV2Api.listJobs());
      setError(null);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/jobs">
      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : jobs.length === 0 ? (
        <HrEmptyState title="لا توجد وظائف" description="لم تُسجَّل وظائف بعد." />
      ) : (
        <div className={styles.hrTableWrap}>
          <table className={styles.hrTable}>
            <caption>الوظائف</caption>
            <thead>
              <tr>
                <th scope="col">المسمى</th>
                <th scope="col">المستوى</th>
                <th scope="col">سريان من</th>
                <th scope="col">سريان إلى</th>
                <th scope="col">الحالة</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((j) => (
                <tr key={j.jobId}>
                  <td>{j.title}</td>
                  <td>{j.grade ?? "—"}</td>
                  <td>{formatArabicDate(j.effectiveFrom)}</td>
                  <td>{j.effectiveTo ? formatArabicDate(j.effectiveTo) : "—"}</td>
                  <td>{JOB_STATUS_AR[j.status] ?? j.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </HrWorkspace>
  );
}
