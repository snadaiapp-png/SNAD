"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import { WorkflowOverview } from "./components/workflow-overview";
import { WorkflowMyTasks } from "./components/workflow-my-tasks";
import { WorkflowIncidents } from "./components/workflow-incidents";
import { WorkflowSettings } from "./components/workflow-settings";
import {
  workflowApi,
  type WorkflowDefinitionResponse,
  type WorkflowInstanceResponse,
  type WorkflowApprovalResponse,
  type WorkflowMonitoringHealthResponse,
} from "@/lib/api/workflow-api";

type Tab =
  | "overview"
  | "definitions"
  | "my-tasks"
  | "approvals"
  | "instances"
  | "incidents"
  | "monitoring"
  | "settings";

// ── Status Badge ─────────────────────────────────────────────────────

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    ACTIVE: "var(--snad-color-success)", DRAFT: "var(--snad-color-text-secondary)", INACTIVE: "var(--snad-color-warning)", ARCHIVED: "var(--snad-color-text-muted)",
    RUNNING: "var(--snad-color-info)", PAUSED: "var(--snad-color-warning)", COMPLETED: "var(--snad-color-success)",
    CANCELLED: "var(--snad-color-error)", FAILED: "var(--snad-color-error)",
    PENDING: "var(--snad-color-warning)", APPROVED: "var(--snad-color-success)", REJECTED: "var(--snad-color-error)",
    EXPIRED: "var(--snad-color-text-muted)", CANCELLED_ALT: "var(--snad-color-error)",
  };
  const color = colors[status] || "var(--snad-color-text-secondary)";
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

// ── Definitions Tab ───────────────────────────────────────────────────

