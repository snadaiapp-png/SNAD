"use client";

import { useCallback, useEffect, useState } from "react";
import { scpApi, type ProvisioningJob } from "@/lib/api/scp-api";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button } from "@/components/sds";
import {
  ScpEmpty,
  ScpError,
  ScpPage,
  ScpSkeleton,
  ScpStatusPill,
} from "../_components/ScpStates";
import { useScpFormat } from "../_components/format";
import styles from "../scp.module.css";

/**
 * Provisioning jobs — observability over subscription activation with a
 * capability-guarded retry action for failed jobs.
 */
export default function ProvisioningPage() {
  const { t } = useI18n();
  const { day } = useScpFormat();
  const [jobs, setJobs] = useState<ProvisioningJob[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState("");
  const [busyId, setBusyId] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setJobs(await scpApi.provisioningJobs(status ? { status } : {}));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  async function retry(jobId: string) {
    setBusyId(jobId);
    setError("");
    try {
      await scpApi.retryProvisioningJob(jobId);
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusyId("");
    }
  }

  if (loading) {
    return (
      <ScpPage title={t("scp.provisioning.title")}>
        <ScpSkeleton lines={8} />
      </ScpPage>
    );
  }

  return (
    <ScpPage title={t("scp.provisioning.title")} subtitle={t("scp.provisioning.subtitle")}>
      <div className={styles.filters}>
        <select
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          aria-label={t("scp.provisioning.statusFilter")}
        >
          <option value="">{t("scp.filters.allStatuses")}</option>
          {["PENDING", "RUNNING", "SUCCEEDED", "FAILED", "RETRYING"].map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </div>

      {error ? <ScpError message={error} onRetry={load} /> : null}

      {!jobs || jobs.length === 0 ? (
        <ScpEmpty message={t("scp.state.empty")} />
      ) : (
        <div className={styles.panel}>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <caption>{t("scp.provisioning.count", { count: jobs.length })}</caption>
              <thead>
                <tr>
                  <th scope="col">{t("scp.provisioning.action")}</th>
                  <th scope="col">{t("scp.provisioning.status")}</th>
                  <th scope="col">{t("scp.provisioning.attempts")}</th>
                  <th scope="col">{t("scp.provisioning.startedAt")}</th>
                  <th scope="col">{t("scp.provisioning.subscription")}</th>
                  <th scope="col">{t("scp.common.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id}>
                    <td data-label={t("scp.provisioning.action")}>{job.action}</td>
                    <td data-label={t("scp.provisioning.status")}>
                      <ScpStatusPill value={job.status} />
                      {job.errorCode ? (
                        <span className={styles.appCardMeta}> · {job.errorCode}</span>
                      ) : null}
                    </td>
                    <td data-label={t("scp.provisioning.attempts")}>{job.attempts}</td>
                    <td data-label={t("scp.provisioning.startedAt")}>{day(job.startedAt)}</td>
                    <td data-label={t("scp.provisioning.subscription")}>
                      <a href={`/executive/subscriptions/${job.subscriptionId}`}>
                        {t("scp.subscriptions.details")}
                      </a>
                    </td>
                    <td data-label={t("scp.common.actions")}>
                      {job.status === "FAILED" || job.status === "RETRYING" ? (
                        <Button
                          variant="secondary"
                          size="sm"
                          disabled={busyId === job.id}
                          onClick={() => void retry(job.id)}
                        >
                          {t("scp.provisioning.retry")}
                        </Button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </ScpPage>
  );
}
