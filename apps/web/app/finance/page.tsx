"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import { financeApi, type FinanceAccountResponse, type FinanceInvoiceResponse, type FinancePaymentResponse } from "@/lib/api/finance-api";
import { FinanceExecutionProvider } from "./finance-execution-provider";

export default function FinancePage() {
  const { state } = useAuth();
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [accounts, setAccounts] = useState<FinanceAccountResponse[]>([]);
  const [invoices, setInvoices] = useState<FinanceInvoiceResponse[]>([]);
  const [payments, setPayments] = useState<FinancePaymentResponse[]>([]);
  const [executionProgress, setExecutionProgress] = useState<number>(0);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [accts, invs, pays] = await Promise.all([
          financeApi.listAccounts(),
          financeApi.listInvoices(),
          financeApi.listPayments(),
        ]);
        if (!cancelled) {
          setAccounts(accts || []);
          setInvoices(invs || []);
          setPayments(pays || []);
          const provider = new FinanceExecutionProvider();
          const programs = await provider.getPrograms();
          if (programs.length > 0 && !cancelled) {
            const progress = await provider.getProgramProgress(programs[0].id);
            if (!cancelled) setExecutionProgress(progress.percentage);
          }
        }
      } catch (e: unknown) {
        if (!cancelled) setError(e instanceof Error ? e.message : "فشل تحميل البيانات المالية");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [state]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;
  if (state !== "AUTHENTICATED") {
    router.replace("/?returnUrl=%2Ffinance");
    return <AuthLoadingState phase="workspace" />;
  }
  if (loading) return <AuthLoadingState />;
  if (error)
    return (
      <ExecutiveShell>
        <div style={{ padding: "2rem", textAlign: "center" }}>
          <h1 style={{ fontSize: "1.5rem", fontWeight: 600 }}>الإدارة المالية</h1>
          <p style={{ color: "var(--snad-danger)", marginTop: "1rem" }}>{error}</p>
          <button
            onClick={() => window.location.reload()}
            style={{
              marginTop: "1rem", padding: "0.5rem 1.5rem", borderRadius: "0.375rem",
              border: "1px solid var(--snad-border)",
              background: "var(--snad-surface)", cursor: "pointer",
            }}
          >
            إعادة المحاولة
          </button>
        </div>
      </ExecutiveShell>
    );

  const activeAccounts = accounts.filter((a) => a.status === "ACTIVE");
  const pendingInvoices = invoices.filter((i) => i.status === "DRAFT" || i.status === "ISSUED");
  const completedPayments = payments.filter((p) => p.status === "COMPLETED");

  return (
    <ExecutiveShell>
      <div style={{ padding: "1.5rem", maxWidth: "1200px", margin: "0 auto" }}>
        {/* Header */}
        <header style={{ marginBottom: "2rem" }}>
          <h1 style={{ fontSize: "1.75rem", fontWeight: 700, margin: 0 }}>
            الإدارة المالية
          </h1>
          <p style={{ color: "var(--snad-text-muted)", marginTop: "0.5rem" }}>
            إدارة الحسابات والفواتير والمدفوعات والتقارير المالية
          </p>
        </header>

        {/* Stats Grid */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "1rem", marginBottom: "2rem" }}>
          <StatCard label="الحسابات النشطة" value={activeAccounts.length} color="var(--snad-success)" />
          <StatCard label="الفواتير المعلقة" value={pendingInvoices.length} color="var(--snad-warning)" />
          <StatCard label="المدفوعات المكتملة" value={completedPayments.length} color="var(--snad-primary)" />
          <StatCard label="نسبة التنفيذ" value={`${executionProgress}%`} color="var(--snad-accent)" />
        </div>

        {/* Accounts Section */}
        <section style={{ marginBottom: "2rem" }}>
          <h2 style={{ fontSize: "1.25rem", fontWeight: 600, marginBottom: "1rem" }}>
            دليل الحسابات
          </h2>
          {accounts.length === 0 ? (
            <p style={{ color: "var(--snad-text-muted)" }}>لا توجد حسابات بعد</p>
          ) : (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
              <thead>
                <tr style={{ borderBottom: "2px solid var(--snad-border)", textAlign: "right" }}>
                  <th style={{ padding: "0.5rem 0.75rem" }}>الرمز</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>الاسم</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>النوع</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>الحالة</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>العملة</th>
                </tr>
              </thead>
              <tbody>
                {accounts.slice(0, 10).map((a) => (
                  <tr key={a.id} style={{ borderBottom: "1px solid var(--snad-border)" }}>
                    <td style={{ padding: "0.5rem 0.75rem", fontFamily: "monospace" }}>{a.code}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{a.name}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{a.accountType}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>
                      <span style={{
                        padding: "2px 8px", borderRadius: "4px", fontSize: "0.75rem",
                        background: a.status === "ACTIVE" ? "color-mix(in srgb, var(--snad-success) 10%, transparent)" : "color-mix(in srgb, var(--snad-text-muted) 10%, transparent)",
                        color: a.status === "ACTIVE" ? "var(--snad-success)" : "var(--snad-text-muted)",
                      }}>{a.status}</span>
                    </td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{a.currency}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        {/* Invoices Section */}
        <section style={{ marginBottom: "2rem" }}>
          <h2 style={{ fontSize: "1.25rem", fontWeight: 600, marginBottom: "1rem" }}>
            الفواتير
          </h2>
          {invoices.length === 0 ? (
            <p style={{ color: "var(--snad-text-muted)" }}>لا توجد فواتير بعد</p>
          ) : (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
              <thead>
                <tr style={{ borderBottom: "2px solid var(--snad-border)", textAlign: "right" }}>
                  <th style={{ padding: "0.5rem 0.75rem" }}>رقم الفاتورة</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>العميل</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>الحالة</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>المبلغ</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>تاريخ الإصدار</th>
                </tr>
              </thead>
              <tbody>
                {invoices.slice(0, 10).map((i) => (
                  <tr key={i.id} style={{ borderBottom: "1px solid var(--snad-border)" }}>
                    <td style={{ padding: "0.5rem 0.75rem", fontFamily: "monospace" }}>{i.invoiceNumber}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{i.customerName}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{i.status}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{String(i.totalAmount)} {i.currency}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{i.issueDate}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        {/* Payments Section */}
        <section style={{ marginBottom: "2rem" }}>
          <h2 style={{ fontSize: "1.25rem", fontWeight: 600, marginBottom: "1rem" }}>
            المدفوعات
          </h2>
          {payments.length === 0 ? (
            <p style={{ color: "var(--snad-text-muted)" }}>لا توجد مدفوعات بعد</p>
          ) : (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
              <thead>
                <tr style={{ borderBottom: "2px solid var(--snad-border)", textAlign: "right" }}>
                  <th style={{ padding: "0.5rem 0.75rem" }}>رقم الدفع</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>طريقة الدفع</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>الحالة</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>المبلغ</th>
                  <th style={{ padding: "0.5rem 0.75rem" }}>التاريخ</th>
                </tr>
              </thead>
              <tbody>
                {payments.slice(0, 10).map((p) => (
                  <tr key={p.id} style={{ borderBottom: "1px solid var(--snad-border)" }}>
                    <td style={{ padding: "0.5rem 0.75rem", fontFamily: "monospace" }}>{p.paymentNumber}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{p.paymentMethod}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{p.status}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{String(p.amount)} {p.currency}</td>
                    <td style={{ padding: "0.5rem 0.75rem" }}>{p.paymentDate}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </ExecutiveShell>
  );
}

function StatCard({ label, value, color }: { label: string; value: string | number; color: string }) {
  return (
    <div style={{
      padding: "1rem 1.25rem",
      borderRadius: "0.5rem",
      border: "1px solid var(--snad-border)",
      background: "var(--snad-surface)",
    }}>
      <div style={{ fontSize: "1.75rem", fontWeight: 700, color }}>{value}</div>
      <div style={{ fontSize: "0.75rem", color: "var(--snad-text-muted)", marginTop: "0.25rem" }}>{label}</div>
    </div>
  );
}
