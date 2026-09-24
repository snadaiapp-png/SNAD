"use client";

import { useCallback, useEffect, useState } from "react";
import {
  executiveApi,
  type ModuleResponse,
  type PlanModuleEntitlementResponse,
  type TenantEntitlementResponse,
  type SaasPlan,
} from "@/lib/api/executive-api";
import styles from "./executive.module.css";

interface Props {
  plans: SaasPlan[];
}

type SubTab = "modules" | "plan-entitlements" | "tenant-entitlements";

export function ModulesEntitlementsPanel({ plans }: Props) {
  const [subTab, setSubTab] = useState<SubTab>("modules");
  const [modules, setModules] = useState<ModuleResponse[]>([]);
  const [planEntitlements, setPlanEntitlements] = useState<PlanModuleEntitlementResponse[]>([]);
  const [tenantEntitlements, setTenantEntitlements] = useState<TenantEntitlementResponse[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<string>("");
  const [selectedTenantId, setSelectedTenantId] = useState<string>("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refreshModules = useCallback(async () => {
    setBusy(true);
    setError("");
    try {
      const list = await executiveApi.modules();
      setModules(list);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }, []);

  const refreshPlanEntitlements = useCallback(async (planId: string) => {
    if (!planId) return;
    setBusy(true);
    setError("");
    try {
      const list = await executiveApi.planModules(planId);
      setPlanEntitlements(list);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }, []);

  const refreshTenantEntitlements = useCallback(async (tenantId: string) => {
    if (!tenantId) return;
    setBusy(true);
    setError("");
    try {
      const list = await executiveApi.tenantEntitlements(tenantId);
      setTenantEntitlements(list);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    if (subTab === "modules" && modules.length === 0) {
      void refreshModules();
    }
    if (subTab === "plan-entitlements" && selectedPlanId && planEntitlements.length === 0) {
      void refreshPlanEntitlements(selectedPlanId);
    }
  }, [subTab, modules.length, planEntitlements.length, selectedPlanId, refreshModules, refreshPlanEntitlements]);

  const handleRecalculate = async () => {
    if (!selectedTenantId) {
      setNotice("أدخل معرّف المستأجر");
      return;
    }
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await executiveApi.recalculateEntitlements(selectedTenantId);
      setNotice("تم إعادة حساب الصلاحيات بنجاح");
      void refreshTenantEntitlements(selectedTenantId);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={styles.panel}>
      <div className={styles.subTabs}>
        <button
          type="button"
          className={subTab === "modules" ? styles.active : ""}
          onClick={() => setSubTab("modules")}
        >
          الموديولات
        </button>
        <button
          type="button"
          className={subTab === "plan-entitlements" ? styles.active : ""}
          onClick={() => setSubTab("plan-entitlements")}
        >
          صلاحيات الباقات
        </button>
        <button
          type="button"
          className={subTab === "tenant-entitlements" ? styles.active : ""}
          onClick={() => setSubTab("tenant-entitlements")}
        >
          صلاحيات المستأجرين
        </button>
      </div>

      {error && <div className={styles.error}>{error}</div>}
      {notice && <div className={styles.notice}>{notice}</div>}
      {busy && <div className={styles.busy}>جارٍ التحميل...</div>}

      {subTab === "modules" && (
        <div className={styles.tableWrap}>
          <h3>الموديولات المسجلة ({modules.length})</h3>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>الكود</th>
                <th>الاسم</th>
                <th>الوصف</th>
                <th>الحالة</th>
                <th>الترتيب</th>
                <th>الإصدار</th>
                <th>مفعّل</th>
              </tr>
            </thead>
            <tbody>
              {modules.map((m) => (
                <tr key={m.id}>
                  <td><code>{m.code}</code></td>
                  <td>{m.name}</td>
                  <td>{m.description ?? "—"}</td>
                  <td><span className={styles.status} data-status={m.status}>{m.status}</span></td>
                  <td>{m.displayOrder}</td>
                  <td>{m.version ?? "—"}</td>
                  <td>{m.enabled ? "✅" : "❌"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {subTab === "plan-entitlements" && (
        <div className={styles.tableWrap}>
          <h3>صلاحيات الباقة</h3>
          <div className={styles.row}>
            <select
              value={selectedPlanId}
              onChange={(e) => {
                setSelectedPlanId(e.target.value);
                if (e.target.value) void refreshPlanEntitlements(e.target.value);
              }}
            >
              <option value="">— اختر باقة —</option>
              {plans.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.code})
                </option>
              ))}
            </select>
          </div>
          {selectedPlanId && planEntitlements.length > 0 && (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>الموديول</th>
                  <th>الصلاحية</th>
                  <th>القيمة</th>
                  <th>الحد</th>
                  <th>الحصة</th>
                  <th>الفترة</th>
                  <th>مفعّل</th>
                </tr>
              </thead>
              <tbody>
                {planEntitlements.map((e) => (
                  <tr key={e.id}>
                    <td><code>{e.moduleCode}</code></td>
                    <td>{e.capabilityCode ?? "—"}</td>
                    <td>{e.capabilityValue ?? "—"}</td>
                    <td>{e.limitValue ?? "—"}</td>
                    <td>{e.quotaValue ?? "—"}</td>
                    <td>{e.quotaPeriod ?? "—"}</td>
                    <td>{e.moduleEnabled ? "✅" : "❌"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {subTab === "tenant-entitlements" && (
        <div className={styles.tableWrap}>
          <h3>الصلاحيات الفعّالة للمستأجر</h3>
          <div className={styles.row}>
            <input
              type="text"
              placeholder="معرّف المستأجر (UUID)"
              value={selectedTenantId}
              onChange={(e) => setSelectedTenantId(e.target.value)}
              className={styles.input}
            />
            <button
              type="button"
              onClick={() => selectedTenantId && void refreshTenantEntitlements(selectedTenantId)}
              disabled={!selectedTenantId || busy}
            >
              عرض الصلاحيات
            </button>
            <button
              type="button"
              onClick={handleRecalculate}
              disabled={!selectedTenantId || busy}
            >
              إعادة الحساب
            </button>
          </div>
          {tenantEntitlements.length > 0 && (
            <div className={styles.entitlementsList}>
              {tenantEntitlements.map((te) => (
                <div key={`${te.tenantId}-${te.moduleCode}`} className={styles.entitlementCard}>
                  <div className={styles.entitlementHeader}>
                    <h4>
                      <code>{te.moduleCode}</code>
                      {te.moduleEnabled ? (
                        <span className={styles.status} data-status="ACTIVE">مفعّل</span>
                      ) : (
                        <span className={styles.status} data-status="INACTIVE">معطّل</span>
                      )}
                    </h4>
                    <span className={styles.meta}>
                      الباقة: {te.planId ? te.planId.substring(0, 8) : "—"} ·
                      الاشتراك: {te.subscriptionId ? te.subscriptionId.substring(0, 8) : "—"}
                    </span>
                  </div>
                  {Object.keys(te.capabilities).length > 0 && (
                    <div className={styles.entitlementSection}>
                      <strong>الميزات:</strong>
                      <ul>
                        {Object.entries(te.capabilities).map(([code, enabled]) => (
                          <li key={code}>
                            <code>{code}</code>: {enabled ? "✅" : "❌"}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {Object.keys(te.limits).length > 0 && (
                    <div className={styles.entitlementSection}>
                      <strong>الحدود:</strong>
                      <ul>
                        {Object.entries(te.limits).map(([code, limit]) => (
                          <li key={code}>
                            <code>{code}</code>: {limit.toLocaleString("ar-SA")}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {Object.keys(te.quotas).length > 0 && (
                    <div className={styles.entitlementSection}>
                      <strong>الحصص:</strong>
                      <ul>
                        {Object.entries(te.quotas).map(([code, quota]) => (
                          <li key={code}>
                            <code>{code}</code>: {quota.value.toLocaleString("ar-SA")} ({quota.period})
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
