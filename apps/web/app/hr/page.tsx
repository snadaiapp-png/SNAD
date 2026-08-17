"use client";
import { ExecutiveShell } from "@/components/shell";
import { useI18n } from "@/lib/i18n/I18nProvider";

export default function HrPage() {
  const { t } = useI18n();
  return (
    <ExecutiveShell>
      <div style={{ padding: "2rem", maxWidth: "800px", margin: "0 auto", textAlign: "center" }}>
        <h1 style={{ fontSize: "1.75rem", fontWeight: 700, marginBottom: "1rem" }}>
          الموارد البشرية
        </h1>
        <p style={{ color: "var(--snad-text-muted, #8b949e)", fontSize: "1rem", lineHeight: 1.7 }}>
          هذه الوحدة مخطط لها كجزء من منصة سند ولكن لم يتم تنفيذها بعد.
          سيشمل قسم الموارد البشرية إدارة الموظفين والهيكل التنظيمي والحضور والإجازات والرواتب.
        </p>
        <p style={{ color: "var(--snad-text-dim, #6e7681)", fontSize: "0.875rem", marginTop: "1.5rem" }}>
          Foundation: NOT_STARTED — Backend implementation planned
        </p>
      </div>
    </ExecutiveShell>
  );
}
