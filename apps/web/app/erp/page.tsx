"use client";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import { erpApi, type ErpDashboardSummary, type ItemResponse } from "@/lib/api/erp-api";

export default function ErpPage() {
  const { state } = useAuth();
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [summary, setSummary] = useState<ErpDashboardSummary | null>(null);
  const [items, setItems] = useState<ItemResponse[]>([]);

  const loadData = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const [dash, list] = await Promise.all([erpApi.dashboard(), erpApi.listItems()]);
      setSummary(dash); setItems(list || []);
    } catch (e: unknown) { setError(e instanceof Error ? e.message : "فشل تحميل ERP"); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { if (state === "AUTHENTICATED") loadData(); }, [state, loadData]);

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (state !== "AUTHENTICATED") { router.replace("/?returnUrl=%2Ferp"); return <AuthLoadingState phase="workspace" />; }
  if (loading) return <AuthLoadingState />;
  if (error) return (<div style={{ padding: 24, textAlign: "center", color: "var(--snad-color-error)" }}>{error}<button onClick={loadData} style={{ marginLeft: 12, padding: "4px 12px", cursor: "pointer" }}>إعادة المحاولة</button></div>);

  const sc: Record<string,string> = { DRAFT:"var(--snad-color-warning)", ACTIVE:"var(--snad-color-success)", INACTIVE:"var(--snad-color-text-secondary)", ARCHIVED:"var(--snad-color-text-muted)" };
  const sl: Record<string,string> = { DRAFT:"مسودة", ACTIVE:"نشط", INACTIVE:"غير نشط", ARCHIVED:"مؤرشف" };

  return (
    <ExecutiveShell>
      <div style={{ padding: 24, maxWidth: 1200, margin: "0 auto" }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>منصة ERP</h1>
        {summary && (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8, marginBottom: 24 }}>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-info-soft)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-info)" }}>{summary.totalItems}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>الأصناف</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-success-soft)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-success)" }}>{summary.totalWarehouses}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>المستودعات</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-warning-soft)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-warning)" }}>{summary.lowStockItems}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>مخزون منخفض</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-surface-secondary)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-brand-accent)" }}>{summary.pendingPurchaseOrders}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>أوامر شراء معلقة</div>
            </div>
          </div>
        )}
        {items.length === 0 ? (
          <div style={{ padding: 48, textAlign: "center", color: "var(--snad-color-text-muted)" }}>لا توجد أصناف بعد.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {items.map((i) => (
              <div key={i.id} style={{ padding: 16, borderRadius: 8, border: "1px solid var(--snad-color-border-default)", backgroundColor: "var(--snad-color-surface-primary)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 600 }}>{i.name}</div>
                  <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>{i.code} · {i.sku || "—"} · {i.unitOfMeasure}</div>
                </div>
                <span style={{ padding: "4px 12px", borderRadius: 12, fontSize: 12, fontWeight: 500, backgroundColor: (sc[i.status]||"var(--snad-color-text-secondary)")+"20", color: sc[i.status]||"var(--snad-color-text-secondary)" }}>{sl[i.status]||i.status}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </ExecutiveShell>
  );
}