function DefinitionsTab() {
  const [defs, setDefs] = useState<WorkflowDefinitionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [newCode, setNewCode] = useState("");
  const [newName, setNewName] = useState("");
  const [newModule, setNewModule] = useState("GENERAL");
  const [newTriggerType, setNewTriggerType] = useState("MANUAL");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await workflowApi.listDefinitions(50);
      setDefs(result);
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      if (err?.status === 401 || err?.status === 403) {
        setError("غير مصرح — يلزم تسجيل الدخول أو صلاحية WORKFLOW.VIEW");
      } else {
        setError(err?.message || "تعذّر تحميل تعريفات سير العمل");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleCreate = async () => {
    if (!newCode || !newName) {
      setError("الرمز والاسم مطلوبان");
      return;
    }
    try {
      await workflowApi.createDefinition({
        code: newCode, name: newName, module: newModule, triggerType: newTriggerType,
      });
      setNewCode(""); setNewName(""); setShowCreate(false);
      await load();
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      setError(err?.message || "فشل إنشاء التعريف");
    }
  };

  const handleActivate = async (id: string) => {
    try {
      await workflowApi.activateDefinition(id);
      await load();
    } catch (e: unknown) {
      setError((e as { message?: string })?.message || "فشل التفعيل");
    }
  };

  if (loading) return <AuthLoadingState />;
  if (error) return (
    <div style={{ padding: 16, color: "var(--snad-color-error)", direction: "rtl" }}>
      <p><strong>خطأ:</strong> {error}</p>
      <button onClick={load} style={btnStyle}>إعادة المحاولة</button>
    </div>
  );

  return (
    <div style={{ direction: "rtl", padding: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <h2 style={{ fontSize: 20, fontWeight: 700 }}>تعريفات سير العمل</h2>
        <button onClick={() => setShowCreate(!showCreate)} style={btnStyle}>
          {showCreate ? "إلغاء" : "+ تعريف جديد"}
        </button>
      </div>

      {showCreate && (
        <div style={{ padding: 16, border: "1px solid var(--snad-color-border-default)", borderRadius: 8, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 12 }}>
            <input placeholder="الرمز (مثال: WF-001)" value={newCode}
              onChange={(e) => setNewCode(e.target.value)} style={inputStyle} />
            <input placeholder="الاسم" value={newName}
              onChange={(e) => setNewName(e.target.value)} style={inputStyle} />
            <select value={newModule} onChange={(e) => setNewModule(e.target.value)} style={inputStyle}>
              <option value="GENERAL">عام</option>
              <option value="MANAGEMENT">الإدارة</option>
              <option value="CRM">إدارة العملاء</option>
              <option value="FINANCE">المالية</option>
              <option value="HR">الموارد البشرية</option>
            </select>
            <select value={newTriggerType} onChange={(e) => setNewTriggerType(e.target.value)} style={inputStyle}>
              <option value="MANUAL">يدوي</option>
              <option value="EVENT">حدث</option>
              <option value="SCHEDULED">مجدول</option>
              <option value="API">API</option>
            </select>
          </div>
          <button onClick={handleCreate} style={{ ...btnStyle, backgroundColor: "var(--snad-color-info)", color: "var(--snad-color-surface-primary)" }}>
            حفظ
          </button>
        </div>
      )}

      {defs.length === 0 ? (
        <div style={{ padding: 24, textAlign: "center", color: "var(--snad-color-text-secondary)" }}>
          لا توجد تعريفات سير عمل بعد. ابدأ بإنشاء تعريف جديد.
        </div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ borderBottom: "2px solid var(--snad-color-border-default)", textAlign: "right" }}>
              <th style={thStyle}>الرمز</th>
              <th style={thStyle}>الاسم</th>
              <th style={thStyle}>الوحدة</th>
              <th style={thStyle}>الإصدار</th>
              <th style={thStyle}>الحالة</th>
              <th style={thStyle}>إجراءات</th>
            </tr>
          </thead>
          <tbody>
            {defs.map((d) => (
              <tr key={d.id} style={{ borderBottom: "1px solid var(--snad-color-surface-secondary)" }}>
                <td style={tdStyle}>{d.code}</td>
                <td style={tdStyle}>{d.name}</td>
                <td style={tdStyle}>{d.module}</td>
                <td style={tdStyle}>v{d.version}.{d.versionLock}</td>
                <td style={tdStyle}><StatusBadge status={d.status} /></td>
                <td style={tdStyle}>
                  {d.status === "DRAFT" && (
                    <button onClick={() => handleActivate(d.id)} style={smallBtnStyle}>تفعيل</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

// ── Instances Tab ─────────────────────────────────────────────────────

function InstancesTab() {
  const [instances, setInstances] = useState<WorkflowInstanceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await workflowApi.listInstances(50);
      setInstances(result);
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      if (err?.status === 401 || err?.status === 403) {
        setError("غير مصرح — يلزم صلاحية WORKFLOW.VIEW");
      } else {
        setError(err?.message || "تعذّر تحميل المثيلات");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  if (loading) return <AuthLoadingState />;
  if (error) return (
    <div style={{ padding: 16, color: "var(--snad-color-error)", direction: "rtl" }}>
      <p><strong>خطأ:</strong> {error}</p>
      <button onClick={load} style={btnStyle}>إعادة المحاولة</button>
    </div>
  );

  return (
    <div style={{ direction: "rtl", padding: 16 }}>
      <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>مثيلات سير العمل</h2>
      {instances.length === 0 ? (
        <div style={{ padding: 24, textAlign: "center", color: "var(--snad-color-text-secondary)" }}>
          لا توجد مثيلات نشطة. ابدأ تشغيل تعريف سير عمل لإنشاء مثيل.
        </div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ borderBottom: "2px solid var(--snad-color-border-default)", textAlign: "right" }}>
              <th style={thStyle}>الكيان</th>
              <th style={thStyle}>معرّف الكيان</th>
              <th style={thStyle}>الخطوة الحالية</th>
              <th style={thStyle}>الإصدار</th>
              <th style={thStyle}>الحالة</th>
            </tr>
          </thead>
          <tbody>
            {instances.map((i) => (
              <tr key={i.id} style={{ borderBottom: "1px solid var(--snad-color-surface-secondary)" }}>
                <td style={tdStyle}>{i.businessEntityType}</td>
                <td style={{ ...tdStyle, fontFamily: "monospace", fontSize: 12 }}>
                  {i.businessEntityId.substring(0, 8)}…
                </td>
                <td style={tdStyle}>{i.currentStepKey || "—"}</td>
                <td style={tdStyle}>v{i.workflowVersion}.{i.version}</td>
                <td style={tdStyle}><StatusBadge status={i.status} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

// ── Approvals Tab ─────────────────────────────────────────────────────

function ApprovalsTab() {
  const [approvals, setApprovals] = useState<WorkflowApprovalResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actioningId, setActioningId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await workflowApi.listPendingApprovals(50);
      setApprovals(result);
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      if (err?.status === 401 || err?.status === 403) {
        setError("غير مصرح — يلزم صلاحية WORKFLOW.VIEW");
      } else {
        setError(err?.message || "تعذّر تحميل طلبات الموافقة");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleApprove = async (id: string) => {
    setActioningId(id);
    try {
      await workflowApi.approveRequest(id, "تمت الموافقة");
      await load();
    } catch (e: unknown) {
      setError((e as { message?: string })?.message || "فشل الموافقة");
    } finally {
      setActioningId(null);
    }
  };

  const handleReject = async (id: string) => {
    setActioningId(id);
    try {
      await workflowApi.rejectRequest(id, "مرفوض");
      await load();
    } catch (e: unknown) {
      setError((e as { message?: string })?.message || "فشل الرفض");
    } finally {
      setActioningId(null);
    }
  };

  if (loading) return <AuthLoadingState />;
  if (error) return (
    <div style={{ padding: 16, color: "var(--snad-color-error)", direction: "rtl" }}>
      <p><strong>خطأ:</strong> {error}</p>
      <button onClick={load} style={btnStyle}>إعادة المحاولة</button>
    </div>
  );

  return (
    <div style={{ direction: "rtl", padding: 16 }}>
      <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>قائمة انتظار الموافقات</h2>
      {approvals.length === 0 ? (
        <div style={{ padding: 24, textAlign: "center", color: "var(--snad-color-text-secondary)" }}>
          لا توجد طلبات موافقة معلّقة.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {approvals.map((a) => (
            <div key={a.id} style={{
              padding: 16, border: "1px solid var(--snad-color-border-default)", borderRadius: 8,
              display: "flex", justifyContent: "space-between", alignItems: "center",
              flexWrap: "wrap", gap: 8,
            }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600 }}>
                  طلب موافقة #{a.id.substring(0, 8)}…
                </div>
                <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)", marginTop: 4 }}>
                  المثيل: {a.workflowInstanceId.substring(0, 8)}… · الحالة: <StatusBadge status={a.status} />
                </div>
                {a.comments && (
                  <div style={{ fontSize: 12, color: "var(--snad-color-text-primary)", marginTop: 4 }}>
                    ملاحظات: {a.comments}
                  </div>
                )}
              </div>
              {a.status === "PENDING" && (
                <div style={{ display: "flex", gap: 8 }}>
                  <button
                    onClick={() => handleApprove(a.id)}
                    disabled={actioningId === a.id}
                    style={{ ...smallBtnStyle, backgroundColor: "var(--snad-color-success)", color: "var(--snad-color-surface-primary)" }}>
                    {actioningId === a.id ? "…" : "موافقة"}
                  </button>
                  <button
                    onClick={() => handleReject(a.id)}
                    disabled={actioningId === a.id}
                    style={{ ...smallBtnStyle, backgroundColor: "var(--snad-color-error)", color: "var(--snad-color-surface-primary)" }}>
                    {actioningId === a.id ? "…" : "رفض"}
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Monitoring Tab ─────────────────────────────────────────────────────

function MonitoringTab() {
  const [health, setHealth] = useState<WorkflowMonitoringHealthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await workflowApi.getMonitoringHealth();
      setHealth(result);
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      if (err?.status === 401 || err?.status === 403) {
        setError("غير مصرح — يلزم صلاحية WORKFLOW.VIEW");
      } else {
        setError(err?.message || "تعذّر تحميل حالة المراقبة");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  if (loading) return <AuthLoadingState />;
  if (error || !health) return (
    <div style={{ padding: 16, color: "var(--snad-color-error)", direction: "rtl" }}>
      <p><strong>خطأ:</strong> {error || "لا توجد بيانات"}</p>
      <button onClick={load} style={btnStyle}>إعادة المحاولة</button>
    </div>
  );

  return (
    <div style={{ direction: "rtl", padding: 16 }}>
      <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>مراقبة مستوى الخدمة (SLA)</h2>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12 }}>
        <div style={cardStyle}>
          <div style={{ fontSize: 13, color: "var(--snad-color-text-secondary)" }}>الحالة</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-success)" }}>{health.status}</div>
        </div>
        <div style={cardStyle}>
          <div style={{ fontSize: 13, color: "var(--snad-color-text-secondary)" }}>خطوات متأخرة</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: health.overdueSteps > 0 ? "var(--snad-color-error)" : "var(--snad-color-success)" }}>
            {health.overdueSteps}
          </div>
        </div>
        <div style={cardStyle}>
          <div style={{ fontSize: 13, color: "var(--snad-color-text-secondary)" }}>موافقات متأخرة</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: health.overdueApprovals > 0 ? "var(--snad-color-error)" : "var(--snad-color-success)" }}>
            {health.overdueApprovals}
          </div>
        </div>
        <div style={cardStyle}>
          <div style={{ fontSize: 13, color: "var(--snad-color-text-secondary)" }}>إجمالي المخالفات</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: health.totalBreaches > 0 ? "var(--snad-color-error)" : "var(--snad-color-success)" }}>
            {health.totalBreaches}
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────

const btnStyle: React.CSSProperties = {
  padding: "8px 16px", borderRadius: 6, border: "1px solid var(--snad-color-border-default)",
  backgroundColor: "var(--snad-color-surface-primary)", color: "var(--snad-color-text-primary)", cursor: "pointer", fontSize: 14,
};
const smallBtnStyle: React.CSSProperties = {
  padding: "4px 12px", borderRadius: 4, border: "none",
  cursor: "pointer", fontSize: 13,
};
const inputStyle: React.CSSProperties = {
  padding: "8px 12px", borderRadius: 6, border: "1px solid var(--snad-color-border-default)",
  fontSize: 14, width: "100%",
};
const thStyle: React.CSSProperties = {
  padding: "8px 12px", fontSize: 13, fontWeight: 600, color: "var(--snad-color-text-primary)",
};
const tdStyle: React.CSSProperties = {
  padding: "12px", fontSize: 14, color: "var(--snad-color-text-primary)",
};
const cardStyle: React.CSSProperties = {
  padding: 16, borderRadius: 8, border: "1px solid var(--snad-color-border-default)",
};

export default function WorkflowPage() {
  const router = useRouter();
  const { state, user } = useAuth();
  const [activeTab, setActiveTab] = useState<Tab>("overview");

  useEffect(() => {
    if (state !== "INITIALIZING" && state !== "CHECKING_SESSION" && !user) {
      router.push("/identity/login?from=/workflow");
    }
  }, [state, user, router]);

  const isLoading = state === "INITIALIZING" || state === "CHECKING_SESSION" || state === "AUTHENTICATING";
  if (isLoading || !user) return <AuthLoadingState />;

  const tabs: { id: Tab; labelAr: string }[] = [
    { id: "overview", labelAr: "نظرة عامة" },
    { id: "definitions", labelAr: "التعريفات" },
    { id: "my-tasks", labelAr: "مهامي" },
    { id: "approvals", labelAr: "الموافقات" },
    { id: "instances", labelAr: "المثيلات" },
    { id: "incidents", labelAr: "الحوادث" },
    { id: "monitoring", labelAr: "المراقبة" },
    { id: "settings", labelAr: "الإعدادات" },
  ];

  return (
    <ExecutiveShell>
      <div style={{
        padding: "24px 16px", maxWidth: 1280, margin: "0 auto",
        direction: "rtl", fontFamily: "system-ui, -apple-system, sans-serif",
      }}>
        <header style={{ marginBottom: 24 }}>
          <h1 style={{ fontSize: 28, fontWeight: 700, color: "var(--snad-color-text-primary)", marginBottom: 8 }}>
            محرك سير العمل
          </h1>
          <p style={{ color: "var(--snad-color-text-secondary)", fontSize: 14 }}>
            إدارة تعريفات سير العمل، تشغيل المثيلات، ومعالجة طلبات الموافقة
          </p>
        </header>

        <nav style={{
          display: "flex", gap: 4, borderBottom: "1px solid var(--snad-color-border-default)",
          marginBottom: 16, overflowX: "auto",
        }}>
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setActiveTab(t.id)}
              style={{
                padding: "12px 20px", cursor: "pointer", fontSize: 14,
                fontWeight: activeTab === t.id ? 600 : 400,
                color: activeTab === t.id ? "var(--snad-color-info)" : "var(--snad-color-text-secondary)",
                borderBottom: activeTab === t.id ? "2px solid var(--snad-color-info)" : "2px solid transparent",
                backgroundColor: "transparent", border: "none", borderLeft: "1px solid var(--snad-color-border-default)",
                borderRight: "1px solid var(--snad-color-border-default)", borderTop: "none",
                whiteSpace: "nowrap",
              }}
            >
              {t.labelAr}
            </button>
          ))}
        </nav>

        {activeTab === "overview" && <WorkflowOverview />}
        {activeTab === "definitions" && <DefinitionsTab />}
        {activeTab === "my-tasks" && <WorkflowMyTasks />}
        {activeTab === "approvals" && <ApprovalsTab />}
        {activeTab === "instances" && <InstancesTab />}
        {activeTab === "incidents" && <WorkflowIncidents />}
        {activeTab === "monitoring" && <MonitoringTab />}
        {activeTab === "settings" && <WorkflowSettings />}
      </div>
    </ExecutiveShell>
  );
}
