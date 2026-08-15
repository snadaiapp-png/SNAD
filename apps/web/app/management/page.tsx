"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import {
  managementApi,
  type CommandCenterDashboard,
  type AlertResponse,
  type InsightResponse,
} from "@/lib/api/management-api";

// ── Health Score Badge ────────────────────────────────────────────────

function HealthScoreBadge({ score }: { score: number }) {
  const color = score >= 80 ? "#16a34a" : score >= 50 ? "#ca8a04" : "#dc2626";
  const label = score >= 80 ? "سليم" : score >= 50 ? "متحفظ" : "حرج";
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
      <div style={{
        width: 60, height: 60, borderRadius: "50%",
        backgroundColor: color, color: "#fff",
        display: "flex", alignItems: "center", justifyContent: "center",
        fontSize: 24, fontWeight: 700,
      }}>
        {score}
      </div>
      <span style={{ fontSize: 14, color: "#666" }}>{label}</span>
    </div>
  );
}

// ── Score Card ────────────────────────────────────────────────────────

function ScoreCard({ label, score, count }: { label: string; score: number; count?: number }) {
  const color = score >= 80 ? "#16a34a" : score >= 50 ? "#ca8a04" : "#dc2626";
  return (
    <div style={{
      padding: 16, borderRadius: 8, border: "1px solid #e5e7eb",
      display: "flex", flexDirection: "column", gap: 4,
    }}>
      <span style={{ fontSize: 13, color: "#666" }}>{label}</span>
      <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
        <span style={{ fontSize: 28, fontWeight: 700, color }}>{score}</span>
        {count !== undefined && <span style={{ fontSize: 12, color: "#999" }}>({count})</span>}
      </div>
      <div style={{ height: 4, borderRadius: 2, backgroundColor: "#f3f4f6" }}>
        <div style={{ width: `${score}%`, height: "100%", borderRadius: 2, backgroundColor: color }} />
      </div>
    </div>
  );
}

// ── Alert Item ───────────────────────────────────────────────────────

function AlertItem({ alert }: { alert: AlertResponse }) {
  const severityColor = alert.severity === "CRITICAL" ? "#dc2626"
    : alert.severity === "HIGH" ? "#ea580c"
    : alert.severity === "MEDIUM" ? "#ca8a04" : "#6b7280";
  return (
    <div style={{
      padding: 12, borderRadius: 8, border: "1px solid #e5e7eb",
      borderRight: `4px solid ${severityColor}`,
      display: "flex", justifyContent: "space-between", alignItems: "center",
    }}>
      <div>
        <div style={{ fontWeight: 600, fontSize: 14 }}>{alert.title}</div>
        <div style={{ fontSize: 12, color: "#666" }}>
          {alert.type.replace(/_/g, " ")} · {alert.sourceEntityType}
        </div>
      </div>
      <span style={{
        padding: "2px 8px", borderRadius: 4, fontSize: 11,
        backgroundColor: severityColor, color: "#fff", fontWeight: 600,
      }}>
        {alert.severity}
      </span>
    </div>
  );
}

// ── Insight Panel ────────────────────────────────────────────────────

