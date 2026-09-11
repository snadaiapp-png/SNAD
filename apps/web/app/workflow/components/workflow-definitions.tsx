"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import {
  workflowApi,
  type WorkflowDefinitionResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

/**
 * R0.G5 — Definitions workspace.
 *
 * Logical versions are grouped into families (definitionFamilyId). For each
 * family the workspace shows the workflow name/code, source module,
 * published and active draft versions, engine generation, publication
 * state, and last-updated time, with search and filters plus expandable
 * version history. System canaries (release infrastructure) are classified
 * and separated from business workflows (R0.G8) — never mutated.
 *
 * Business-version display rule (R0.G1): versions render as v{version}.
 * versionLock is an opaque optimistic-concurrency token and must never be
 * presented as a semantic version.
 *
 * NOTE: the create-form module list stays a small static catalog until R1
 * replaces module selection with authoritative dynamic discovery.
 */

type FamilyGroup = {
  familyId: string;
  displayName: string;
  displayCode: string;
  module: string;
  engine: string;
  classification: string;
  publishedVersion: number | null;
  draftVersion: number | null;
  latest: WorkflowDefinitionResponse;
  versions: WorkflowDefinitionResponse[];
};

function buildFamilies(definitions: WorkflowDefinitionResponse[]): FamilyGroup[] {
  const byFamily = new Map<string, WorkflowDefinitionResponse[]>();
  for (const definition of definitions) {
    const key = definition.definitionFamilyId || definition.id;
    const bucket = byFamily.get(key);
    if (bucket) bucket.push(definition);
    else byFamily.set(key, [definition]);
  }
  const groups: FamilyGroup[] = [];
  for (const [familyId, versions] of byFamily) {
    const sorted = [...versions].sort((a, b) => b.version - a.version);
    const latest = sorted[0];
    const published = sorted.find((v) => v.publicationState === "PUBLISHED");
    const draft = sorted.find((v) => v.publicationState === "DRAFT");
    groups.push({
      familyId,
      displayName: latest.name,
      displayCode: latest.code,
      module: latest.module,
      engine: latest.engineGeneration,
      classification:
        latest.classification ??
        (latest.code.startsWith("Y2-PROD-CANARY-") ? "SYSTEM_CANARY" : "BUSINESS"),
      publishedVersion: published ? published.version : null,
      draftVersion: draft ? draft.version : null,
      latest,
      versions: sorted,
    });
  }
  return groups.sort((a, b) => a.displayName.localeCompare(b.displayName, "ar"));
}

