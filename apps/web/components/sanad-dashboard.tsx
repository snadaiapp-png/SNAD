"use client";

const capabilities = [
  { title: "إدارة المؤسسات", description: "هيكل متعدد المؤسسات والمستأجرين مع فصل كامل للبيانات." },
  { title: "إدارة المستخدمين", description: "حسابات وصلاحيات مركزية قابلة للتدقيق." },
  { title: "الأمن والاسترداد", description: "روابط أحادية الاستخدام وإلغاء الجلسات بعد تغيير كلمة المرور." },
  { title: "الهوية العربية", description: "واجهة RTL بخط عربي معتمد ونظام ألوان موحد." },
];

export default function SanadDashboard() {
  return (
    <section className="mx-auto max-w-[1600px] px-4 pb-12 sm:px-7 lg:px-9" aria-labelledby="snad-dashboard-title">
      <div className="overflow-hidden rounded-3xl border border-teal-900/10 bg-white shadow-sm">
        <div className="grid gap-8 bg-brand-primary p-7 text-white lg:grid-cols-[1.2fr_0.8fr] lg:p-10">
          <div>
            <p className="text-xs font-black tracking-[0.22em] text-brand-gold" lang="en">SNAD BUSINESS OPERATING SYSTEM</p>
            <h2 id="snad-dashboard-title" className="mt-3 text-3xl font-black sm:text-4xl">منصة موحدة لتشغيل الأعمال</h2>
            <p className="mt-4 max-w-3xl text-sm leading-8 text-teal-100">تجمع سند الإدارة المؤسسية والأتمتة والأمن وتجربة المستخدم العربية ضمن بنية SaaS واحدة.</p>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <article className="rounded-2xl bg-white/10 p-4 ring-1 ring-inset ring-white/15"><strong className="block text-2xl tabular-nums">RTL</strong><span className="text-xs text-teal-100">واجهة عربية أصلية</span></article>
            <article className="rounded-2xl bg-white/10 p-4 ring-1 ring-inset ring-white/15"><strong className="block text-2xl tabular-nums">24/7</strong><span className="text-xs text-teal-100">تشغيل سحابي</span></article>
            <article className="rounded-2xl bg-white/10 p-4 ring-1 ring-inset ring-white/15"><strong className="block text-2xl">SaaS</strong><span className="text-xs text-teal-100">متعدد المستأجرين</span></article>
            <article className="rounded-2xl bg-white/10 p-4 ring-1 ring-inset ring-white/15"><strong className="block text-2xl">Zero Trust</strong><span className="text-xs text-teal-100">أمن افتراضي</span></article>
          </div>
        </div>
        <div className="grid gap-4 p-6 md:grid-cols-2 xl:grid-cols-4 lg:p-8">
          {capabilities.map((capability) => (
            <article key={capability.title} className="rounded-2xl border border-slate-200 bg-slate-50 p-5">
              <div className="mb-4 size-2 rounded-full bg-brand-gold" />
              <h3 className="font-black text-slate-900">{capability.title}</h3>
              <p className="mt-2 text-sm leading-7 text-slate-600">{capability.description}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