function InsightPanel({ insight }: { insight: InsightResponse }) {
  if (!insight) return null;
  return (
    <div style={{
      padding: 16, borderRadius: 8, border: "1px solid #e5e7eb",
      backgroundColor: "#fefce8",
    }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
        <span style={{ fontWeight: 600, fontSize: 14 }}>{insight.title}</span>
        <span style={{
          padding: "2px 8px", borderRadius: 4, fontSize: 10,
          backgroundColor: "#fde68a", color: "#92400e",
        }}>
          استشاري فقط · {insight.modelName}
        </span>
      </div>
      <p style={{ fontSize: 13, color: "#555", lineHeight: 1.6, whiteSpace: "pre-wrap" }}>
        {insight.description}
      </p>
      <div style={{ marginTop: 8, fontSize: 11, color: "#999" }}>
        الثقة: {insight.confidence}
      </div>
    </div>
  );
}

// ── Main Page ────────────────────────────────────────────────────────

export default function ManagementCommandCenterPage() {
  const { state } = useAuth();
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dashboard, setDashboard] = useState<CommandCenterDashboard | null>(null);
  const [alerts, setAlerts] = useState<AlertResponse[]>([]);
  const [insights, setInsights] = useState<InsightResponse[]>([]);
  const [activeTab, setActiveTab] = useState<"overview" | "alerts" | "intelligence">("overview");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [dash, alertList, insightList] = await Promise.all([
        managementApi.getDashboard(),
        managementApi.listOpenAlerts(20),
        managementApi.listInsights(5),
      ]);
      setDashboard(dash);
      setAlerts(alertList || []);
      setInsights(insightList || []);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "فشل تحميل لوحة القيادة";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (state === "AUTHENTICATED") loadData();
  }, [state, loadData]);

  const generateSummary = useCallback(async () => {
    try {
      const insight = await managementApi.generateSummary();
      setInsights(prev => [insight, ...prev]);
    } catch { /* ignore */ }
  }, []);

  const recommendAction = useCallback(async () => {
    try {
      const insight = await managementApi.recommendAction();
      setInsights(prev => [insight, ...prev]);
    } catch { /* ignore */ }
  }, []);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }
  if (state !== "AUTHENTICATED") {
    router.replace("/?returnUrl=%2Fmanagement");
    return <AuthLoadingState phase="workspace" />;
  }

  if (loading) return <AuthLoadingState />;
  if (error) return (
    <div style={{ padding: 24, textAlign: "center", color: "#dc2626" }}>
      {error}
      <button onClick={loadData} style={{ marginLeft: 12, padding: "4px 12px", cursor: "pointer" }}>
        إعادة المحاولة
      </button>
    </div>
  );
  if (!dashboard) return <div style={{ padding: 24 }}>لا توجد بيانات</div>;

  return (
    <ExecutiveShell>
      <div style={{ padding: 24, maxWidth: 1200, margin: "0 auto" }}>
        {/* Header */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
          <h1 style={{ fontSize: 24, fontWeight: 700 }}>مركز القيادة التنفيذية</h1>
          {dashboard && <HealthScoreBadge score={dashboard.healthScore} />}
        </div>

        {/* Tabs */}
        <div style={{ display: "flex", gap: 8, marginBottom: 16, borderBottom: "1px solid #e5e7eb" }}>
          {[
            { id: "overview", label: "نظرة عامة" },
            { id: "alerts", label: `التنبيهات (${alerts.length})` },
            { id: "intelligence", label: "الذكاء التنفيذي" },
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as typeof activeTab)}
              style={{
                padding: "8px 16px", border: "none", borderBottom: activeTab === tab.id ? "2px solid #2563eb" : "none",
                backgroundColor: "transparent", cursor: "pointer", fontSize: 14, fontWeight: activeTab === tab.id ? 600 : 400,
                color: activeTab === tab.id ? "#2563eb" : "#666",
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Overview Tab */}
        {activeTab === "overview" && (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12, marginBottom: 24 }}>
            <ScoreCard label="الأهداف الاستراتيجية" score={dashboard.strategyScore}
              count={dashboard.totalObjectives} />
            <ScoreCard label="مؤشرات الأداء" score={dashboard.kpiScore}
              count={dashboard.totalKpis} />
            <ScoreCard label="القرارات" score={dashboard.decisionScore}
              count={dashboard.pendingDecisions} />
            <ScoreCard label="المخاطر" score={dashboard.riskScore}
              count={dashboard.totalRisks} />
            <ScoreCard label="المشكلات" score={dashboard.issueScore}
              count={dashboard.totalIssues} />
            <ScoreCard label="التصعيدات" score={dashboard.escalationScore}
              count={dashboard.totalEscalations} />
          </div>
        )}

        {/* Summary counts */}
        {activeTab === "overview" && (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8 }}>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "#fef2f2" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "#dc2626" }}>{dashboard.criticalRisks}</div>
              <div style={{ fontSize: 12, color: "#666" }}>مخاطر حرجة</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "#fef2f2" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "#dc2626" }}>{dashboard.criticalIssues}</div>
              <div style={{ fontSize: 12, color: "#666" }}>مشكلات حرجة</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "#fef2f2" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "#dc2626" }}>{dashboard.overdueEscalations}</div>
              <div style={{ fontSize: 12, color: "#666" }}>تصعيدات متأخرة</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "#fff7ed" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "#ea580c" }}>{dashboard.atRiskObjectives}</div>
              <div style={{ fontSize: 12, color: "#666" }}>أهداف معرضة للخطر</div>
            </div>
          </div>
        )}

        {/* Alerts Tab */}
        {activeTab === "alerts" && (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {alerts.length === 0 ? (
              <div style={{ padding: 24, textAlign: "center", color: "#999" }}>
                لا توجد تنبيهات نشطة
              </div>
            ) : (
              alerts.map(alert => <AlertItem key={alert.id} alert={alert} />)
            )}
          </div>
        )}

        {/* Intelligence Tab */}
        {activeTab === "intelligence" && (
          <div>
            <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
              <button onClick={generateSummary} style={{
                padding: "8px 16px", borderRadius: 8, border: "1px solid #2563eb",
                backgroundColor: "#2563eb", color: "#fff", cursor: "pointer", fontSize: 13,
              }}>
                توليد ملخص تنفيذي
              </button>
              <button onClick={recommendAction} style={{
                padding: "8px 16px", borderRadius: 8, border: "1px solid #16a34a",
                backgroundColor: "#16a34a", color: "#fff", cursor: "pointer", fontSize: 13,
              }}>
                توصية إجراء
              </button>
            </div>
            {insights.length === 0 ? (
              <div style={{ padding: 24, textAlign: "center", color: "#999" }}>
                لا توجد رؤى. اضغط على الأزرار أعلاه لتوليد التحليل.
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                {insights.map(insight => (
                  <InsightPanel key={insight.id} insight={insight} />
                ))}
              </div>
            )}
          </div>
        )}

        {/* Footer */}
        <div style={{ marginTop: 24, fontSize: 11, color: "#999" }}>
          آخر تحديث: {new Date(dashboard.generatedAt).toLocaleString("ar-SA")}
        </div>
      </div>
    </ExecutiveShell>
  );
}
