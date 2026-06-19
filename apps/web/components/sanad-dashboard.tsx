"use client";

import { FormEvent, useMemo, useState } from "react";

type Tab = "overview" | "organizations" | "memberships" | "users";
type Status = "ACTIVE" | "INACTIVE" | "INVITED" | "SUSPENDED" | "ARCHIVED";

type Organization = {
  id: string;
  name: string;
  description: string;
  status: Status;
};

type Member = {
  id: string;
  organizationId: string;
  name: string;
  email: string;
  status: Status;
};

type User = {
  id: string;
  name: string;
  email: string;
  status: Status;
};

const tenantId = "11111111-1111-1111-1111-111111111111";

const initialOrganizations: Organization[] = [
  {
    id: "org-1",
    name: "شركة سند للتقنية",
    description: "المؤسسة الرئيسية لإدارة المنتجات والخدمات الرقمية.",
    status: "ACTIVE",
  },
  {
    id: "org-2",
    name: "فرع جدة",
    description: "العمليات التجارية وخدمة العملاء في المنطقة الغربية.",
    status: "ACTIVE",
  },
];

const initialMembers: Member[] = [
  {
    id: "member-1",
    organizationId: "org-1",
    name: "مدير المشروع",
    email: "owner@sanad.sa",
    status: "ACTIVE",
  },
  {
    id: "member-2",
    organizationId: "org-1",
    name: "فريق العمليات",
    email: "operations@sanad.sa",
    status: "INVITED",
  },
];

const initialUsers: User[] = [
  { id: "user-1", name: "مدير المشروع", email: "owner@sanad.sa", status: "ACTIVE" },
  { id: "user-2", name: "مسؤول المالية", email: "finance@sanad.sa", status: "INVITED" },
  { id: "user-3", name: "فريق الدعم", email: "support@sanad.sa", status: "SUSPENDED" },
];

const statusLabel: Record<Status, string> = {
  ACTIVE: "نشط",
  INACTIVE: "غير نشط",
  INVITED: "بانتظار القبول",
  SUSPENDED: "موقوف",
  ARCHIVED: "مؤرشف",
};

const statusClass: Record<Status, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  INACTIVE: "bg-slate-100 text-slate-600 ring-slate-500/20",
  INVITED: "bg-amber-50 text-amber-700 ring-amber-600/20",
  SUSPENDED: "bg-red-50 text-red-700 ring-red-600/20",
  ARCHIVED: "bg-violet-50 text-violet-700 ring-violet-600/20",
};

