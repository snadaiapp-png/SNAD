"use client";
/**
 * Zid integration admin console (tenant-scoped).
 *
 * UX guards only — the backend is authoritative: every route is gated
 * by ZID_INTEGRATION_VIEW / ZID_INTEGRATION_MANAGE capabilities, and
 * this page hides actions the user lacks permission for. No token or
 * secret material is ever displayed (the API never returns any).
 *
 * RTL/Arabic first (project default), English labels in parentheses.
 */
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { hasCapability } from "@/lib/auth/capabilities";
import { ExecutiveShell } from "@/components/shell";
import {
  zidApi,
  type ZidConnectionStatus,
  type ZidSyncSummary,
  type ZidAuditRow,
  type ZidWebhookEventRow,
  type ZidConfiguration,
} from "@/lib/api/zid-api";

export const ZID_CAPABILITIES = {
  VIEW: "ZID_INTEGRATION_VIEW",
  MANAGE: "ZID_INTEGRATION_MANAGE",
} as const;

const STATUS_LABELS: Record<string, { ar: string; color: string }> = {
  NOT_LINKED: { ar: "غير مرتبط", color: "var(--snad-color-text-secondary)" },
  CONNECTED: { ar: "متصل", color: "var(--snad-color-success)" },
  REAUTH_REQUIRED: { ar: "يحتاج إعادة تفويض", color: "var(--snad-color-warning)" },
  PAUSED: { ar: "متوقف", color: "var(--snad-color-warning)" },
  FAILED: { ar: "فشل", color: "var(--snad-color-error)" },
};

const LINK_RESULT_MESSAGES: Record<string, string> = {
  success: "تم ربط متجر زد بنجاح",
  invalid_state: "فشل الربط: حالة غير صالحة أو منتهية",
  store_already_bound: "فشل الربط: المتجر مرتبط بمستأجر آخر",
  authorization_failed: "فشل الربط: تعذر تبادل رمز التفويض",
  identity_unresolved: "فشل الربط: تعذر التحقق من هوية المتجر",
  merchant_denied: "ألغى التاجر عملية التفويض",
  invalid_request: "طلب ربط غير مكتمل",
  failed: "فشل الربط لسبب غير متوقع",
};

function formatTime(value: string | null | undefined): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString("ar-SA");
  } catch {
    return value;
  }
}

