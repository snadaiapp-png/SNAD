const crmMetrics = [
  { label: 'Accounts', value: 'Live API', detail: '/api/v1/crm/accounts' },
  { label: 'Contacts', value: 'Tenant scoped', detail: '/api/v1/crm/contacts' },
  { label: 'Leads', value: 'Convertible', detail: '/api/v1/crm/leads/{id}/convert' },
  { label: 'Pipeline', value: 'Stage history', detail: '/api/v1/crm/opportunities/{id}/stage' },
];

const crmFlows = [
  'Create tenant-scoped account',
  'Attach contact to the same tenant account',
  'Create lead and convert it into account, contact, and opportunity',
  'Move opportunity through governed stages',
  'Create activities and read Customer Timeline',
];

export default function CrmPage() {
  return (
    <main dir="rtl" className="min-h-screen bg-slate-950 px-6 py-10 text-white">
      <section className="mx-auto flex max-w-6xl flex-col gap-8">
        <header className="rounded-3xl border border-cyan-400/20 bg-white/5 p-8 shadow-2xl shadow-cyan-950/40">
          <p className="text-sm font-semibold text-cyan-300">SNAD CRM Workspace</p>
          <h1 className="mt-3 text-4xl font-bold tracking-tight">بيئة CRM التنفيذية الموحدة</h1>
          <p className="mt-4 max-w-3xl text-sm leading-7 text-slate-300">
            هذه الواجهة تعرض مسار CRM الحقيقي بعد توحيد الفروع: الحسابات، جهات الاتصال، العملاء المحتملون، الفرص، الأنشطة، والخط الزمني. البيانات يجب أن تأتي من واجهات /api/v1/crm/** عبر جلسة SNAD الآمنة وليس من جداول benchmark الاصطناعية.
          </p>
        </header>

        <section className="grid gap-4 md:grid-cols-4">
          {crmMetrics.map((metric) => (
            <article key={metric.label} className="rounded-2xl border border-white/10 bg-white/[0.04] p-5">
              <p className="text-sm text-slate-400">{metric.label}</p>
              <p className="mt-2 text-2xl font-semibold text-cyan-200">{metric.value}</p>
              <p className="mt-2 break-words text-xs text-slate-500" dir="ltr">{metric.detail}</p>
            </article>
          ))}
        </section>

        <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
          <article className="rounded-3xl border border-white/10 bg-white/[0.04] p-6">
            <h2 className="text-2xl font-semibold">مسار العمل الحقيقي المطلوب اختباره</h2>
            <ol className="mt-5 space-y-3 text-sm text-slate-300">
              {crmFlows.map((flow, index) => (
                <li key={flow} className="flex gap-3 rounded-2xl bg-slate-900/80 p-4">
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-cyan-400 text-sm font-bold text-slate-950">{index + 1}</span>
                  <span>{flow}</span>
                </li>
              ))}
            </ol>
          </article>

          <aside className="rounded-3xl border border-amber-300/20 bg-amber-300/10 p-6">
            <h2 className="text-xl font-semibold text-amber-100">حالة الاعتماد</h2>
            <div className="mt-4 space-y-3 text-sm text-amber-50/80">
              <p>الواجهة الحالية هي بوابة تشغيل CRM وليست إعلان إنتاج.</p>
              <p>الاعتماد النهائي يتطلب نجاح smoke test مصادق، build للواجهة، وFlyway clean + upgrade.</p>
              <p className="font-mono text-xs text-amber-200" dir="ltr">CRM_PRODUCTION_READY: NO</p>
            </div>
          </aside>
        </section>
      </section>
    </main>
  );
}
