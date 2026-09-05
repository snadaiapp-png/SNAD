"use client";

/**
 * Employee 360 — WS5 Task 9 (/hr/employees/[employmentId]).
 *
 * Ten tabs: Overview, Employment, Assignments, Organization, Contract,
 * Compensation, Private Information, Timeline, Compliance, Audit.
 *
 * Authorization: tabs are gated UX-only by the canonical HRM capabilities;
 * the backend remains authoritative (403/404 fail-closed handled by the
 * shared error state). Restricted reads are explicit:
 * - Private Information opens ONLY with HRM.PII.VIEW and triggers the
 *   audited private read for that single person;
 * - Compensation loads ONLY with HRM.COMPENSATION.VIEW, scoped to this
 *   employment;
 * - Audit loads ONLY with HRM.AUDIT.VIEW.
 *
 * Lifecycle commands send { effectiveDate, expectedVersion, reasonCode }
 * with a generated Idempotency-Key. Termination is labelled "إنهاء خدمة"
 * (end of service) — never "delete". Stale-version 409 conflicts surface
 * the canonical conflict message and never overwrite newer data.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent as ReactKeyboardEvent } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  hrmV2Api,
  newIdempotencyKey,
  type AssignmentResponse,
  type AuditEntryResponse,
  type CompensationPackageResponse,
  type ComplianceContextResponse,
  type ContractResponse,
  type EmploymentResponse,
  type PersonPrivateResponse,
  type PersonSummaryResponse,
} from "@/lib/api/hr-v2-api";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrEmptyState, HrErrorState, HrLoading, hrmErrorMessage } from "../../components/hr-feedback";
import { HrComplianceBadge } from "../../components/hr-compliance-badge";
import {
  ASSIGNMENT_STATUS_AR,
  CONTRACT_STATUS_AR,
  employmentStatusAr,
  formatArabicDate,
  workerClassificationAr,
} from "../../hr-labels";
import styles from "../../hr.module.css";

type TabKey =
  | "overview" | "employment" | "assignments" | "organization" | "contract"
  | "compensation" | "private" | "timeline" | "compliance" | "audit";

const LIFECYCLE_COMMANDS = {
  "submit-onboarding": { label: "إرسال التأهيل", confirmTitle: "إرسال الموظف إلى التأهيل" },
  "activate": { label: "تنشيط", confirmTitle: "تنشيط التوظيف" },
  "start-leave": { label: "بدء إجازة", confirmTitle: "بدء إجازة" },
  "return-from-leave": { label: "العودة من إجازة", confirmTitle: "العودة من الإجازة" },
  "suspend": { label: "إيقاف مؤقت", confirmTitle: "إيقاف الموظف مؤقتًا" },
  "reinstate": { label: "إعادة تنشيط", confirmTitle: "إعادة تنشيط التوظيف" },
  "terminate": { label: "إنهاء خدمة", confirmTitle: "إنهاء خدمة الموظف" },
  "void": { label: "إبطال السجل", confirmTitle: "إبطال سجل التوظيف" },
} as const;

type LifecycleCommand = keyof typeof LIFECYCLE_COMMANDS;

/** Commands offered per status — display hints only; the backend remains
 *  the authority on legal transitions (409 otherwise). */