function Badge({ status }: { status: Status }) {
  return (
    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${statusClass[status]}`}>
      {statusLabel[status]}
    </span>
  );
}

function makeId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export default function SanadDashboard() {
  const [tab, setTab] = useState<Tab>("overview");
  const [organizations, setOrganizations] = useState(initialOrganizations);
  const [members, setMembers] = useState(initialMembers);
  const [users, setUsers] = useState(initialUsers);
  const [selectedOrganizationId, setSelectedOrganizationId] = useState(initialOrganizations[0].id);
  const [notice, setNotice] = useState("تم تشغيل وضع العرض التجريبي بنجاح.");
  const [organizationName, setOrganizationName] = useState("");
  const [organizationDescription, setOrganizationDescription] = useState("");
  const [memberName, setMemberName] = useState("");
  const [memberEmail, setMemberEmail] = useState("");
  const [userName, setUserName] = useState("");
  const [userEmail, setUserEmail] = useState("");

  const selectedOrganization = organizations.find((item) => item.id === selectedOrganizationId) ?? null;
  const visibleMembers = members.filter((item) => item.organizationId === selectedOrganizationId);

  const stats = useMemo(
    () => [
      { label: "المؤسسات", value: organizations.length, note: `${organizations.filter((item) => item.status === "ACTIVE").length} نشطة` },
      { label: "العضويات", value: members.length, note: `${members.filter((item) => item.status === "INVITED").length} دعوات معلقة` },
      { label: "المستخدمون", value: users.length, note: `${users.filter((item) => item.status === "ACTIVE").length} نشطون` },
      { label: "حالة النشر", value: "جاهز", note: "Vercel deployment slice" },
    ],
    [members, organizations, users],
  );

  const nav: Array<{ key: Tab; label: string; icon: string }> = [
    { key: "overview", label: "نظرة عامة", icon: "⌂" },
    { key: "organizations", label: "المؤسسات", icon: "▦" },
    { key: "memberships", label: "العضويات", icon: "◎" },
    { key: "users", label: "المستخدمون", icon: "♙" },
  ];

  function addOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!organizationName.trim()) return;
    const organization: Organization = {
      id: makeId("org"),
      name: organizationName.trim(),
      description: organizationDescription.trim() || "لا يوجد وصف.",
      status: "ACTIVE",
    };
    setOrganizations((items) => [organization, ...items]);
    setSelectedOrganizationId(organization.id);
    setOrganizationName("");
    setOrganizationDescription("");
    setNotice("تم إنشاء المؤسسة بنجاح.");
  }

  function addMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedOrganizationId || !memberEmail.trim()) return;
    setMembers((items) => [
      {
        id: makeId("member"),
        organizationId: selectedOrganizationId,
        name: memberName.trim() || "عضو جديد",
        email: memberEmail.trim().toLowerCase(),
        status: "INVITED",
      },
      ...items,
    ]);
    setMemberName("");
    setMemberEmail("");
    setNotice("تم إرسال دعوة العضوية.");
  }

  function addUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!userEmail.trim()) return;
    setUsers((items) => [
      {
        id: makeId("user"),
        name: userName.trim() || "مستخدم جديد",
        email: userEmail.trim().toLowerCase(),
        status: "INVITED",
      },
      ...items,
    ]);
    setUserName("");
    setUserEmail("");
    setNotice("تم إنشاء المستخدم.");
  }

  function toggleUser(user: User) {
    const status: Status = user.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    setUsers((items) => items.map((item) => (item.id === user.id ? { ...item, status } : item)));
    setNotice(`تم تحديث حالة المستخدم إلى ${statusLabel[status]}.`);
  }

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900 lg:grid lg:grid-cols-[260px_minmax(0,1fr)]">
      <aside className="hidden min-h-screen flex-col bg-[#102422] px-5 py-7 text-white lg:flex">
        <div className="mb-8 flex items-center gap-3 px-2">
          <div className="grid size-12 place-items-center rounded-2xl bg-teal-700 text-xl font-black shadow-lg shadow-teal-950/30">س</div>
          <div>
            <strong className="block tracking-[0.18em]">SANAD</strong>
            <span className="text-xs text-teal-100/60">Business Operating System</span>
          </div>
        </div>
        <nav className="grid gap-2">
          {nav.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setTab(item.key)}
              className={`flex items-center gap-3 rounded-xl px-3 py-3 text-right transition ${tab === item.key ? "bg-white/10 text-white" : "text-slate-300 hover:bg-white/5 hover:text-white"}`}
            >
              <span className={`grid size-8 place-items-center rounded-lg ${tab === item.key ? "bg-teal-700" : "bg-white/5"}`}>{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>
        <div className="mt-auto rounded-xl border border-white/10 bg-white/5 p-3 text-xs text-slate-300">
          <div className="mb-1 flex items-center gap-2 font-bold text-emerald-300"><span className="size-2 rounded-full bg-emerald-400" />واجهة النشر</div>
          <span dir="ltr" className="block break-all text-[10px] text-slate-500">{tenantId}</span>
        </div>
      </aside>

      <main className="min-w-0 p-4 pb-28 sm:p-7 lg:p-9">
        <header className="mb-6 flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-black uppercase tracking-wider text-teal-700">SANAD Workspace</p>
            <h1 className="mt-1 text-3xl font-black sm:text-4xl">{nav.find((item) => item.key === tab)?.label}</h1>
            <p className="mt-2 text-sm text-slate-500">واجهة عربية متجاوبة لإدارة العمليات الأساسية للمنصة.</p>
          </div>
          <div className="grid size-11 place-items-center rounded-xl border border-teal-100 bg-teal-50 font-black text-teal-800">ع</div>
        </header>

        <section className="mb-5 rounded-2xl border border-teal-100 bg-white p-4 shadow-sm sm:flex sm:items-center sm:justify-between sm:gap-6">
          <div>
            <p className="font-black text-teal-900">بيئة العرض التجريبي</p>
            <p className="mt-1 text-sm text-slate-500">تم فصل واجهة النشر عن تغييرات Backend لضمان نشر Vercel نظيف.</p>
          </div>
          <span className="mt-3 inline-flex rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-bold text-emerald-700 sm:mt-0">جاهز للمراجعة</span>
        </section>

        <div className="mb-5 flex items-center justify-between rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm font-bold text-emerald-800">
          <span>{notice}</span>
          <button type="button" onClick={() => setNotice("")} className="text-lg leading-none">×</button>
        </div>

        {tab === "overview" && (
          <section className="grid gap-5">
            <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
              {stats.map((stat) => (
                <article key={stat.label} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                  <p className="text-sm text-slate-500">{stat.label}</p>
                  <strong className="mt-2 block text-3xl font-black">{stat.value}</strong>
                  <span className="mt-1 block text-xs text-slate-400">{stat.note}</span>
                </article>
              ))}
            </div>
            <div className="grid gap-5 xl:grid-cols-[1.3fr_0.7fr]">
              <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <div className="mb-5 flex items-start justify-between">
                  <div><p className="text-xs font-black text-teal-700">جاهزية الواجهة</p><h2 className="mt-1 text-xl font-black">المسار التشغيلي الأول</h2></div>
                  <span className="rounded-xl bg-teal-50 px-3 py-2 text-xs font-black text-teal-800">RTL + Responsive</span>
                </div>
                <div className="grid gap-4">
                  {["إدارة المؤسسات", "إدارة العضويات", "إدارة المستخدمين", "تصميم متجاوب"].map((label, index) => (
                    <div key={label}>
                      <div className="mb-2 flex justify-between text-sm"><span>{label}</span><strong>{[100, 90, 90, 100][index]}%</strong></div>
                      <div className="h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-teal-700" style={{ width: `${[100, 90, 90, 100][index]}%` }} /></div>
                    </div>
                  ))}
                </div>
              </article>
              <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <p className="text-xs font-black text-teal-700">المؤسسة المحددة</p>
                {selectedOrganization ? (
                  <div className="mt-3 grid gap-3">
                    <Badge status={selectedOrganization.status} />
                    <h2 className="text-xl font-black">{selectedOrganization.name}</h2>
                    <p className="text-sm leading-7 text-slate-500">{selectedOrganization.description}</p>
                    <button type="button" onClick={() => setTab("memberships")} className="w-fit text-sm font-black text-teal-700">فتح العضويات ←</button>
                  </div>
                ) : <p className="mt-4 text-slate-500">لا توجد مؤسسة محددة.</p>}
              </article>
            </div>
          </section>
        )}

        {tab === "organizations" && (
          <section className="grid gap-5 xl:grid-cols-[340px_minmax(0,1fr)]">
            <form onSubmit={addOrganization} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="mb-5 text-xl font-black">مؤسسة جديدة</h2>
              <label className="grid gap-2 text-sm font-bold">اسم المؤسسة<input value={organizationName} onChange={(event) => setOrganizationName(event.target.value)} className="rounded-xl border border-slate-300 px-3 py-2.5 outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-100" required /></label>
              <label className="mt-4 grid gap-2 text-sm font-bold">الوصف<textarea value={organizationDescription} onChange={(event) => setOrganizationDescription(event.target.value)} rows={4} className="rounded-xl border border-slate-300 px-3 py-2.5 outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-100" /></label>
              <button className="mt-5 w-full rounded-xl bg-teal-700 px-4 py-3 font-black text-white hover:bg-teal-800">إنشاء المؤسسة</button>
            </form>
            <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="mb-5 text-xl font-black">سجل المؤسسات</h2>
              <div className="grid gap-3">
                {organizations.map((organization) => (
                  <button key={organization.id} type="button" onClick={() => setSelectedOrganizationId(organization.id)} className={`grid gap-3 rounded-xl border p-4 text-right transition sm:grid-cols-[1fr_auto] ${selectedOrganizationId === organization.id ? "border-teal-300 bg-teal-50/40" : "border-slate-200 hover:border-teal-200"}`}>
                    <div><strong className="block">{organization.name}</strong><span className="mt-1 block text-sm text-slate-500">{organization.description}</span></div><Badge status={organization.status} />
                  </button>
                ))}
              </div>
            </article>
          </section>
        )}

        {tab === "memberships" && (
          <section className="grid gap-5 xl:grid-cols-[340px_minmax(0,1fr)]">
            <form onSubmit={addMember} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="mb-5 text-xl font-black">دعوة عضو</h2>
              <label className="grid gap-2 text-sm font-bold">المؤسسة<select value={selectedOrganizationId} onChange={(event) => setSelectedOrganizationId(event.target.value)} className="rounded-xl border border-slate-300 px-3 py-2.5">{organizations.map((organization) => <option key={organization.id} value={organization.id}>{organization.name}</option>)}</select></label>
              <label className="mt-4 grid gap-2 text-sm font-bold">الاسم<input value={memberName} onChange={(event) => setMemberName(event.target.value)} className="rounded-xl border border-slate-300 px-3 py-2.5" /></label>
              <label className="mt-4 grid gap-2 text-sm font-bold">البريد الإلكتروني<input dir="ltr" type="email" value={memberEmail} onChange={(event) => setMemberEmail(event.target.value)} className="rounded-xl border border-slate-300 px-3 py-2.5 text-left" required /></label>
              <button className="mt-5 w-full rounded-xl bg-teal-700 px-4 py-3 font-black text-white">إرسال الدعوة</button>
            </form>
            <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="mb-5 text-xl font-black">عضويات {selectedOrganization?.name}</h2>
              <div className="grid gap-3">
                {visibleMembers.length ? visibleMembers.map((member) => (
                  <div key={member.id} className="grid gap-3 rounded-xl border border-slate-200 p-4 sm:grid-cols-[1fr_auto]">
                    <div><strong className="block">{member.name}</strong><span dir="ltr" className="mt-1 block text-left text-sm text-slate-500">{member.email}</span></div><Badge status={member.status} />
                  </div>
                )) : <p className="rounded-xl bg-slate-50 p-6 text-center text-slate-500">لا توجد عضويات لهذه المؤسسة.</p>}
              </div>
            </article>
          </section>
        )}

        {tab === "users" && (
          <section className="grid gap-5 xl:grid-cols-[340px_minmax(0,1fr)]">
            <form onSubmit={addUser} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="mb-5 text-xl font-black">مستخدم جديد</h2>
              <label className="grid gap-2 text-sm font-bold">الاسم<input value={userName} onChange={(event) => setUserName(event.target.value)} className="rounded-xl border border-slate-300 px-3 py-2.5" /></label>
              <label className="mt-4 grid gap-2 text-sm font-bold">البريد الإلكتروني<input dir="ltr" type="email" value={userEmail} onChange={(event) => setUserEmail(event.target.value)} className="rounded-xl border border-slate-300 px-3 py-2.5 text-left" required /></label>
              <button className="mt-5 w-full rounded-xl bg-teal-700 px-4 py-3 font-black text-white">إنشاء المستخدم</button>
            </form>
            <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="mb-5 text-xl font-black">دليل المستخدمين</h2>
              <div className="grid gap-3">
                {users.map((user) => (
                  <div key={user.id} className="grid gap-3 rounded-xl border border-slate-200 p-4 sm:grid-cols-[1fr_auto]">
                    <div><strong className="block">{user.name}</strong><span dir="ltr" className="mt-1 block text-left text-sm text-slate-500">{user.email}</span></div>
                    <div className="flex items-center gap-2"><Badge status={user.status} /><button type="button" onClick={() => toggleUser(user)} className="rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-black text-slate-700 hover:bg-slate-200">تبديل الحالة</button></div>
                  </div>
                ))}
              </div>
            </article>
          </section>
        )}
      </main>

      <nav className="fixed inset-x-3 bottom-3 z-50 grid grid-cols-4 gap-1 rounded-2xl bg-[#102422]/95 p-2 text-slate-300 shadow-2xl backdrop-blur lg:hidden">
        {nav.map((item) => (
          <button key={item.key} type="button" onClick={() => setTab(item.key)} className={`grid justify-items-center gap-1 rounded-xl px-1 py-2 text-[11px] ${tab === item.key ? "bg-teal-700 text-white" : ""}`}><span className="text-base">{item.icon}</span>{item.label}</button>
        ))}
      </nav>
    </div>
  );
}
