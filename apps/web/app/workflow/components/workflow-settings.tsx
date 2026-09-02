"use client";

/**
 * Settings (design decision AP3): Y2 operating policy summary. V1 is
 * read-only — capability and SLA governance stay server-authoritative;
 * editing arrives with the governance UI wave.
 */
export function WorkflowSettings() {
  const policies = [
    { name: "سياسة الموافقة الذاتية", value: "مرفوضة افتراضيًا (DENY) — استثناء يتطلب WORKFLOW.SELF_APPROVAL_OVERRIDE" },
    { name: "سياسات التجميع", value: "ANY_ONE و ALL — التسبيب (QUORUM) مؤجل" },
    { name: "تسليم الأحداث", value: "At-least-once مع inbox مُكرر الإسناد — لا exactly-once" },
    { name: "حوكمة الإصدارات", value: "مسودة ← تحقق ← نشر غير قابل للتحريف" },
    { name: "المحركات", value: "LEGACY للمثيلات الجارية، Y2 لكل بدء جديد بعد القطع" },
  ];
  return (
    <div dir="rtl">
      <h3>سياسات التشغيل</h3>
      <table style={{ borderCollapse: "collapse", width: "100%" }}>
        <tbody>
          {policies.map((policy) => (
            <tr key={policy.name}>
              <td style={{ border: "1px solid var(--snad-color-border, #ddd)", padding: 8, fontWeight: 600, width: 220 }}>
                {policy.name}
              </td>
              <td style={{ border: "1px solid var(--snad-color-border, #ddd)", padding: 8 }}>{policy.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