const STATUS_COMMANDS: Record<string, LifecycleCommand[]> = {
  DRAFT: ["submit-onboarding", "void"],
  ONBOARDING: ["activate", "void"],
  ACTIVE: ["start-leave", "suspend", "terminate"],
  ON_LEAVE: ["return-from-leave", "terminate"],
  SUSPENDED: ["reinstate", "terminate"],
  TERMINATED: [],
  VOID: [],
};

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function Employee360Page() {
  const { state, me } = useAuth();
  const { employmentId } = useEmploymentId();

  const capabilities = me?.capabilities ?? [];
  const canUpdate = capabilities.includes(HRM_CAPABILITIES.EMPLOYEE_UPDATE);
  const canTerminate = capabilities.includes(HRM_CAPABILITIES.EMPLOYEE_TERMINATE);
  const canPii = capabilities.includes(HRM_CAPABILITIES.PII_VIEW);
  const canComp = capabilities.includes(HRM_CAPABILITIES.COMPENSATION_VIEW);
  const canAudit = capabilities.includes(HRM_CAPABILITIES.AUDIT_VIEW);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [employment, setEmployment] = useState<EmploymentResponse | null>(null);
  const [person, setPerson] = useState<PersonSummaryResponse | null>(null);
  const [assignments, setAssignments] = useState<AssignmentResponse[]>([]);
  const [contracts, setContracts] = useState<ContractResponse[]>([]);
  const [compliance, setCompliance] = useState<ComplianceContextResponse | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<TabKey>("overview");
  const [privateData, setPrivateData] = useState<PersonPrivateResponse | null>(null);
  const [privateState, setPrivateState] = useState<"idle" | "error">("idle");
  const [privateError, setPrivateError] = useState<unknown>(null);
  const [compensation, setCompensation] = useState<CompensationPackageResponse[] | null>(null);
  const [compState, setCompState] = useState<"idle" | "error">("idle");
  const [compError, setCompError] = useState<unknown>(null);
  const [audit, setAudit] = useState<AuditEntryResponse[] | null>(null);
  const [auditState, setAuditState] = useState<"idle" | "error">("idle");
  const [auditError, setAuditError] = useState<unknown>(null);

  // Lifecycle confirmation dialog state.
  const [pendingCommand, setPendingCommand] = useState<LifecycleCommand | null>(null);
  const [effectiveDate, setEffectiveDate] = useState(todayIso());
  const [reasonCode, setReasonCode] = useState("");
  const [dialogBusy, setDialogBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const loadCore = useCallback(async () => {
    if (!employmentId) return;
    try {
      const emp = await hrmV2Api.getEmployment(employmentId);
      const [pers, asgs, ctts, ctx] = await Promise.all([
        hrmV2Api.getPerson(emp.personId),
        hrmV2Api.listAssignments(),
        hrmV2Api.listContracts(),
        hrmV2Api.getComplianceContext(employmentId),
      ]);
      setEmployment(emp);
      setPerson(pers);
      setAssignments(asgs.filter((a) => a.employmentId === employmentId));
      setContracts(ctts.filter((c) => c.employmentId === employmentId));
      setCompliance(ctx);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [employmentId]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    // Deferred to the next macrotask (codebase pattern) so the effect body
    // performs no synchronous setState.
    const timer = window.setTimeout(() => void loadCore(), 0);
    return () => window.clearTimeout(timer);
  }, [state, loadCore]);

  // Restricted tab data loads lazily and only for capable users. The fetch
  // guard is a ref (not state) so the effect body performs no synchronous
  // setState — state updates happen in the async continuations only.
  const privateRequested = useRef(false);
  useEffect(() => {
    if (activeTab !== "private" || !canPii || !person || privateRequested.current) return;
    privateRequested.current = true;
    hrmV2Api.getPersonPrivate(person.personId)
      .then(setPrivateData)
      .catch((err) => { setPrivateError(err); setPrivateState("error"); });
  }, [activeTab, canPii, person]);

  const compRequested = useRef(false);
  useEffect(() => {
    if (activeTab !== "compensation" || !canComp || !employmentId || compRequested.current) return;
    compRequested.current = true;
    hrmV2Api.listCompensationPackages(employmentId)
      .then(setCompensation)
      .catch((err) => { setCompError(err); setCompState("error"); });
  }, [activeTab, canComp, employmentId]);

  const auditRequested = useRef(false);
  useEffect(() => {
    if (activeTab !== "audit" || !canAudit || !employmentId || auditRequested.current) return;
    auditRequested.current = true;
    hrmV2Api.listAudit({ resourceType: "EMPLOYMENT", limit: 50 })
      .then((rows) => setAudit(rows.filter((r) => r.resourceId === employmentId)))
      .catch((err) => { setAuditError(err); setAuditState("error"); });
  }, [activeTab, canAudit, employmentId]);

  const tabs = useMemo(() => {
    const list: { key: TabKey; label: string }[] = [
      { key: "overview", label: "نظرة عامة" },
      { key: "employment", label: "التوظيف" },
      { key: "assignments", label: "الإسنادات" },
      { key: "organization", label: "التنظيم" },
      { key: "contract", label: "العقد" },
      { key: "compensation", label: "التعويضات" },
      { key: "private", label: "المعلومات الخاصة" },
      { key: "timeline", label: "الخط الزمني" },
      { key: "compliance", label: "الالتزام" },
      { key: "audit", label: "التدقيق" },
    ];
    const allowed = new Set<TabKey>(["overview", "employment", "assignments", "organization", "contract", "timeline", "compliance"]);
    if (canComp) allowed.add("compensation");
    if (canPii) allowed.add("private");
    if (canAudit) allowed.add("audit");
    return list.filter((t) => allowed.has(t.key));
  }, [canComp, canPii, canAudit]);

  const commandList = employment
    ? (STATUS_COMMANDS[employment.currentStatus] ?? [])
        .filter((c) => c === "terminate" ? canTerminate || canUpdate : canUpdate)
    : [];

  async function confirmLifecycle() {
    if (!employment || !pendingCommand) return;
    setDialogBusy(true);
    setNotice(null);
    try {
      await hrmV2Api.employmentLifecycle(
        employment.employmentId,
        pendingCommand,
        {
          effectiveDate,
          expectedVersion: employment.version,
          ...(reasonCode.trim() ? { reasonCode: reasonCode.trim() } : {}),
        },
        newIdempotencyKey(),
      );
      setPendingCommand(null);
      setReasonCode("");
      setNotice("تم تنفيذ العملية بنجاح");
      await loadCore();
    } catch (err) {
      // Render the canonical safe Arabic message (conflict/compliance/etc.)
      // inside the dialog context. Never overwrite newer server data.
      setNotice(null);
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setDialogBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/employees">
      <p className={styles.breadcrumb}>
        <Link href="/hr/employees" className={styles.tableLink}>العودة إلى سجل الموظفين</Link>
      </p>

      {loading ? <HrLoading /> : error ? (
        <HrErrorState error={error} onRetry={loadCore} />
      ) : employment && person ? (
        <section aria-label={`ملف الموظف ${person.displayName}`}>
          <header className={styles.profileHeader}>
            <div>
              <h2 className={styles.profileName}>{person.displayName}</h2>
              <p className={styles.profileMeta}>
                {employment.employeeNumber} · {employmentStatusAr(employment.currentStatus)} ·{" "}
                {workerClassificationAr(employment.workerClassificationCode)}
              </p>
            </div>
            {compliance ? (
              <HrComplianceBadge
                mode={compliance.mode}
                packCode={compliance.packCode}
                packVersion={compliance.packVersion}
              />
            ) : null}
          </header>

          {notice ? (
            <p role="status" className={styles.notice}>{notice}</p>
          ) : null}

          <div role="tablist" aria-label="أقسام ملف الموظف" className={styles.tablist}
               onKeyDown={(e) => onTablistKeyDown(e, tabs.map((t) => t.key), activeTab, setActiveTab)}>
            {tabs.map((t) => (
              <button
                key={t.key}
                type="button"
                role="tab"
                id={`tab-${t.key}`}
                aria-selected={activeTab === t.key}
                aria-controls={`panel-${t.key}`}
                tabIndex={activeTab === t.key ? 0 : -1}
                className={activeTab === t.key ? `${styles.tab} ${styles.tabActive}` : styles.tab}
                onClick={() => setActiveTab(t.key)}
              >
                {t.label}
              </button>
            ))}
          </div>

          <div role="tabpanel" id={`panel-${activeTab}`} aria-labelledby={`tab-${activeTab}`} className={styles.tabpanel}>
            {activeTab === "overview" ? (
              <dl className={styles.detailList}>
                <dt>الاسم</dt><dd>{person.displayName}</dd>
                <dt>الرقم الوظيفي</dt><dd>{employment.employeeNumber}</dd>
                <dt>الحالة</dt><dd>{employmentStatusAr(employment.currentStatus)}</dd>
                <dt>التصنيف</dt><dd>{workerClassificationAr(employment.workerClassificationCode)}</dd>
                <dt>تاريخ المباشرة</dt><dd>{formatArabicDate(employment.employmentStartDate)}</dd>
                <dt>رابط مستخدم</dt>
                <dd>{person.userId ? "مرتبط بحساب مستخدم" : "غير مرتبط"}</dd>
              </dl>
            ) : null}

            {activeTab === "employment" ? (
              <div>
                <h3>سجل التوظيف</h3>
                <dl className={styles.detailList}>
                  <dt>الحالة الحالية</dt><dd>{employmentStatusAr(employment.currentStatus)}</dd>
                  <dt>الكيان القانوني</dt><dd>{employment.legalEntityId}</dd>
                  <dt>تاريخ المباشرة</dt><dd>{formatArabicDate(employment.employmentStartDate)}</dd>
                  <dt>تاريخ انتهاء الخدمة</dt>
                  <dd>{employment.terminationDate ? formatArabicDate(employment.terminationDate) : "—"}</dd>
                  <dt>إصدار السجل</dt><dd>{employment.version}</dd>
                </dl>

                {commandList.length > 0 ? (
                  <div className={styles.actionRow} aria-label="إجراءات دورة الحياة">
                    {commandList.map((c) => (
                      <button key={c} type="button" className={styles.actionButton}
                              onClick={() => { setPendingCommand(c); setDialogError(null); setEffectiveDate(todayIso()); }}>
                        {LIFECYCLE_COMMANDS[c].label}
                      </button>
                    ))}
                  </div>
                ) : (
                  <p className={styles.mutedNote}>لا توجد إجراءات متاحة في الحالة الحالية.</p>
                )}
              </div>
            ) : null}

            {activeTab === "assignments" ? (
              assignments.length === 0 ? (
                <HrEmptyState title="لا توجد إسنادات" description="لم تُسجَّل إسنادات لهذا الموظف." />
              ) : (
                <div className={styles.hrTableWrap}>
                  <table className={styles.hrTable}>
                    <thead>
                      <tr>
                        <th scope="col">النوع</th>
                        <th scope="col">الحالة</th>
                        <th scope="col">من</th>
                        <th scope="col">إلى</th>
                        <th scope="col">نسبة التخصيص</th>
                      </tr>
                    </thead>
                    <tbody>
                      {assignments.map((a) => (
                        <tr key={a.assignmentId}>
                          <td>{a.assignmentType}</td>
                          <td>{ASSIGNMENT_STATUS_AR[a.status] ?? a.status}</td>
                          <td>{formatArabicDate(a.effectiveFrom)}</td>
                          <td>{a.effectiveTo ? formatArabicDate(a.effectiveTo) : "—"}</td>
                          <td>{a.allocationPercent ?? "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            ) : null}

            {activeTab === "organization" ? (
              assignments.length === 0 ? (
                <HrEmptyState title="لا توجد بيانات تنظيمية" description="تُعرض البيانات التنظيمية من الإسنادات السارية." />
              ) : (
                <dl className={styles.detailList}>
                  <dt>الوحدة التنظيمية</dt>
                  <dd>{assignments[0]?.orgUnitId ?? "—"}</dd>
                  <dt>المنصب</dt>
                  <dd>{assignments[0]?.positionId ?? "—"}</dd>
                  <dt>المدير المباشر (إسناد)</dt>
                  <dd>{assignments[0]?.reportsToAssignmentId ?? "—"}</dd>
                </dl>
              )
            ) : null}

            {activeTab === "contract" ? (
              contracts.length === 0 ? (
                <HrEmptyState title="لا توجد عقود" description="لم تُسجَّل عقود لهذا التوظيف." />
              ) : (
                <div className={styles.hrTableWrap}>
                  <table className={styles.hrTable}>
                    <thead>
                      <tr>
                        <th scope="col">رقم العقد</th>
                        <th scope="col">الحالة</th>
                        <th scope="col">تاريخ السريان</th>
                        <th scope="col">حالة الالتزام</th>
                      </tr>
                    </thead>
                    <tbody>
                      {contracts.map((c) => (
                        <tr key={c.contractId}>
                          <td>{c.contractNumber}</td>
                          <td>{CONTRACT_STATUS_AR[c.status] ?? c.status}</td>
                          <td>{formatArabicDate(c.effectiveDate)}</td>
                          <td>{c.complianceStatus ?? "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            ) : null}

            {activeTab === "compensation" ? (
              !canComp ? <HrEmptyState title="قسم محمي" description="عرض التعويضات يتطلب صلاحية خاصة." /> :
              compState === "error" ? <HrErrorState error={compError} /> :
              !compensation ? <HrLoading /> : compensation.length === 0 ? (
                <HrEmptyState title="لا توجد حزم تعويض" description="لم تُسجَّل تعويضات لهذا التوظيف." />
              ) : (
                <div className={styles.hrTableWrap}>
                  <table className={styles.hrTable}>
                    <thead>
                      <tr>
                        <th scope="col">العملة</th>
                        <th scope="col">دورية الصرف</th>
                        <th scope="col">من</th>
                        <th scope="col">الحالة</th>
                      </tr>
                    </thead>
                    <tbody>
                      {compensation.map((p) => (
                        <tr key={p.packageId}>
                          <td>{p.currencyCode}</td>
                          <td>{p.payFrequency ?? "—"}</td>
                          <td>{formatArabicDate(p.effectiveFrom)}</td>
                          <td>{p.status}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            ) : null}

            {activeTab === "private" ? (
              !canPii ? <HrEmptyState title="قسم محمي" description="عرض المعلومات الخاصة يتطلب صلاحية خاصة ويُسجَّل في التدقيق." /> :
              privateState === "error" ? <HrErrorState error={privateError} /> :
              privateData ? (
                <dl className={styles.detailList}>
                  <dt>تاريخ الميلاد</dt><dd>{privateData.dateOfBirth ? formatArabicDate(privateData.dateOfBirth) : "غير مسجل"}</dd>
                  <dt>الجنسية</dt><dd>{privateData.nationalityCountryCode ?? "غير مسجل"}</dd>
                  <dt>الحالة الاجتماعية</dt><dd>{privateData.maritalStatus ?? "غير مسجل"}</dd>
                </dl>
              ) : <HrLoading label="جارٍ القراءة (تُسجَّل في سجل التدقيق)…" />
            ) : null}

            {activeTab === "timeline" ? (
              <ol className={styles.timeline}>
                <li><strong>{formatArabicDate(employment.employmentStartDate)}</strong> — بدء التوظيف</li>
                {assignments.map((a) => (
                  <li key={a.assignmentId}>
                    <strong>{formatArabicDate(a.effectiveFrom)}</strong> — إسناد ({a.assignmentType})
                  </li>
                ))}
                {contracts.map((c) => (
                  <li key={c.contractId}>
                    <strong>{formatArabicDate(c.effectiveDate)}</strong> — عقد {c.contractNumber}
                  </li>
                ))}
                {employment.terminationDate ? (
                  <li><strong>{formatArabicDate(employment.terminationDate)}</strong> — انتهاء الخدمة</li>
                ) : null}
              </ol>
            ) : null}

            {activeTab === "compliance" ? (
              compliance ? (
                <div>
                  <HrComplianceBadge
                    mode={compliance.mode}
                    packCode={compliance.packCode}
                    packVersion={compliance.packVersion}
                  />
                  <dl className={styles.detailList}>
                    <dt>الاختصاص القانوني</dt><dd>{compliance.laborJurisdiction}</dd>
                    <dt>تصنيف العامل</dt><dd>{compliance.workerClassification}</dd>
                    <dt>تاريخ السريان</dt><dd>{compliance.effectiveDate ? formatArabicDate(compliance.effectiveDate) : "—"}</dd>
                  </dl>
                </div>
              ) : <HrLoading label="جارٍ القراءة (تُسجَّل في سجل التدقيق)…" />
            ) : null}

            {activeTab === "audit" ? (
              !canAudit ? <HrEmptyState title="قسم محمي" description="عرض التدقيق يتطلب صلاحية خاصة." /> :
              auditState === "error" ? <HrErrorState error={auditError} /> :
              !audit ? <HrLoading /> : audit.length === 0 ? (
                <HrEmptyState title="لا توجد قيود تدقيق" description="لم تُسجَّل قيود تدقيق لهذا التوظيف." />
              ) : (
                <div className={styles.hrTableWrap}>
                  <table className={styles.hrTable}>
                    <thead>
                      <tr>
                        <th scope="col">الحدث</th>
                        <th scope="col">النتيجة</th>
                        <th scope="col">الوقت</th>
                      </tr>
                    </thead>
                    <tbody>
                      {audit.map((a) => (
                        <tr key={a.auditId}>
                          <td>{a.action}</td>
                          <td>{a.result}</td>
                          <td>{new Date(a.occurredAt).toLocaleString("ar", { calendar: "gregory", numberingSystem: "latn" })}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            ) : null}
          </div>
        </section>
      ) : (
        <HrEmptyState title="غير موجود" description="سجل التوظيف المطلوب غير متاح." />
      )}

      {pendingCommand ? (
        <div role="dialog" aria-modal="true" aria-label={LIFECYCLE_COMMANDS[pendingCommand].confirmTitle}
             className={styles.dialogOverlay}>
          <div className={styles.dialog}>
            <h3>{LIFECYCLE_COMMANDS[pendingCommand].confirmTitle}</h3>
            <p className={styles.mutedNote}>
              سيتم إرسال التاريخ الفعلي وإصدار السجل الحالي مع مفتاح تفرد لضمان عدم التكرار.
            </p>
            {dialogError ? <p role="alert" className={styles.dialogError}>{dialogError}</p> : null}
            <label className={styles.dialogField}>
              التاريخ الفعلي
              <input type="date" value={effectiveDate} onChange={(e) => setEffectiveDate(e.target.value)} />
            </label>
            <label className={styles.dialogField}>
              سبب الإجراء (اختياري)
              <input type="text" value={reasonCode} onChange={(e) => setReasonCode(e.target.value)} />
            </label>
            <div className={styles.actionRow}>
              <button type="button" className={styles.actionButton} onClick={confirmLifecycle} disabled={dialogBusy}>
                تأكيد
              </button>
              <button type="button" className={styles.cancelButton} onClick={() => { setPendingCommand(null); setDialogError(null); }}>
                إلغاء
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </HrWorkspace>
  );
}

// ---------------------------------------------------------------------------
// Local helpers
// ---------------------------------------------------------------------------

function useEmploymentId(): { employmentId: string } {
  // Next.js App Router dynamic segment. Isolated for testability.
  const params = useParams<{ employmentId: string }>();
  return { employmentId: params?.employmentId ?? "" };
}

function onTablistKeyDown(
  e: ReactKeyboardEvent,
  keys: TabKey[],
  current: TabKey,
  setTab: (t: TabKey) => void,
): void {
  const idx = keys.indexOf(current);
  if (e.key === "ArrowLeft" && idx > 0) { e.preventDefault(); setTab(keys[idx - 1]); }
  if (e.key === "ArrowRight" && idx < keys.length - 1) { e.preventDefault(); setTab(keys[idx + 1]); }
  if (e.key === "ArrowDown" && idx < keys.length - 1) { e.preventDefault(); setTab(keys[idx + 1]); }
  if (e.key === "ArrowUp" && idx > 0) { e.preventDefault(); setTab(keys[idx - 1]); }
}
