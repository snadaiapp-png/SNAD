"use client";

import { useState } from "react";
import type {
  WorkflowSimulationResponse,
  WorkflowValidationResponse,
} from "@/lib/api/workflow-api";
import type { WorkflowDiagnostic } from "./workflow-diagnostics";
import styles from "./workflow-designer.module.css";

type DrawerTab = "Validation" | "Simulation" | "Activity";

export function DiagnosticsDrawer({
  localDiagnostics,
  latestValidation,
  simulation,
  activity,
  onSelectStep,
  onSelectTransition,
}: {
  localDiagnostics: WorkflowDiagnostic[];
  latestValidation: WorkflowValidationResponse | null;
  simulation: WorkflowSimulationResponse | null;
  activity: string[];
  onSelectStep: (id: string) => void;
  onSelectTransition: (id: string) => void;
}) {
  const [tab, setTab] = useState<DrawerTab>("Validation");

  return (
    <section className={styles.drawer} aria-label="مركز التشخيص والأدلة">
      <div className={styles.drawerTabs} role="tablist" aria-label="Diagnostics">
        {(["Validation", "Simulation", "Activity"] as DrawerTab[]).map((item) => (
          <button
            key={item}
            type="button"
            role="tab"
            aria-selected={tab === item}
            className={tab === item ? styles.drawerTabActive : undefined}
            onClick={() => setTab(item)}
          >
            {item}
          </button>
        ))}
      </div>

      {tab === "Validation" && (
        <div role="tabpanel" className={styles.drawerBody}>
          <div className={styles.evidenceBlock}>
            <strong>تشخيص محلي حتمي</strong>
            <small>إرشادي؛ لا يستبدل تحقق الخادم.</small>
            {localDiagnostics.length === 0 ? (
              <p>لا توجد ملاحظات بنيوية محلية.</p>
            ) : (
              <ul>
                {localDiagnostics.map((item, index) => (
                  <li key={`${item.code}-${item.stepId ?? item.transitionId ?? index}`}>
                    <button
                      type="button"
                      className={styles.diagnosticLink}
                      disabled={!item.stepId && !item.transitionId}
                      onClick={() => {
                        if (item.stepId) onSelectStep(item.stepId);
                        if (item.transitionId) onSelectTransition(item.transitionId);
                      }}
                    >
                      [{item.severity}] {item.code}: {item.message}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className={styles.evidenceBlock}>
            <strong>تحقق الخادم — السلطة النهائية للنشر</strong>
            {!latestValidation ? (
              <p>لم يُنفذ تحقق خادم صالح للرسم الحالي.</p>
            ) : latestValidation.valid ? (
              <p>صالح وفق آخر تحقق خادم ✓</p>
            ) : (
              <ul>
                {latestValidation.errors.map((item, index) => (
                  <li key={`${item.code}-${item.stepId}-${index}`}>
                    <button
                      type="button"
                      className={styles.diagnosticLink}
                      disabled={!item.stepId}
                      onClick={() => item.stepId && onSelectStep(item.stepId)}
                    >
                      {item.code}: {item.message}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      {tab === "Simulation" && (
        <div role="tabpanel" className={styles.drawerBody}>
          <strong>محاكاة غير إنتاجية</strong>
          <p>هذه المعاينة لا تنفذ آثارًا جانبية ولا تنشئ WorkItems أو موافقات أو إشعارات أو تغييرات أعمال.</p>
          {!simulation ? (
            <p>لا توجد محاكاة صالحة للرسم الحالي.</p>
          ) : (
            <>
              <p>الخطوات التي تمت زيارتها: {simulation.visitedStepIds.length}</p>
              {simulation.notes.length > 0 && (
                <ul>{simulation.notes.map((note, index) => <li key={`${index}-${note}`}>{note}</li>)}</ul>
              )}
            </>
          )}
        </div>
      )}

      {tab === "Activity" && (
        <div role="tabpanel" className={styles.drawerBody}>
          <strong>نشاط الجلسة الحالية</strong>
          <small>ليس سجل تدقيق من الخادم.</small>
          {activity.length === 0 ? (
            <p>لا يوجد نشاط مسجل في هذه الجلسة بعد.</p>
          ) : (
            <ol>{activity.map((item, index) => <li key={`${index}-${item}`}>{item}</li>)}</ol>
          )}
        </div>
      )}
    </section>
  );
}
