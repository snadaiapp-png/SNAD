"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import { HrExecutionProvider } from "./hr-execution-provider";
import type { ExecutionProgram, ExecutionProgress } from "@/lib/execution";

/**
 * HR Module — Foundation Recovery Page
 *
 * Status: FOUNDATION_RECOVERED_READY_FOR_DEVELOPMENT
 *
 * The HR module has:
 * - Execution data (hr-execution-data.ts) with 8 groups and 32+ tasks
 * - Execution provider (hr-execution-provider.ts) implementing the shared framework
 * - NO backend implementation yet (no Java controllers, services, or DB tables)
 *
 * This page shows the execution plan and current status (all NOT_STARTED).
 * It is NOT a placeholder — it provides real value by showing the planned scope
 * and tracking implementation progress.
 */
export default function HrPage() {
  const { state } = useAuth();
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [program, setProgram] = useState<ExecutionProgram | null>(null);
  const [progress, setProgress] = useState<ExecutionProgress | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const provider = new HrExecutionProvider();
      const programs = await provider.getPrograms();
      if (programs.length > 0) {
        setProgram(programs[0]);
        const prog = await provider.getProgramProgress(programs[0].id);
        setProgress(prog);
      }
    } catch (e) {
      console.error("Failed to load HR execution data:", e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (state === "AUTHENTICATED") loadData();
  }, [state, loadData]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;
  if (state !== "AUTHENTICATED") {
    router.replace("/?returnUrl=%2Fhr");
    return <AuthLoadingState phase="workspace" />;
  }
  if (loading) return <AuthLoadingState />;

  return (
    <ExecutiveShell>
      <div style={{ padding: "1.5rem", maxWidth: "1200px", margin: "0 auto" }}>
        {/* Header */}
        <header style={{ marginBottom: "2rem" }}>
          <h1 style={{ fontSize: "1.75rem", fontWeight: 700, margin: 0 }}>
            الموارد البشرية
          </h1>
          <p style={{ color: "var(--snad-text-muted, #8b949e)", marginTop: "0.5rem" }}>
            إدارة الموظفين والهيكل التنظيمي والحضور والإجازات والرواتب
          </p>
          <div style={{
            marginTop: "0.75rem", padding: "6px 12px", borderRadius: "4px",
            display: "inline-block", fontSize: "0.75rem",
            background: "rgba(251,191,36,0.1)", color: "var(--snad-warning, #fbbf24)",
            border: "1px solid rgba(251,191,36,0.2)",
          }}>
            FOUNDATION_RECOVERED_READY_FOR_DEVELOPMENT
          </div>
        </header>

        {/* Progress Overview */}
        {progress && (
          <div style={{
            padding: "1rem 1.25rem", marginBottom: "2rem", borderRadius: "0.5rem",
            border: "1px solid var(--snad-border, #30363d)",
            background: "var(--snad-surface, #161b22)",
          }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span style={{ fontSize: "0.875rem", color: "var(--snad-text-muted, #8b949e)" }}>
                نسبة التنفيذ الإجمالية
              </span>
              <span style={{ fontSize: "1.5rem", fontWeight: 700, color: "var(--snad-warning, #fbbf24)" }}>
                {progress.percentage}%
              </span>
            </div>
            <div style={{
              marginTop: "0.5rem", height: "8px", borderRadius: "4px",
              background: "var(--snad-border, #30363d)", overflow: "hidden",
            }}>
              <div style={{
                height: "100%", width: `${progress.percentage}%`,
                background: "var(--snad-warning, #fbbf24)", borderRadius: "4px",
                transition: "width 0.3s ease",
              }} />
            </div>
            <div style={{ marginTop: "0.5rem", fontSize: "0.75rem", color: "var(--snad-text-dim, #6e7681)" }}>
              المهام: {progress.total} | مكتمل: {progress.done} | قيد التنفيذ: {progress.inProgress} | لم يبدأ: {progress.notStarted}
            </div>
          </div>
        )}

        {/* Execution Groups */}
        {program && (
          <section>
            <h2 style={{ fontSize: "1.25rem", fontWeight: 600, marginBottom: "1rem" }}>
              مجموعات التنفيذ
            </h2>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: "1rem" }}>
              {program.groups.map((group) => (
                <div key={group.code} style={{
                  padding: "1rem 1.25rem", borderRadius: "0.5rem",
                  border: "1px solid var(--snad-border, #30363d)",
                  background: "var(--snad-surface, #161b22)",
                }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "start" }}>
                    <div>
                      <span style={{
                        fontSize: "0.75rem", color: "var(--snad-text-dim, #6e7681)",
                        fontFamily: "monospace",
                      }}>{group.code}</span>
                      <h3 style={{ fontSize: "1rem", fontWeight: 600, margin: "4px 0 0 0" }}>
                        {group.titleAr}
                      </h3>
                    </div>
                    <span style={{
                      padding: "2px 8px", borderRadius: "4px", fontSize: "0.6875rem",
                      background: group.status === "DONE" ? "rgba(74,222,128,0.1)"
                        : group.status === "IN_PROGRESS" ? "rgba(45,212,191,0.1)"
                        : "rgba(139,148,158,0.1)",
                      color: group.status === "DONE" ? "var(--snad-success, #4ade80)"
                        : group.status === "IN_PROGRESS" ? "var(--snad-primary, #2dd4bf)"
                        : "var(--snad-text-muted, #8b949e)",
                    }}>
                      {group.status.replace(/_/g, " ")}
                    </span>
                  </div>
                  <p style={{
                    fontSize: "0.8125rem", color: "var(--snad-text-muted, #8b949e)",
                    marginTop: "0.5rem", lineHeight: 1.5,
                  }}>
                    {group.purposeAr}
                  </p>
                  <div style={{ marginTop: "0.75rem", fontSize: "0.75rem", color: "var(--snad-text-dim, #6e7681)" }}>
                    المهام: {group.tasks.length}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}
      </div>
    </ExecutiveShell>
  );
}
