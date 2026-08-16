"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import { websiteApi, type WebsiteResponse, type WebsiteSummary } from "@/lib/api/website-api";

export default function WebsitesPage() {
  const { state } = useAuth();
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [websites, setWebsites] = useState<WebsiteResponse[]>([]);
  const [summary, setSummary] = useState<WebsiteSummary | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [list, sum] = await Promise.all([
        websiteApi.list(),
        websiteApi.summary(),
      ]);
      setWebsites(list || []);
      setSummary(sum);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "فشل تحميل المواقع");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (state === "AUTHENTICATED") loadData();
  }, [state, loadData]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }
  if (state !== "AUTHENTICATED") {
    router.replace(`/?returnUrl=%2Fwebsites`);
    return <AuthLoadingState phase="workspace" />;
  }
  if (loading) return <AuthLoadingState />;
  if (error) return (
    <div style={{ padding: 24, textAlign: "center", color: "var(--snad-color-error)" }}>
      {error}
      <button onClick={loadData} style={{ marginLeft: 12, padding: "4px 12px", cursor: "pointer" }}>
        إعادة المحاولة
      </button>
    </div>
  );

  const statusColors: Record<string, string> = {
    DRAFT: "var(--snad-color-warning)", ACTIVE: "var(--snad-color-success)", SUSPENDED: "var(--snad-color-warning)", ARCHIVED: "var(--snad-color-text-secondary)",
  };
  const statusLabels: Record<string, string> = {
    DRAFT: "مسودة", ACTIVE: "نشط", SUSPENDED: "معلق", ARCHIVED: "مؤرشف",
  };

  return (
    <ExecutiveShell>
      <div style={{ padding: 24, maxWidth: 1200, margin: "0 auto" }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>منصة المواقع</h1>

        {summary && (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8, marginBottom: 24 }}>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-success-soft)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-success)" }}>{summary.totalWebsites}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>إجمالي المواقع</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-info-soft)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-info)" }}>{summary.activeWebsites}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>مواقع نشطة</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-warning-soft)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-warning)" }}>{summary.publishedPages}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>صفحات منشورة</div>
            </div>
            <div style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-surface-secondary)", textAlign: "center" }}>
              <div style={{ fontSize: 24, fontWeight: 700, color: "var(--snad-color-brand-accent)" }}>{summary.activeDomains}</div>
              <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>نطاقات نشطة</div>
            </div>
          </div>
        )}

        {websites.length === 0 ? (
          <div style={{ padding: 48, textAlign: "center", color: "var(--snad-color-text-muted)" }}>
            لا توجد مواقع بعد. أنشئ موقعك الأول.
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {websites.map((w) => (
              <div key={w.id} style={{
                padding: 16, borderRadius: 8, border: "1px solid var(--snad-color-border-default)", backgroundColor: "var(--snad-color-surface-primary)",
                display: "flex", justifyContent: "space-between", alignItems: "center",
              }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 600 }}>{w.name}</div>
                  <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>/{w.slug} · {w.defaultLocale}</div>
                </div>
                <span style={{
                  padding: "4px 12px", borderRadius: 12, fontSize: 12, fontWeight: 500,
                  backgroundColor: `color-mix(in srgb, ${statusColors[w.status]} 12%, transparent)`, color: statusColors[w.status],
                }}>
                  {statusLabels[w.status] || w.status}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </ExecutiveShell>
  );
}
