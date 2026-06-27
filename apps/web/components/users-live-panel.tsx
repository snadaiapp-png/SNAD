"use client";

import { useState, type FormEvent } from "react";
import { authApi } from "@/lib/api/auth";
import { usersApi, type UserLifecycleAction, type UserResponse, type UserStatus } from "@/lib/api/users";
import { useTenantContext } from "@/lib/auth/tenant-context";
import { toUserFacingError, type UserFacingError } from "@/lib/api/user-facing-errors";
import { useAuth } from "@/lib/auth/auth-provider";

const statusLabel: Record<UserStatus, string> = { ACTIVE: "نشط", INACTIVE: "غير نشط", INVITED: "بانتظار القبول", SUSPENDED: "موقوف", ARCHIVED: "مؤرشف" };
const statusClass: Record<UserStatus, string> = { ACTIVE: "bg-emerald-50 text-emerald-700 ring-emerald-600/20", INACTIVE: "bg-slate-100 text-slate-600 ring-slate-500/20", INVITED: "bg-amber-50 text-amber-700 ring-amber-600/20", SUSPENDED: "bg-rose-50 text-rose-700 ring-rose-600/20", ARCHIVED: "bg-slate-100 text-slate-600 ring-slate-500/20" };

export default function UsersLivePanel() {
  const { tenantId, isReady } = useTenantContext();
  const { logout, user } = useAuth();
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [status, setStatus] = useState<UserStatus>("INVITED");
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState("اضغط تحميل لعرض المستخدمين.");
  const [error, setError] = useState<UserFacingError | null>(null);

  async function loadUsers() {
    if (!tenantId) return;
    setBusy("load"); setError(null);
    try { const result = await usersApi.list(tenantId); setUsers(result); setNotice(`تم تحميل ${result.length} مستخدم من النظام.`); }
    catch (err) { setError(toUserFacingError(err)); setUsers([]); }
    finally { setBusy(null); }
  }

  async function createUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId) return;
    setBusy("create"); setError(null);
    try { const saved = await usersApi.create(tenantId, { email, displayName: displayName || null, status }); setUsers((items) => [saved, ...items]); setEmail(""); setDisplayName(""); setStatus("INVITED"); setNotice("تم إنشاء المستخدم بنجاح."); }
    catch (err) { setError(toUserFacingError(err)); }
    finally { setBusy(null); }
  }

  async function transition(item: UserResponse, action: UserLifecycleAction) {
    setBusy(item.id); setError(null);
    try { const saved = await usersApi.transition(tenantId!, item.id, action); setUsers((items) => items.map((current) => current.id === saved.id ? saved : current)); setNotice("تم تحديث حالة المستخدم."); }
    catch (err) { setError(toUserFacingError(err)); }
    finally { setBusy(null); }
  }

  async function sendPasswordSetup(item: UserResponse) {
    setBusy(`recovery:${item.id}`); setError(null);
    try { const response = await authApi.adminResetPassword(item.id, { locale: "ar" }); setNotice(response.message); }
    catch (err) { setError(toUserFacingError(err)); }
    finally { setBusy(null); }
  }

  return (
    <section className="mx-auto max-w-[1600px] px-4 py-6 sm:px-7 lg:px-9" aria-labelledby="live-users-title">
      <div className="rounded-3xl border border-teal-900/10 bg-white p-5 shadow-sm sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-black tracking-[0.16em] text-teal-700" lang="en">SNAD · LIVE API</p><h2 id="live-users-title" className="mt-2 text-2xl font-black text-slate-900">إدارة المستخدمين</h2><p className="mt-2 text-sm text-slate-500">إدارة دورة حياة الحساب وإرسال رابط إعداد كلمة المرور إلى البريد.</p></div><div className="flex items-center gap-3"><span dir="ltr" className="text-xs font-bold text-slate-600">{user?.email}</span><button type="button" onClick={logout} className="rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-black text-slate-700">خروج</button></div></div>
        <button type="button" onClick={loadUsers} disabled={busy !== null || !isReady} className="mt-5 rounded-xl bg-brand-primary px-5 py-2.5 font-black text-white disabled:opacity-50">{busy === "load" ? "جارٍ التحميل…" : "تحميل المستخدمين"}</button>
        {error && <div className="mt-3 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert"><p className="font-black">{error.title}</p><p className="mt-1">{error.message}</p></div>}
        <p className="mt-3 rounded-xl bg-slate-50 px-4 py-3 text-sm font-bold text-slate-700" role="status">{notice}</p>
        <div className="mt-6 grid gap-5 xl:grid-cols-[0.8fr_1.2fr]">
          <form onSubmit={createUser} className="grid content-start gap-3 rounded-2xl border border-slate-200 p-4"><h3 className="font-black text-slate-900">مستخدم جديد</h3><label className="grid gap-1 text-sm font-bold text-slate-700">البريد الإلكتروني<input type="email" dir="ltr" value={email} onChange={(event) => setEmail(event.target.value)} required className="rounded-xl border border-slate-200 px-3 py-2.5 text-left" /></label><label className="grid gap-1 text-sm font-bold text-slate-700">اسم العرض<input value={displayName} onChange={(event) => setDisplayName(event.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5" /></label><label className="grid gap-1 text-sm font-bold text-slate-700">الحالة<select value={status} onChange={(event) => setStatus(event.target.value as UserStatus)} className="rounded-xl border border-slate-200 px-3 py-2.5"><option value="INVITED">بانتظار القبول</option><option value="ACTIVE">نشط</option><option value="INACTIVE">غير نشط</option></select></label><button disabled={busy !== null || !isReady} className="rounded-xl bg-brand-primary px-4 py-2.5 font-black text-white disabled:opacity-50">{busy === "create" ? "جارٍ الحفظ…" : "إنشاء المستخدم"}</button></form>
          <div className="grid content-start gap-3">{users.length === 0 ? <div className="rounded-2xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">لا توجد بيانات محملة.</div> : users.map((item) => <article key={item.id} className="rounded-2xl border border-slate-200 p-4"><div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0"><h3 dir="ltr" className="truncate font-black text-slate-900">{item.email}</h3>{item.displayName && <p className="mt-1 text-sm text-slate-600">{item.displayName}</p>}</div><span className={`rounded-full px-3 py-1 text-xs font-black ring-1 ring-inset ${statusClass[item.status]}`}>{statusLabel[item.status]}</span></div><div className="mt-4 flex flex-wrap gap-2">{item.status !== "ACTIVE" && <button type="button" disabled={busy !== null} onClick={() => transition(item, "activate")} className="rounded-lg border border-emerald-200 px-3 py-1.5 text-xs font-black text-emerald-700">تفعيل</button>}{item.status !== "INACTIVE" && <button type="button" disabled={busy !== null} onClick={() => transition(item, "deactivate")} className="rounded-lg border border-amber-200 px-3 py-1.5 text-xs font-black text-amber-700">تعطيل</button>}{item.status === "ACTIVE" && <button type="button" disabled={busy !== null} onClick={() => sendPasswordSetup(item)} className="rounded-lg bg-brand-primary px-3 py-1.5 text-xs font-black text-white">{busy === `recovery:${item.id}` ? "جارٍ الإرسال…" : "إرسال رابط كلمة المرور"}</button>}</div></article>)}</div>
        </div>
      </div>
    </section>
  );
}
