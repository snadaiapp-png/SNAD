"use client";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import { storeApi, type StoreResponse, type StoreSummary } from "@/lib/api/store-api";

export default function StoresPage() {
  const { state } = useAuth();
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [stores, setStores] = useState<StoreResponse[]>([]);
  const [summary, setSummary] = useState<StoreSummary | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const [list, sum] = await Promise.all([storeApi.list(), storeApi.summary()]);
      setStores(list || []); setSummary(sum);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "فشل تحميل المتاجر");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { if (state === "AUTHENTICATED") loadData(); }, [state, loadData]);

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (state !== "AUTHENTICATED") { router.replace("/?returnUrl=%2Fstores"); return <AuthLoadingState phase="workspace" />; }
  if (loading) return <AuthLoadingState />;
  if (error) return (<div style={{ padding: 24, textAlign: "center", color: "var(--snad-color-error)" }}>{error}<button onClick={loadData} style={{ marginLeft: 12, padding: "4px 12px", cursor: "pointer" }}>إعادة المحاولة</button></div>);

  const sc: Record<string,string> = { DRAFT:"var(--snad-color-warning)", ACTIVE:"var(--snad-color-success)", SUSPENDED:"var(--snad-color-warning)", ARCHIVED:"var(--snad-color-text-secondary)" };
  const sl: Record<string,string> = { DRAFT:"مسودة", ACTIVE:"نشط", SUSPENDED:"معلق", ARCHIVED:"مؤرشف" };

  return (
    <ExecutiveShell>
      <div style={{ padding: 24, maxWidth: 1200, margin: "0 auto" }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>منصة المتاجر</h1>
        {summary && (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8, marginBottom: 24 }}>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-success-soft)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-success)" }}>{summary.totalStores}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>إجمالي المتاجر</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-info-soft)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-info)" }}>{summary.activeStores}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>متاجر نشطة</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-warning-soft)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-warning)" }}>{summary.publishedProducts}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>منتجات منشورة</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-surface-secondary)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-brand-accent)" }}>{summary.totalOrders}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>الطلبات</div>
            </div>
          </div>
        )}
        {stores.length === 0 ? (
          <div style={{ padding: 48, textAlign: "center", color: "var(--snad-color-text-muted)" }}>لا توجد متاجر بعد. أنشئ متجرك الأول.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {stores.map((s) => (
              <div key={s.id} style={{ padding: 16, borderRadius: 8, border: "1px solid var(--snad-color-border-default)", backgroundColor: "var(--snad-color-surface-primary)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 600 }}>{s.name}</div>
                  <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>/{s.slug} · {s.defaultCurrency}</div>
                </div>
                <span style={{ padding: "4px 12px", borderRadius: 12, fontSize: 12, fontWeight: 500, backgroundColor: (sc[s.status]||"var(--snad-color-text-secondary)")+"20", color: sc[s.status]||"var(--snad-color-text-secondary)" }}>{sl[s.status]||s.status}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </ExecutiveShell>
  );
}