const MODULE_LABELS: Record<string, string> = {
  GENERAL: "عام",
  MANAGEMENT: "الإدارة",
  CRM: "إدارة العملاء",
  FINANCE: "المالية",
  HR: "الموارد البشرية",
};

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

  // Workspace filters (R0.G5).
  const [search, setSearch] = useState("");
  const [moduleFilter, setModuleFilter] = useState("");
  const [stateFilter, setStateFilter] = useState("");
  const [engineFilter, setEngineFilter] = useState("");
  const [classificationFilter, setClassificationFilter] = useState("");
  const [expandedFamily, setExpandedFamily] = useState<string | null>(null);
  const [familyHistory, setFamilyHistory] = useState<Record<string, WorkflowDefinitionResponse[]>>({});

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDefinitions(await workflowApi.listDefinitions(200));
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل تعريفات سير العمل"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const toggleHistory = async (family: FamilyGroup) => {
    if (expandedFamily === family.familyId) {
      setExpandedFamily(null);
      return;
    }
    setExpandedFamily(family.familyId);
    if (!familyHistory[family.familyId]) {
      try {
        const versions = await workflowApi.listDefinitionVersions(family.latest.id);
        setFamilyHistory((previous) => ({ ...previous, [family.familyId]: versions }));
      } catch (cause: unknown) {
        setFamilyHistory((previous) => ({
          ...previous,
          [family.familyId]: family.versions,
        }));
        console.error("version history load failed", cause);
      }
    }
  };

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

  const families = useMemo(() => buildFamilies(definitions), [definitions]);

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return families.filter((family) => {
      if (needle
        && !family.displayName.toLowerCase().includes(needle)
        && !family.displayCode.toLowerCase().includes(needle)) return false;
      if (moduleFilter && family.module !== moduleFilter) return false;
      if (stateFilter) {
        if (stateFilter === "PUBLISHED" && family.publishedVersion === null) return false;
        if (stateFilter === "DRAFT" && family.draftVersion === null) return false;
        if (stateFilter === "RETIRED"
          && !family.versions.some((v) => v.publicationState === "RETIRED")) return false;
      }
      if (engineFilter && family.engine !== engineFilter) return false;
      if (classificationFilter && family.classification !== classificationFilter) return false;
      return true;
    });
  }, [families, search, moduleFilter, stateFilter, engineFilter, classificationFilter]);

  const businessFamilies = filtered.filter((family) => family.classification !== "SYSTEM_CANARY");
  const canaryFamilies = filtered.filter((family) => family.classification === "SYSTEM_CANARY");

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

      {/* Search + filters (R0.G5) */}
      <div
        data-testid="definitions-filters"
        style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 10, margin: "12px 0" }}
      >
        <input
          aria-label="بحث"
          placeholder="بحث بالاسم أو الرمز"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <select aria-label="تصفية الوحدة" value={moduleFilter} onChange={(event) => setModuleFilter(event.target.value)}>
          <option value="">كل الوحدات</option>
          {Object.entries(MODULE_LABELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <select aria-label="تصفية حالة النشر" value={stateFilter} onChange={(event) => setStateFilter(event.target.value)}>
          <option value="">كل حالات النشر</option>
          <option value="PUBLISHED">منشور</option>
          <option value="DRAFT">مسودة</option>
          <option value="RETIRED">مُعتزل</option>
        </select>
        <select aria-label="تصفية المحرك" value={engineFilter} onChange={(event) => setEngineFilter(event.target.value)}>
          <option value="">كل المحركات</option>
          <option value="Y2">Y2</option>
          <option value="LEGACY">LEGACY</option>
        </select>
        <select aria-label="تصفية التصنيف" value={classificationFilter} onChange={(event) => setClassificationFilter(event.target.value)}>
          <option value="">الكل (أعمال/نظام)</option>
          <option value="BUSINESS">أعمال</option>
          <option value="SYSTEM_CANARY">كاناري نظامي</option>
        </select>
      </div>

      {!error && definitions.length === 0 ? (
        <p>لا توجد تعريفات سير عمل.</p>
      ) : (
        <WorkspaceTable
          families={businessFamilies}
          emptyLabel="لا توجد تعريفات سير عمل مطابقة."
          expandedFamily={expandedFamily}
          familyHistory={familyHistory}
          onToggleHistory={(family) => void toggleHistory(family)}
          onActivate={(definition) => void runMutation(() => workflowApi.activateDefinition(definition.id))}
        />
      )}

      {/* R0.G8 — system canaries are release infrastructure: classified,
          separated, never offered business actions. */}
      {canaryFamilies.length > 0 && (
        <section data-testid="system-canaries" style={{ marginTop: 28 }}>
          <h3 style={{ fontSize: 16 }}>
            بنية تحتية للإصدار (كاناري نظامي)
            <span
              data-testid="canary-badge"
              style={{
                marginInlineStart: 8,
                fontSize: 11,
                padding: "2px 8px",
                borderRadius: 999,
                background: "var(--snad-color-warning-soft)",
                border: "1px solid var(--snad-color-warning)",
              }}
            >
              SYSTEM_CANARY
            </span>
          </h3>
          <WorkspaceTable
            families={canaryFamilies}
            emptyLabel=""
            expandedFamily={expandedFamily}
            familyHistory={familyHistory}
            onToggleHistory={(family) => void toggleHistory(family)}
            onActivate={() => undefined}
          />
        </section>
      )}
    </div>
  );
}

