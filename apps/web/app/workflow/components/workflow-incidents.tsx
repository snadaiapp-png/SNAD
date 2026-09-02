"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowIncidentResponse,
} from "@/lib/api/workflow-api";

/**
 * Incidents view (design decision AF3): OPEN/ACKNOWLEDGED incidents with
 * acknowledge/resolve commands. Resolution requires a non-blank note — the
 * backend enforces the same rule, and 403/409 are surfaced explicitly.
 */
export function WorkflowIncidents() {
  const [incidents, setIncidents] = useState<WorkflowIncidentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [resolutions, setResolutions] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setIncidents(await workflowApi.listIncidents(50));
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      setError(err?.message ?? "تعذر تحميل الحوادث");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const acknowledge = async (incident: WorkflowIncidentResponse) => {
    setError(null);
    try {
      await workflowApi.acknowledgeIncident(incident.id);
      await load();
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? "فشل الإقرار");
    }
  };

  const resolve = async (incident: WorkflowIncidentResponse) => {
    setError(null);
    const note = (resolutions[incident.id] ?? "").trim();
    if (!note) {
      setError("سبب الحل مطلوب");
      return;
    }
    try {
      await workflowApi.resolveIncident(incident.id, note);
      await load();
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      setError(err?.status === 409 ? "تعارض: تم تحديث الحادث للتو" : err?.message ?? "فشل الحل");
    }
  };

  if (loading) return <p>جارٍ التحميل…</p>;

  return (
    <div dir="rtl">
      {error && <p role="alert" style={{ color: "var(--snad-color-error)" }}>{error}</p>}
      {incidents.length === 0 && <p>لا توجد حوادث مفتوحة.</p>}
      {incidents.map((incident) => (
        <div key={incident.id} style={{ border: "1px solid var(--snad-color-border, #ddd)", borderRadius: 8, padding: 12, marginBottom: 8 }}>
          <strong>{incident.source}</strong>{" "}
          <span style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>
            {incident.severity} · {incident.status} · {incident.failureCategory}
          </span>
          <div style={{ marginTop: 8, display: "flex", gap: 8, alignItems: "center" }}>
            {incident.status === "OPEN" && (
              <button onClick={() => void acknowledge(incident)}>إقرار</button>
            )}
            {incident.status !== "RESOLVED" && (
              <>
                <input
                  placeholder="سبب الحل (إلزامي)"
                  value={resolutions[incident.id] ?? ""}
                  onChange={(e) =>
                    setResolutions((prev) => ({ ...prev, [incident.id]: e.target.value }))
                  }
                />
                <button
                  onClick={() => void resolve(incident)}
                  disabled={!(resolutions[incident.id] ?? "").trim()}
                >
                  حل
                </button>
              </>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
