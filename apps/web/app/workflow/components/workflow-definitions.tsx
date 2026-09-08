"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import {
  workflowApi,
  type WorkflowDefinitionResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

export function WorkflowDefinitions() {
  const [definitions, setDefinitions] = useState<WorkflowDefinitionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [module, setModule] = useState("GENERAL");
  const [triggerType, setTriggerType] = useState("MANUAL");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDefinitions(await workflowApi.listDefinitions(50));
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل تعريفات سير العمل"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const runMutation = async (command: () => Promise<WorkflowDefinitionResponse>) => {
    setError(null);
    setConflict(null);
    try {
      await command();
      await load();
      return true;
    } catch (cause: unknown) {
      const status = (cause as { status?: number })?.status;
      if (status === 409) {
        setConflict("تم تحديث تعريف سير العمل بالتزامن. أُعيد تحميل النسخة الأحدث.");
        await load();
      } else if (status === 403) {
        setError(describeWorkflowError(cause, "لا تملك صلاحية تعديل تعريفات سير العمل"));
      } else {
        setError(describeWorkflowError(cause, "فشل تعديل تعريف سير العمل"));
      }
      return false;
    }
  };

  const createDefinition = async () => {
    const normalizedCode = code.trim();
    const normalizedName = name.trim();
    if (!normalizedCode || !normalizedName) {
      setError("الرمز والاسم مطلوبان");
      return;
    }
    const saved = await runMutation(() =>
      workflowApi.createDefinition({
        code: normalizedCode,
        name: normalizedName,
        module,
        triggerType,
      }),
    );
    if (saved) {
      setCode("");
      setName("");
      setShowCreate(false);
    }
  };

  if (loading) return <AuthLoadingState />;

  return (
    <div dir="rtl">
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", flexWrap: "wrap", marginBottom: 16 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 20 }}>تعريفات سير العمل</h2>
          <p style={{ margin: "4px 0 0", color: "var(--snad-color-text-secondary)" }}>
            البيانات والأوامر تأتي من خدمة Workflow؛ لا توجد بيانات تجريبية محلية.
          </p>
        </div>
        <button type="button" onClick={() => setShowCreate((value) => !value)}>
          {showCreate ? "إلغاء" : "+ تعريف جديد"}
        </button>
      </div>

      {conflict && <p role="alert" style={{ color: "var(--snad-color-warning)" }}>{conflict}</p>}
      {error && (
        <div role="alert" style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <p style={{ color: "var(--snad-color-error)", margin: 0 }}>{error}</p>
          <button type="button" onClick={() => void load()}>إعادة المحاولة</button>
        </div>
      )}

      {showCreate && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10, padding: 14, margin: "16px 0", border: "1px solid var(--snad-color-border-default)", borderRadius: 8 }}>
          <input aria-label="رمز التعريف" placeholder="الرمز" value={code} onChange={(event) => setCode(event.target.value)} />
          <input aria-label="اسم التعريف" placeholder="الاسم" value={name} onChange={(event) => setName(event.target.value)} />
          <select aria-label="الوحدة" value={module} onChange={(event) => setModule(event.target.value)}>
            <option value="GENERAL">عام</option>
            <option value="MANAGEMENT">الإدارة</option>
            <option value="CRM">إدارة العملاء</option>
            <option value="FINANCE">المالية</option>
            <option value="HR">الموارد البشرية</option>
          </select>
          <select aria-label="نوع المشغل" value={triggerType} onChange={(event) => setTriggerType(event.target.value)}>
            <option value="MANUAL">يدوي</option>
            <option value="EVENT">حدث</option>
            <option value="SCHEDULED">مجدول</option>
            <option value="API">API</option>
          </select>
          <button type="button" onClick={() => void createDefinition()}>حفظ</button>
        </div>
      )}

      {!error && definitions.length === 0 ? (
        <p>لا توجد تعريفات سير عمل.</p>
      ) : (
        <div style={{ overflowX: "auto", marginTop: 16 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ textAlign: "right" }}>
                <th style={cellStyle}>الرمز</th>
                <th style={cellStyle}>الاسم</th>
                <th style={cellStyle}>الوحدة</th>
                <th style={cellStyle}>الإصدار</th>
                <th style={cellStyle}>الحالة</th>
                <th style={cellStyle}>إجراء</th>
              </tr>
            </thead>
            <tbody>
              {definitions.map((definition) => (
                <tr key={definition.id}>
                  <td style={cellStyle}>{definition.code}</td>
                  <td style={cellStyle}>{definition.name}</td>
                  <td style={cellStyle}>{definition.module}</td>
                  <td style={cellStyle}>v{definition.version}.{definition.versionLock}</td>
                  <td style={cellStyle}>{definition.status}</td>
                  <td style={cellStyle}>
                    {definition.status === "DRAFT" ? (
                      <button
                        type="button"
                        onClick={() => void runMutation(() => workflowApi.activateDefinition(definition.id))}
                      >
                        تفعيل
                      </button>
                    ) : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

const cellStyle = {
  padding: "10px 12px",
  borderBottom: "1px solid var(--snad-color-border-default)",
};
