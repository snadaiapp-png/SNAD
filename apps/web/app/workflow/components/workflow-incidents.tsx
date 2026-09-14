"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  workflowApi,
  type WorkflowIncidentResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";
import styles from "../workflow.module.css";
import {
  StatusBadge,
  WorkflowEmptyState,
  WorkflowSectionHeader,
  formatWorkflowDate,
} from "./workflow-ui";

const SEVERITY_ORDER: Record<WorkflowIncidentResponse["severity"], number> = {
  CRITICAL: 4,
  HIGH: 3,
  MEDIUM: 2,
  LOW: 1,
};

export function WorkflowIncidents() {
  const [incidents, setIncidents] = useState<WorkflowIncidentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const [actioningId, setActioningId] = useState<string | null>(null);
  const [resolutions, setResolutions] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setIncidents(await workflowApi.listIncidents(50));
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل الحوادث"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const ordered = useMemo(
    () => [...incidents].sort((a, b) => {
      if (a.status === "RESOLVED" && b.status !== "RESOLVED") return 1;
      if (a.status !== "RESOLVED" && b.status === "RESOLVED") return -1;
      return SEVERITY_ORDER[b.severity] - SEVERITY_ORDER[a.severity];
    }),
    [incidents],
  );

  const runMutation = async (
    incident: WorkflowIncidentResponse,
    command: () => Promise<WorkflowIncidentResponse>,
    fallback: string,
  ) => {
    setActioningId(incident.id);
    setError(null);
    setConflict(null);
    try {
      await command();
      await load();
    } catch (cause: unknown) {
      const status = (cause as { status?: number })?.status;
      if (status === 409) {
        setConflict("تم تحديث الحادث من مستخدم آخر. أُعيد تحميل النسخة الأحدث.");
        await load();
      } else if (status === 403) {
        setError(describeWorkflowError(cause, "لا تملك صلاحية تنفيذ هذا الإجراء على الحادث"));
      } else {
        setError(describeWorkflowError(cause, fallback));
      }
    } finally {
      setActioningId(null);
    }
  };

  const acknowledge = (incident: WorkflowIncidentResponse) =>
    runMutation(
      incident,
      () => workflowApi.acknowledgeIncident(incident.id),
      "فشل الإقرار بالحادث",
    );

  const resolve = async (incident: WorkflowIncidentResponse) => {
    const resolution = (resolutions[incident.id] ?? "").trim();
    if (!resolution) {
      setError("سبب الحل مطلوب");
      return;
    }
    await runMutation(
      incident,
      () => workflowApi.resolveIncident(incident.id, incident.version, resolution),
      "فشل حل الحادث",
    );
  };

  return (
    <div dir="rtl">
      <WorkflowSectionHeader
        eyebrow="الاستجابة التشغيلية"
        title="الحوادث"
        description="الحالات التي تحتاج إقرارًا أو معالجة، مرتبة لإبراز الحوادث الأعلى خطورة أولًا."
        actions={
          <button type="button" onClick={() => void load()} disabled={loading}>
            {loading ? "جارٍ التحديث…" : "تحديث"}
          </button>
        }
      />

      {conflict && (
        <div role="alert" className={`${styles.alert} ${styles.alertWarning}`}>{conflict}</div>
      )}
      {error && (
        <div role="alert" className={`${styles.alert} ${styles.alertError}`}>
          <span>{error}</span>
          <button type="button" onClick={() => void load()}>إعادة المحاولة</button>
        </div>
      )}

      {!loading && !error && ordered.length === 0 ? (
        <WorkflowEmptyState
          title="لا توجد حوادث مفتوحة"
          description="تظهر هنا أعطال التنفيذ أو الحالات التي تتطلب استجابة تشغيلية."
        />
      ) : (
        <div className={styles.cardsGrid}>
          {ordered.map((incident) => {
            const busy = actioningId === incident.id;
            return (
              <article key={incident.id} className={styles.incidentCard}>
                <div className={styles.cardHeader}>
                  <div>
                    <h3 className={styles.cardTitle}>{incident.source || "حادث سير عمل"}</h3>
                    <div className={styles.cardMeta}>
                      <StatusBadge value={incident.severity} />
                      <StatusBadge value={incident.status} />
                      <span>{incident.failureCategory}</span>
                      <span>مرجع المزامنة #{incident.version}</span>
                    </div>
                  </div>
                  <span className={styles.mono}>#{incident.id.slice(0, 8)}…</span>
                </div>

                <div className={styles.cardMeta}>
                  <span>الإنشاء: {formatWorkflowDate(incident.createdAt)}</span>
                  <span>المثيل: <span className={styles.mono}>{incident.workflowInstanceId.slice(0, 8)}…</span></span>
                </div>

                {incident.resolution ? (
                  <div className={styles.policyDescription}>الحل: {incident.resolution}</div>
                ) : null}

                <div className={styles.cardActions}>
                  {incident.status === "OPEN" && (
                    <button type="button" disabled={busy} onClick={() => void acknowledge(incident)}>
                      إقرار بالحادث
                    </button>
                  )}
                  {incident.status !== "RESOLVED" && (
                    <>
                      <input
                        aria-label={`سبب حل الحادث ${incident.id}`}
                        placeholder="سبب الحل (إلزامي)"
                        value={resolutions[incident.id] ?? ""}
                        onChange={(event) =>
                          setResolutions((current) => ({ ...current, [incident.id]: event.target.value }))
                        }
                      />
                      <button type="button" disabled={busy} onClick={() => void resolve(incident)}>
                        إغلاق كـ محلول
                      </button>
                    </>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