export default function ZidIntegrationPage() {
  const { state, me } = useAuth();
  const router = useRouter();

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // OAuth callback outcome is derived once from the URL (SSR-safe lazy
  // initializer) instead of via an effect-setState round-trip.
  const [notice, setNotice] = useState<string | null>(() => {
    if (typeof window === "undefined") return null;
    const link = new URLSearchParams(window.location.search).get("link");
    return link ? (LINK_RESULT_MESSAGES[link] ?? "اكتملت عملية الربط") : null;
  });
  const [config, setConfig] = useState<ZidConfiguration | null>(null);
  const [connection, setConnection] = useState<ZidConnectionStatus | null>(null);
  const [summary, setSummary] = useState<ZidSyncSummary | null>(null);
  const [audit, setAudit] = useState<ZidAuditRow[]>([]);
  const [webhookEvents, setWebhookEvents] = useState<ZidWebhookEventRow[]>([]);

  const canView = hasCapability(me, ZID_CAPABILITIES.VIEW);
  const canManage = hasCapability(me, ZID_CAPABILITIES.MANAGE);

  /** Fetches data and settles spinner/error state after awaits only. */
  const refresh = useCallback(async () => {
    try {
      const [cfg, conn, sum, auditRows, events] = await Promise.all([
        zidApi.configuration(),
        zidApi.connection(),
        zidApi.summary(),
        zidApi.audit().catch(() => []),
        zidApi.webhookEvents().catch(() => []),
      ]);
      setConfig(cfg);
      setConnection(conn);
      setSummary(sum);
      setAudit(auditRows || []);
      setWebhookEvents(events || []);
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "فشل تحميل حالة تكامل زد");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    // Server-data fetch on auth: every setState inside refresh() runs
    // after `await` boundaries (nothing synchronous), so the cascade
    // concern this rule guards against does not apply. Targeted
    // disable follows the established repo precedent in
    // lib/hooks/useTheme.ts and lib/i18n/I18nProvider.tsx.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refresh();
  }, [state, refresh]);

  // Clear the ?link= query param once the banner is shown (external
  // system update — no setState in the effect body).
  useEffect(() => {
    if (notice && typeof window !== "undefined" && window.location.search) {
      window.history.replaceState({}, "", window.location.pathname);
    }
  }, [notice]);

  const retry = useCallback(() => {
    setLoading(true);
    void refresh();
  }, [refresh]);

  const runAction = useCallback(async (name: string, action: () => Promise<unknown>) => {
    setBusy(name);
    setError(null);
    setNotice(null);
    try {
      await action();
      await refresh();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "تعذر تنفيذ العملية");
    } finally {
      setBusy(null);
    }
  }, [refresh]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }
  if (state !== "AUTHENTICATED") {
    router.replace("/?returnUrl=%2Fintegrations%2Fzid");
    return <AuthLoadingState phase="workspace" />;
  }
  // Deny-by-default: without the view capability the console never renders.
  if (!canView) {
    return (
      <ExecutiveShell>
        <div style={{ padding: 48, textAlign: "center", color: "var(--snad-color-text-muted)" }}>
          غير مصرح لك بعرض تكامل زد (Not authorized to view the Zid integration)
        </div>
      </ExecutiveShell>
    );
  }

  const status = STATUS_LABELS[connection?.status ?? "NOT_LINKED"] ?? STATUS_LABELS.NOT_LINKED;
  const linked = Boolean(connection && connection.status !== "NOT_LINKED");

  return (
    <ExecutiveShell>
      <div style={{ padding: 24, maxWidth: 1200, margin: "0 auto" }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 8 }}>تكامل زد (Zid Integration)</h1>
        <p style={{ fontSize: 13, color: "var(--snad-color-text-secondary)", marginBottom: 20 }}>
          ربط متجرك على منصة زد ومزامنة المنتجات والمخزون والطلبات — وفق الواجهات الرسمية الموثقة لزد.
        </p>

        {notice && (
          <div role="status" style={{ padding: 12, borderRadius: 8, marginBottom: 16, backgroundColor: "var(--snad-color-success-soft)", color: "var(--snad-color-success)" }}>
            {notice}
          </div>
        )}
        {error && (
          <div role="alert" style={{ padding: 12, borderRadius: 8, marginBottom: 16, backgroundColor: "var(--snad-color-error-soft)", color: "var(--snad-color-error)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span>{error}</span>
            <button onClick={retry} style={{ cursor: "pointer" }}>إعادة المحاولة</button>
          </div>
        )}

        {config && (!config.enabled || !config.clientConfigured) && (
          <div style={{ padding: 12, borderRadius: 8, marginBottom: 16, backgroundColor: "var(--snad-color-warning-soft)", color: "var(--snad-color-warning)", fontSize: 13 }}>
            التكامل غير مفعّل بعد على هذه البيئة — يحتاج المسؤول إلى توفير بيانات تطبيق زد عبر مخزن الأسرار (SANAD_ZID_*).
          </div>
        )}

        {/* Connection card */}
        <div style={{ padding: 16, borderRadius: 8, border: "1px solid var(--snad-color-border-default)", backgroundColor: "var(--snad-color-surface-primary)", marginBottom: 20 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
            <div style={{ fontSize: 16, fontWeight: 600 }}>حالة الاتصال (Connection)</div>
            <span style={{ padding: "4px 12px", borderRadius: 12, fontSize: 12, fontWeight: 500, backgroundColor: status.color + "20", color: status.color }}>
              {status.ar}
            </span>
          </div>
          {linked ? (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 8, fontSize: 13 }}>
              <div>المتجر: <strong>{connection?.zidStoreName ?? "—"}</strong></div>
              <div>معرف المتجر في زد: <code>{connection?.zidStoreId ?? "—"}</code></div>
              <div>آخر مزامنة ناجحة: {formatTime(connection?.lastSyncAt ?? summary?.lastSyncAt)}</div>
              <div>انتهاء صلاحية التفويض: {formatTime(connection?.authorizationExpiresAt)}</div>
            </div>
          ) : (
            <div style={{ fontSize: 13, color: "var(--snad-color-text-muted)", marginBottom: 12 }}>
              لا يوجد متجر زد مرتبط بهذا المستأجر بعد.
            </div>
          )}
          {canManage && (
            <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
              {!linked ? (
                <button
                  disabled={busy !== null || !config?.enabled || !config?.clientConfigured}
                  onClick={() => runAction("link", async () => {
                    const start = await zidApi.startLink();
                    // Keep the one-time state for the redirect round-trip only —
                    // never rendered, never logged elsewhere.
                    try { sessionStorage.setItem("zid_link_state", start.state); } catch { /* ignore */ }
                    window.location.href = start.authorizationUrl;
                  })}
                  style={{ padding: "8px 16px", borderRadius: 6, border: "none", backgroundColor: "var(--snad-color-brand-accent)", color: "#fff", cursor: busy === "link" ? "wait" : "pointer" }}
                >
                  ربط متجر زد (Link store)
                </button>
              ) : (
                <button
                  disabled={busy !== null}
                  onClick={() => {
                    if (window.confirm("تأكيد فك ربط متجر زد؟ سيتم حذف بيانات التفويض نهائيًا.")) {
                      void runAction("unlink", () => zidApi.unlink());
                    }
                  }}
                  style={{ padding: "8px 16px", borderRadius: 6, border: "1px solid var(--snad-color-error)", backgroundColor: "transparent", color: "var(--snad-color-error)", cursor: "pointer" }}
                >
                  فك الربط (Unlink)
                </button>
              )}
            </div>
          )}
        </div>

        {/* Sync summary */}
        {summary && linked && (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 8, marginBottom: 20 }}>
            {[
              { label: "منتجات مترجَمة", value: summary.productCount, color: "var(--snad-color-info)" },
              { label: "منتجات منشورة", value: summary.publishedProductCount, color: "var(--snad-color-success)" },
              { label: "سجلات المخزون", value: summary.stockCount, color: "var(--snad-color-info)" },
              { label: "طلبات", value: summary.orderCount, color: "var(--snad-color-brand-accent)" },
              { label: "أخطاء/تعارضات", value: summary.failedWebhookEvents + summary.recentConflicts, color: summary.failedWebhookEvents + summary.recentConflicts > 0 ? "var(--snad-color-error)" : "var(--snad-color-text-secondary)" },
            ].map((card) => (
              <div key={card.label} style={{ padding: 12, borderRadius: 8, backgroundColor: "var(--snad-color-surface-secondary)", textAlign: "center" }}>
                <div style={{ fontSize: 22, fontWeight: 700, color: card.color }}>{card.value}</div>
                <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>{card.label}</div>
              </div>
            ))}
          </div>
        )}

        {/* Manual syncs */}
        {canManage && linked && (
          <div style={{ display: "flex", gap: 8, marginBottom: 20, flexWrap: "wrap" }}>
            {[
              { id: "sync-products", label: "مزامنة المنتجات", action: () => zidApi.syncProducts() },
              { id: "sync-inventory", label: "مزامنة المخزون", action: () => zidApi.syncInventory() },
              { id: "sync-orders", label: "مزامنة الطلبات", action: () => zidApi.syncOrders() },
              { id: "sync-customers", label: "مزامنة العملاء", action: () => zidApi.syncCustomers() },
              { id: "reconcile", label: "فحص التطابق", action: () => zidApi.reconcile() },
            ].map((btn) => (
              <button
                key={btn.id}
                disabled={busy !== null}
                onClick={() => runAction(btn.id, btn.action)}
                style={{ padding: "6px 14px", borderRadius: 6, border: "1px solid var(--snad-color-border-default)", backgroundColor: "var(--snad-color-surface-primary)", cursor: busy === btn.id ? "wait" : "pointer" }}
              >
                {busy === btn.id ? "جارٍ التنفيذ…" : btn.label}
              </button>
            ))}
          </div>
        )}

        {/* Webhook events */}
        {linked && webhookEvents.length > 0 && (
          <Section title="أحداث الويبخوك الأخيرة (Recent webhook events)">
            <table style={tableStyle}>
              <thead>
                <tr><th style={thStyle}>النوع</th><th style={thStyle}>الحالة</th><th style={thStyle}>المحاولات</th><th style={thStyle}>استُلم</th></tr>
              </thead>
              <tbody>
                {webhookEvents.slice(0, 10).map((row, i) => (
                  <tr key={i}>
                    <td style={tdStyle}>{row.event_type ?? "—"}</td>
                    <td style={tdStyle}>{row.status}</td>
                    <td style={tdStyle}>{row.attempts}</td>
                    <td style={tdStyle}>{formatTime(row.received_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Section>
        )}

        {/* Audit trail */}
        {audit.length > 0 && (
          <Section title="سجل التدقيق (Audit trail)">
            <table style={tableStyle}>
              <thead>
                <tr><th style={thStyle}>النوع</th><th style={thStyle}>الكيان</th><th style={thStyle}>الحالة</th><th style={thStyle}>معالَج/فاشل</th><th style={thStyle}>الوقت</th></tr>
              </thead>
              <tbody>
                {audit.slice(0, 10).map((row, i) => (
                  <tr key={i}>
                    <td style={tdStyle}>{row.sync_type}</td>
                    <td style={tdStyle}>{row.entity ?? "—"}</td>
                    <td style={{ ...tdStyle, color: row.status === "SUCCESS" ? "var(--snad-color-success)" : "var(--snad-color-error)" }}>{row.status}</td>
                    <td style={tdStyle}>{row.items_processed}/{row.items_failed}</td>
                    <td style={tdStyle}>{formatTime(row.started_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Section>
        )}

        {loading && <div style={{ padding: 24, textAlign: "center", color: "var(--snad-color-text-muted)" }}>جارٍ التحميل…</div>}
      </div>
    </ExecutiveShell>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 20 }}>
      <h2 style={{ fontSize: 15, fontWeight: 600, marginBottom: 8 }}>{title}</h2>
      <div style={{ overflowX: "auto", borderRadius: 8, border: "1px solid var(--snad-color-border-default)", backgroundColor: "var(--snad-color-surface-primary)" }}>
        {children}
      </div>
    </div>
  );
}

const tableStyle: React.CSSProperties = { width: "100%", borderCollapse: "collapse", fontSize: 13 };
const thStyle: React.CSSProperties = { textAlign: "start", padding: "8px 12px", borderBottom: "1px solid var(--snad-color-border-default)", color: "var(--snad-color-text-secondary)", fontWeight: 500 };
const tdStyle: React.CSSProperties = { padding: "8px 12px", borderBottom: "1px solid var(--snad-color-border-default)" };
