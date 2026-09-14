"use client";

import styles from "../workflow.module.css";
import { WorkflowSectionHeader } from "./workflow-ui";

/**
 * Read-only Y2 operating policy summary. Capability and SLA governance remain
 * server-authoritative until a dedicated governance editor is introduced.
 */
export function WorkflowSettings() {
  const policies = [
    {
      name: "الموافقة الذاتية",
      value: <>مرفوضة افتراضيًا <code>DENY</code></>,
      description: <>السماح الاستثنائي يتطلب صلاحية <code>WORKFLOW.SELF_APPROVAL_OVERRIDE</code>.</>,
    },
    {
      name: "سياسات التجميع",
      value: <><code>ANY_ONE</code> أو <code>ALL</code></>,
      description: "إما موافقة واحدة كافية، أو اشتراط موافقة جميع الأطراف. سياسة النصاب ليست مفعّلة حاليًا.",
    },
    {
      name: "تسليم الأحداث",
      value: <>تسليم <code>At-least-once</code></>,
      description: "قد يصل الحدث أكثر من مرة؛ تمنع طبقة inbox المعالجة المكررة بدل افتراض exactly-once.",
    },
    {
      name: "حوكمة الإصدارات",
      value: "مسودة ← تحقق ← نشر",
      description: "التعريف المنشور غير قابل للتعديل في مكانه؛ التغيير يبدأ من مسودة إصدار جديدة.",
    },
    {
      name: "استراتيجية المحرك",
      value: <><code>LEGACY</code> + <code>Y2</code></>,
      description: "المثيلات الجارية تكمل بمحركها الأصلي، بينما تتبع البدايات الجديدة سياسة القطع المعتمدة.",
    },
  ];

  return (
    <div dir="rtl">
      <WorkflowSectionHeader
        eyebrow="الحوكمة"
        title="سياسات التشغيل"
        description="مرجع مبسّط للسياسات الحاكمة التي يفرضها الخادم. هذه الشاشة للقراءة فقط ولا تمنح صلاحيات."
      />

      <div className={styles.policyGrid}>
        {policies.map((policy) => (
          <article key={policy.name} className={styles.policyCard}>
            <h3 className={styles.cardTitle}>{policy.name}</h3>
            <div className={styles.policyValue}>{policy.value}</div>
            <div className={styles.policyDescription}>{policy.description}</div>
          </article>
        ))}
      </div>
    </div>
  );
}
