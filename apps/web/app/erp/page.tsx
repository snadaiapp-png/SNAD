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
  if (error) return (<div style={{ padding: 24, textAlign: "center", color: "#dc2626" }}>{error}<button onClick={loadData} style={{ marginLeft: 12, padding: "4px 12px", cursor: "pointer" }}>إعادة المحاولة</button></div>);

  const sc: Record<string,string> = { DRAFT:"#ca8a04", ACTIVE:"#16a34a", INACTIVE:"#6b7280", ARCHIVED:"#999" };
  const sl: Record<string,string> = { DRAFT:"مسودة", ACTIVE:"نشط", INACTIVE:"غير نشط", ARCHIVED:"مؤرشف" };

  return (
    <ExecutiveShell>
      <div style={{ padding: 24, maxWidth: 1200, margin: "0 auto" }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>منصة ERP</h1>
        {summary && (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8, marginBottom: 24 }}>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "#eff6ff", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "#2563eb" }}>{summary.totalItems}</div>
              <div style={{ fontSize: 12, color: "#666" }}>الأصناف</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "#f0fdf4", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "#16a34a" }}>{summary.totalWarehouses}</div>
              <div style={{ fontSize: 12, color: "#666" }}>المستودعات</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "#fef3c7", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "#ca8a04" }}>{summary.lowStockItems}</div>
              <div style={{ fontSize: 12, color: "#666" }}>مخزون منخفض</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "#f5f3ff", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "#7c3aed" }}>{summary.pendingPurchaseOrders}</div>
              <div style={{ fontSize: 12, color: "#666" }}>أوامر شراء معلقة</div>
            </div>
          </div>
        )}
        {items.length === 0 ? (
          <div style={{ padding: 48, textAlign: "center", color: "#999" }}>لا توجد أصناف بعد.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {items.map((i) => (
              <div key={i.id} style={{ padding: 16, borderRadius: 8, border: "1px solid #e5e7eb", backgroundColor: "#fff", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 600 }}>{i.name}</div>
                  <div style={{ fontSize: 12, color: "#666" }}>{i.code} · {i.sku || "—"} · {i.unitOfMeasure}</div>
                </div>
                <span style={{ padding: "4px 12px", borderRadius: 12, fontSize: 12, fontWeight: 500, backgroundColor: (sc[i.status]||"#666")+"20", color: sc[i.status]||"#666" }}>{sl[i.status]||i.status}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </ExecutiveShell>
  );
}