function WorkspaceTable({
  families,
  emptyLabel,
  expandedFamily,
  familyHistory,
  onToggleHistory,
  onActivate,
}: {
  families: FamilyGroup[];
  emptyLabel: string;
  expandedFamily: string | null;
  familyHistory: Record<string, WorkflowDefinitionResponse[]>;
  onToggleHistory: (family: FamilyGroup) => void;
  onActivate: (definition: WorkflowDefinitionResponse) => void;
}) {
  if (families.length === 0) {
    return emptyLabel ? <p>{emptyLabel}</p> : null;
  }
  return (
    <div style={{ overflowX: "auto", marginTop: 8 }}>
      <table style={{ width: "100%", borderCollapse: "collapse" }} data-testid="definitions-table">
        <thead>
          <tr style={{ textAlign: "right" }}>
            <th style={cellStyle}>سير العمل</th>
            <th style={cellStyle}>الوحدة</th>
            <th style={cellStyle}>الإصدار المنشور</th>
            <th style={cellStyle}>مسودة نشطة</th>
            <th style={cellStyle}>المحرك</th>
            <th style={cellStyle}>حالة النشر</th>
            <th style={cellStyle}>آخر تحديث</th>
            <th style={cellStyle}>إجراء</th>
          </tr>
        </thead>
        <tbody>
          {families.map((family) => (
            <FamilyRow
              key={family.familyId}
              family={family}
              expanded={expandedFamily === family.familyId}
              history={familyHistory[family.familyId]}
              onToggleHistory={() => onToggleHistory(family)}
              onActivate={() => onActivate(family.latest)}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function FamilyRow({
  family,
  expanded,
  history,
  onToggleHistory,
  onActivate,
}: {
  family: FamilyGroup;
  expanded: boolean;
  history?: WorkflowDefinitionResponse[];
  onToggleHistory: () => void;
  onActivate: () => void;
}) {
  const canActivateLegacyDraft =
    family.latest.engineGeneration === "LEGACY"
    && family.latest.publicationState === "DRAFT"
    && family.latest.status === "DRAFT";
  return (
    <>
      <tr key={family.familyId}>
        <td style={cellStyle}>
          <strong>{family.displayName}</strong>
          <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>{family.displayCode}</div>
        </td>
        <td style={cellStyle}>{MODULE_LABELS[family.module] ?? family.module}</td>
        <td style={cellStyle}>{family.publishedVersion === null ? "—" : `v${family.publishedVersion}`}</td>
        <td style={cellStyle}>{family.draftVersion === null ? "—" : `v${family.draftVersion}`}</td>
        <td style={cellStyle}>{family.engine}</td>
        <td style={cellStyle}>{family.latest.publicationState}</td>
        <td style={cellStyle}>{formatUpdatedAt(family.latest.updatedAt)}</td>
        <td style={cellStyle}>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button type="button" onClick={onToggleHistory} data-testid={`history-${family.displayCode}`}>
              {expanded ? "إخفاء السجل" : "سجل الإصدارات"}
            </button>
            {canActivateLegacyDraft && (
              <button type="button" onClick={onActivate}>تفعيل</button>
            )}
            {family.latest.engineGeneration === "Y2" && (
              <a href={`/workflow/definitions/${family.latest.id}`}>فتح المصمم</a>
            )}
          </div>
        </td>
      </tr>
      {expanded && (
        <tr>
          <td style={cellStyle} colSpan={8}>
            {!history ? (
              <p style={{ margin: 0, color: "var(--snad-color-text-secondary)" }}>جارٍ تحميل السجل…</p>
            ) : (
              <ul style={{ margin: 0, paddingInlineStart: 18 }}>
                {[...history]
                  .sort((a, b) => a.version - b.version)
                  .map((version) => (
                    <li key={version.id}>
                      <code>{`v${version.version}`}</code> — {version.publicationState}
                      {version.status ? ` · ${version.status}` : ""}
                    </li>
                  ))}
              </ul>
            )}
          </td>
        </tr>
      )}
    </>
  );
}

function formatUpdatedAt(value: string | null | undefined): string {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "—";
  return parsed.toISOString().slice(0, 16).replace("T", " ");
}

const cellStyle = {
  padding: "10px 12px",
  borderBottom: "1px solid var(--snad-color-border-default)",
};
