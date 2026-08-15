"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import {
  aiApi,
  type AiAgentResponse,
  type AiInferenceResponse,
  type AiQuotaResponse,
} from "@/lib/api/ai-api";

type Tab = "agents" | "inferences" | "quota";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    DRAFT: "#6b7280", ACTIVE: "#16a34a", INACTIVE: "#ca8a04", ARCHIVED: "#9ca3af",
    PENDING: "#ca8a04", COMPLETED: "#16a34a", FAILED: "#dc2626", TIMEOUT: "#dc2626",
    CANCELLED: "#9ca3af",
  };
  const color = colors[status] || "#6b7280";
  return (
    <span style={{
      display: "inline-block", padding: "2px 10px",
      borderRadius: 12, backgroundColor: color + "20", color,
      fontSize: 12, fontWeight: 600,
    }}>
      {status}
    </span>
  );
}

function AgentsTab() {
  const [agents, setAgents] = useState<AiAgentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await aiApi.listAgents(50);
      setAgents(result);
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      if (err?.status === 401 || err?.status === 403) {
        setError("غير مصرح — يلزم صلاحية AI.VIEW");
      } else {
        setError(err?.message || "تعذّر تحميل وكلاء الذكاء الاصطناعي");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  if (loading) return <AuthLoadingState />;
  if (error) return (
    <div style={{ padding: 16, color: "#dc2626", direction: "rtl" }}>
      <p><strong>خطأ:</strong> {error}</p>
      <button onClick={load} style={btnStyle}>إعادة المحاولة</button>
    </div>
  );

  return (
    <div style={{ direction: "rtl", padding: 16 }}>
      <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>وكلاء الذكاء الاصطناعي</h2>
      {agents.length === 0 ? (
        <div style={{ padding: 24, textAlign: "center", color: "#6b7280" }}>
          لا يوجد وكلاء ذكاء اصطناعي مسجلون.
        </div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ borderBottom: "2px solid #e5e7eb", textAlign: "right" }}>
              <th style={thStyle}>الرمز</th>
              <th style={thStyle}>الاسم</th>
              <th style={thStyle}>المزود</th>
              <th style={thStyle}>الحالة</th>
              <th style={thStyle}>استشاري فقط</th>
            </tr>
          </thead>
          <tbody>
            {agents.map((a) => (
              <tr key={a.id} style={{ borderBottom: "1px solid #f3f4f6" }}>
                <td style={tdStyle}>{a.code}</td>
                <td style={tdStyle}>{a.name}</td>
                <td style={tdStyle}>{a.provider}</td>
                <td style={tdStyle}><StatusBadge status={a.status} /></td>
                <td style={tdStyle}>{a.advisoryOnly ? "نعم" : "لا"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function InferencesTab() {
  const [inferences, setInferences] = useState<AiInferenceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await aiApi.listInferences(50);
      setInferences(result);
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      if (err?.status === 401 || err?.status === 403) {
        setError("غير مصرح — يلزم صلاحية AI.VIEW");
      } else {
        setError(err?.message || "تعذّر تحميل سجلات الاستدلال");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  if (loading) return <AuthLoadingState />;
  if (error) return (
    <div style={{ padding: 16, color: "#dc2626", direction: "rtl" }}>
      <p><strong>خطأ:</strong> {error}</p>
      <button onClick={load} style={btnStyle}>إعادة المحاولة</button>
    </div>
  );

  return (
    <div style={{ direction: "rtl", padding: 16 }}>
      <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>سجلات الاستدلال</h2>
      {inferences.length === 0 ? (
        <div style={{ padding: 24, textAlign: "center", color: "#6b7280" }}>
          لا توجد سجلات استدلال بعد.
        </div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ borderBottom: "2px solid #e5e7eb", textAlign: "right" }}>
              <th style={thStyle}>الحالة</th>
              <th style={thStyle}>استشاري</th>
              <th style={thStyle}>الرموز (دخول/خروج)</th>
              <th style={thStyle}>الزمن (ms)</th>
              <th style={thStyle}>التكلفة (سنت)</th>
              <th style={thStyle}>التاريخ</th>
            </tr>
          </thead>
          <tbody>
            {inferences.map((i) => (
              <tr key={i.id} style={{ borderBottom: "1px solid #f3f4f6" }}>
                <td style={tdStyle}><StatusBadge status={i.status} /></td>
                <td style={tdStyle}>{i.advisory ? "نعم" : "لا"}</td>
                <td style={tdStyle}>{i.tokensInput} / {i.tokensOutput}</td>
                <td style={tdStyle}>{i.latencyMs}</td>
                <td style={tdStyle}>{i.costCents}</td>
                <td style={{ ...tdStyle, fontSize: 12 }}>{new Date(i.createdAt).toLocaleString("ar")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function QuotaTab() {
  const [quota, setQuota] = useState<AiQuotaResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await aiApi.getQuota();
      setQuota(result);
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      if (err?.status === 401 || err?.status === 403) {
        setError("غير مصرح — يلزم صلاحية AI.VIEW");
      } else {
        setError(err?.message || "تعذّر تحميل الحصة");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  if (loading) return <AuthLoadingState />;
  if (error || !quota) return (
    <div style={{ padding: 16, color: "#dc2626", direction: "rtl" }}>
      <p><strong>خطأ:</strong> {error || "لا توجد بيانات"}</p>
      <button onClick={load} style={btnStyle}>إعادة المحاولة</button>
    </div>
  );

  return (
    <div style={{ direction: "rtl", padding: 16 }}>
      <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>حصة الذكاء الاصطناعي</h2>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12 }}>
        <div style={cardStyle}>
          <div style={{ fontSize: 13, color: "#6b7280" }}>العمليات هذا الشهر</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: "#2563eb" }}>
            {quota.usedThisMonth}
          </div>
        </div>
        <div style={cardStyle}>
          <div style={{ fontSize: 13, color: "#6b7280" }}>الوضع الاستشاري</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: "#16a34a" }}>
            {quota.advisoryOnly ? "مفعّل" : "معطّل"}
          </div>
        </div>
      </div>
    </div>
  );
}

const btnStyle: React.CSSProperties = {
  padding: "8px 16px", borderRadius: 6, border: "1px solid #e5e7eb",
  backgroundColor: "#fff", color: "#374151", cursor: "pointer", fontSize: 14,
};
const thStyle: React.CSSProperties = {
  padding: "8px 12px", fontSize: 13, fontWeight: 600, color: "#374151",
};
const tdStyle: React.CSSProperties = {
  padding: "12px", fontSize: 14, color: "#374151",
};
const cardStyle: React.CSSProperties = {
  padding: 16, borderRadius: 8, border: "1px solid #e5e7eb",
};

export default function AiPlatformPage() {
  const router = useRouter();
  const { state, user } = useAuth();
  const [activeTab, setActiveTab] = useState<Tab>("agents");

  useEffect(() => {
    if (state !== "INITIALIZING" && state !== "CHECKING_SESSION" && !user) {
      router.push("/identity/login?from=/ai-platform");
    }
  }, [state, user, router]);

  const isLoading = state === "INITIALIZING" || state === "CHECKING_SESSION" || state === "AUTHENTICATING";
  if (isLoading || !user) return <AuthLoadingState />;

  const tabs: { id: Tab; labelAr: string }[] = [
    { id: "agents", labelAr: "الوكلاء" },
    { id: "inferences", labelAr: "الاستدلالات" },
    { id: "quota", labelAr: "الحصة" },
  ];

  return (
    <ExecutiveShell>
      <div style={{
        padding: "24px 16px", maxWidth: 1280, margin: "0 auto",
        direction: "rtl", fontFamily: "system-ui, -apple-system, sans-serif",
      }}>
        <header style={{ marginBottom: 24 }}>
          <h1 style={{ fontSize: 28, fontWeight: 700, color: "#111827", marginBottom: 8 }}>
            منصة الذكاء الاصطناعي
          </h1>
          <p style={{ color: "#6b7280", fontSize: 14 }}>
            إدارة وكلاء الذكاء الاصطناعي ومراقبة الاستدلالات — جميع المخرجات استشارية فقط
          </p>
        </header>

        <nav style={{
          display: "flex", gap: 4, borderBottom: "1px solid #e5e7eb",
          marginBottom: 16, overflowX: "auto",
        }}>
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setActiveTab(t.id)}
              style={{
                padding: "12px 20px", cursor: "pointer", fontSize: 14,
                fontWeight: activeTab === t.id ? 600 : 400,
                color: activeTab === t.id ? "#2563eb" : "#6b7280",
                borderBottom: activeTab === t.id ? "2px solid #2563eb" : "2px solid transparent",
                backgroundColor: "transparent", border: "none", borderLeft: "1px solid #e5e7eb",
                borderRight: "1px solid #e5e7eb", borderTop: "none",
                whiteSpace: "nowrap",
              }}
            >
              {t.labelAr}
            </button>
          ))}
        </nav>

        {activeTab === "agents" && <AgentsTab />}
        {activeTab === "inferences" && <InferencesTab />}
        {activeTab === "quota" && <QuotaTab />}
      </div>
    </ExecutiveShell>
  );
}
