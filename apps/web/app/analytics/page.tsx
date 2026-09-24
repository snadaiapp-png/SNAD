"use client";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import { analyticsApi, type AnalyticsDashboardResponse, type AnalyticsReportResponse, type AnalyticsDataSourceResponse } from "@/lib/api/analytics-api";

type Tab = "dashboards" | "reports" | "sources";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = { ACTIVE: "#16a34a", DRAFT: "#6b7280", INACTIVE: "#ca8a04", ARCHIVED: "#9ca3af", PENDING: "#ca8a04", SCHEDULED: "#2563eb", ERROR: "#dc2626" };
  const c = colors[status] || "#6b7280";
  return <span style={{ display: "inline-block", padding: "2px 10px", borderRadius: 12, backgroundColor: c + "20", color: c, fontSize: 12, fontWeight: 600 }}>{status}</span>;
}

function DashboardsTab() {
  const [items, setItems] = useState<AnalyticsDashboardResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => { setLoading(true); setError(null); try { setItems(await analyticsApi.listDashboards()); } catch (e: unknown) { const err = e as { status?: number; message?: string }; setError(err?.status === 401 || err?.status === 403 ? "غير مصرح" : err?.message || "خطأ"); } finally { setLoading(false); } }, []);
  useEffect(() => { load(); }, [load]);
  if (loading) return <AuthLoadingState />;
  if (error) return <div style={{ padding: 16, color: "#dc2626", direction: "rtl" }}><p>{error}</p><button onClick={load} style={{ padding: "8px 16px", borderRadius: 6, border: "1px solid #e5e7eb", cursor: "pointer" }}>إعادة</button></div>;
  return <div style={{ direction: "rtl", padding: 16 }}><h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>لوحات التحكم</h2>{items.length === 0 ? <div style={{ padding: 24, textAlign: "center", color: "#6b7280" }}>لا توجد لوحات تحكم.</div> : <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr style={{ borderBottom: "2px solid #e5e7eb", textAlign: "right" }}><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>الرمز</th><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>الاسم</th><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>النوع</th><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>الحالة</th></tr></thead><tbody>{items.map(d => <tr key={d.id} style={{ borderBottom: "1px solid #f3f4f6" }}><td style={{ padding: 12, fontSize: 14 }}>{d.code}</td><td style={{ padding: 12, fontSize: 14 }}>{d.name}</td><td style={{ padding: 12, fontSize: 14 }}>{d.dashboardType}</td><td style={{ padding: 12 }}><StatusBadge status={d.status} /></td></tr>)}</tbody></table>}</div>;
}

function ReportsTab() {
  const [items, setItems] = useState<AnalyticsReportResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => { setLoading(true); setError(null); try { setItems(await analyticsApi.listReports()); } catch (e: unknown) { const err = e as { status?: number; message?: string }; setError(err?.status === 401 || err?.status === 403 ? "غير مصرح" : err?.message || "خطأ"); } finally { setLoading(false); } }, []);
  useEffect(() => { load(); }, [load]);
  if (loading) return <AuthLoadingState />;
  if (error) return <div style={{ padding: 16, color: "#dc2626", direction: "rtl" }}><p>{error}</p><button onClick={load} style={{ padding: "8px 16px", borderRadius: 6, border: "1px solid #e5e7eb", cursor: "pointer" }}>إعادة</button></div>;
  return <div style={{ direction: "rtl", padding: 16 }}><h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>التقارير</h2>{items.length === 0 ? <div style={{ padding: 24, textAlign: "center", color: "#6b7280" }}>لا توجد تقارير.</div> : <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr style={{ borderBottom: "2px solid #e5e7eb", textAlign: "right" }}><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>الرمز</th><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>الاسم</th><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>النوع</th><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>الحالة</th></tr></thead><tbody>{items.map(r => <tr key={r.id} style={{ borderBottom: "1px solid #f3f4f6" }}><td style={{ padding: 12, fontSize: 14 }}>{r.code}</td><td style={{ padding: 12, fontSize: 14 }}>{r.name}</td><td style={{ padding: 12, fontSize: 14 }}>{r.reportType}</td><td style={{ padding: 12 }}><StatusBadge status={r.status} /></td></tr>)}</tbody></table>}</div>;
}

