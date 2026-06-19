import SanadDashboard from "@/components/sanad-dashboard";

export default function Home() {
  return (
    <>
      <section className="border-b border-teal-900/10 bg-[#0f2d2a] px-4 py-3 text-white sm:px-7 lg:px-9">
        <div className="mx-auto flex max-w-[1600px] flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs font-black tracking-[0.18em] text-teal-200">SANAD BUSINESS OPERATING SYSTEM</p>
            <h1 className="mt-1 text-lg font-black sm:text-xl">النسخة الأولية لنظام سند جاهزة للتشغيل والمراجعة</h1>
          </div>
          <span className="rounded-full bg-emerald-400/15 px-3 py-1.5 text-xs font-black text-emerald-200 ring-1 ring-inset ring-emerald-300/30">
            Production Preview v0.1
          </span>
        </div>
      </section>
      <SanadDashboard />
    </>
  );
}
