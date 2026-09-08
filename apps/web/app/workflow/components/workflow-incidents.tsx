"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowIncidentResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

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

  if (loading) return <p>جارٍ التحميل…</p>;

  return (
    <div dir="rtl">
      <h2 style={{ marginTop: 0, fontSize: 20 }}>الحوادث</h2>
      {conflict && <p role="alert" style={{ color: "var(--snad-color-warning)" }}>{conflict}</p>}
      {error && (
        <div role="alert" style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
          <p style={{ color: "var(--snad-color-error)", margin: 0 }}>{error}</p>
          <button type="button" onClick={() => void load()}>إعادة المحاولة</button>
        </div>
      )}
      {!error && incidents.length === 0 && <p>لا توجد حوادث مفتوحة.</p>}

      {incidents.map((incident) => {
        const busy = actioningId === incident.id;
        return (
          <article key={incident.id} style={{ border: "1px solid var(--snad-color-border-default)", borderRadius: 8, padding: 12, marginBottom: 8 }}>
            <strong>{incident.source}</strong>{" "}
            <span style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>
              {incident.severity} · {incident.status} · {incident.failureCategory} · إصدار {incident.version}
            </span>
            <div style={{ marginTop: 10, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              {incident.status === "OPEN" && (
                <button type="button" disabled={busy} onClick={() => void acknowledge(incident)}>إقرار</button>
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
                  <button type="button" disabled={busy} onClick={() => void resolve(incident)}>حل</button>
                </>
              )}
            </div>
          </article>
        );
      })}
    </div>
  );
}