function SourcesTab() {
  const [items, setItems] = useState<AnalyticsDataSourceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => { setLoading(true); setError(null); try { setItems(await analyticsApi.listDataSources()); } catch (e: unknown) { const err = e as { status?: number; message?: string }; setError(err?.status === 401 || err?.status === 403 ? "غير مصرح" : err?.message || "خطأ"); } finally { setLoading(false); } }, []);
  useEffect(() => { load(); }, [load]);
  if (loading) return <AuthLoadingState />;
  if (error) return <div style={{ padding: 16, color: "#dc2626", direction: "rtl" }}><p>{error}</p><button onClick={load} style={{ padding: "8px 16px", borderRadius: 6, border: "1px solid #e5e7eb", cursor: "pointer" }}>إعادة</button></div>;
  return <div style={{ direction: "rtl", padding: 16 }}><h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>مصادر البيانات</h2>{items.length === 0 ? <div style={{ padding: 24, textAlign: "center", color: "#6b7280" }}>لا توجد مصادر بيانات.</div> : <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr style={{ borderBottom: "2px solid #e5e7eb", textAlign: "right" }}><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>الرمز</th><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>الاسم</th><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>المصدر</th><th style={{ padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>الحالة</th></tr></thead><tbody>{items.map(s => <tr key={s.id} style={{ borderBottom: "1px solid #f3f4f6" }}><td style={{ padding: 12, fontSize: 14 }}>{s.code}</td><td style={{ padding: 12, fontSize: 14 }}>{s.name}</td><td style={{ padding: 12, fontSize: 14 }}>{s.sourceType}</td><td style={{ padding: 12 }}><StatusBadge status={s.status} /></td></tr>)}</tbody></table>}</div>;
}

export default function AnalyticsPage() {
  const router = useRouter();
  const { state, user } = useAuth();
  const [tab, setTab] = useState<Tab>("dashboards");
  useEffect(() => { if (state !== "INITIALIZING" && state !== "CHECKING_SESSION" && !user) router.push("/identity/login?from=/analytics"); }, [state, user, router]);
  const isLoading = state === "INITIALIZING" || state === "CHECKING_SESSION" || state === "AUTHENTICATING";
  if (isLoading || !user) return <AuthLoadingState />;
  const tabs: { id: Tab; labelAr: string }[] = [{ id: "dashboards", labelAr: "اللوحات" }, { id: "reports", labelAr: "التقارير" }, { id: "sources", labelAr: "المصادر" }];
  return <ExecutiveShell><div style={{ padding: "24px 16px", maxWidth: 1280, margin: "0 auto", direction: "rtl", fontFamily: "system-ui, sans-serif" }}><header style={{ marginBottom: 24 }}><h1 style={{ fontSize: 28, fontWeight: 700, color: "#111827", marginBottom: 8 }}>منصة التحليلات</h1><p style={{ color: "#6b7280", fontSize: 14 }}>إدارة لوحات التحكم والتقارير ومصادر البيانات</p></header><nav style={{ display: "flex", gap: 4, borderBottom: "1px solid #e5e7eb", marginBottom: 16, overflowX: "auto" }}>{tabs.map(t => <button key={t.id} onClick={() => setTab(t.id)} style={{ padding: "12px 20px", cursor: "pointer", fontSize: 14, fontWeight: tab === t.id ? 600 : 400, color: tab === t.id ? "#2563eb" : "#6b7280", borderBottom: tab === t.id ? "2px solid #2563eb" : "2px solid transparent", backgroundColor: "transparent", border: "none", borderLeft: "1px solid #e5e7eb", borderRight: "1px solid #e5e7eb", borderTop: "none", whiteSpace: "nowrap" }}>{t.labelAr}</button>)}</nav>{tab === "dashboards" && <DashboardsTab />}{tab === "reports" && <ReportsTab />}{tab === "sources" && <SourcesTab />}</div></ExecutiveShell>;
}
