"use client";

import { FormEvent, useState } from "react";
import { usersApi, type UserLifecycleAction, type UserResponse, type UserStatus } from "@/lib/api/users";
import { useTenantContext } from "@/lib/auth/tenant-context";
import { toUserFacingError, type UserFacingError } from "@/lib/api/user-facing-errors";
import { useAuth } from "@/lib/auth/auth-provider";

const statusLabel: Record<UserStatus, string> = {
  ACTIVE: "نشط", INACTIVE: "غير نشط", INVITED: "بانتظار القبول", SUSPENDED: "موقوف", ARCHIVED: "مؤرشف",
};
const statusClass: Record<UserStatus, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  INACTIVE: "bg-slate-100 text-slate-600 ring-slate-500/20",
  INVITED: "bg-amber-50 text-amber-700 ring-amber-600/20",
  SUSPENDED: "bg-rose-50 text-rose-700 ring-rose-600/20",
  ARCHIVED: "bg-violet-50 text-violet-700 ring-violet-600/20",
};

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
    try {
      const result = await usersApi.list(tenantId);
      setUsers(result);
      setNotice(`تم تحميل ${result.length} مستخدم من Backend.`);
    } catch (err) { setError(toUserFacingError(err)); setUsers([]);
    } finally { setBusy(null); }
  }

  async function createUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId) return;
    setBusy("create"); setError(null);
    try {
      const saved = await usersApi.create(tenantId, { email, displayName: displayName || null, status });
      setUsers((items) => [saved, ...items]);
      setEmail(""); setDisplayName(""); setStatus("INVITED");
      setNotice("تم إنشاء المستخدم في Backend بنجاح.");
    } catch (err) { setError(toUserFacingError(err));
    } finally { setBusy(null); }
  }

  async function transition(item: UserResponse, action: UserLifecycleAction) {
    setBusy(item.id); setError(null);
    try {
      const saved = await usersApi.transition(tenantId!, item.id, action);
      setUsers((items) => items.map((current) => (current.id === saved.id ? saved : current)));
      setNotice("تم تحديث حالة المستخدم.");
    } catch (err) { setError(toUserFacingError(err));
    } finally { setBusy(null); }
  }

  return (
    <section className="mx-auto max-w-[1600px] px-4 py-6 sm:px-7 lg:px-9" aria-labelledby="live-users-title">
      <div className="rounded-3xl border border-teal-900/10 bg-white p-5 shadow-sm sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-black tracking-[0.16em] text-teal-700">EXEC-PROMPT-031 · LIVE API</p>
            <h2 id="live-users-title" className="mt-2 text-2xl font-black text-slate-900">إدارة المستخدمين الفعليين</h2>
            <p className="mt-2 max-w-3xl text-sm text-slate-500">سياق المستأجر يُشتق تلقائياً من الجلسة المصادقة.</p>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-xs font-bold text-slate-600">{user?.email}</span>
            <button type="button" onClick={logout} className="rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-black text-slate-700">خروج</button>
          </div>
        </div>

        <div className="mt-5">
          <button type="button" onClick={loadUsers} disabled={busy !== null || !isReady} className="rounded-xl bg-teal-800 px-5 py-2.5 font-black text-white disabled:opacity-50">
            {busy === "load" ? "جارٍ التحميل…" : "تحميل المستخدمين"}
          </button>
        </div>

        {error && (
          <div className="mt-3 rounded-xl bg-rose-50 px-4 py-3 ring-1 ring-rose-600/20" role="alert">
            <p className="text-sm font-black text-rose-700">{error.title}</p>
            <p className="mt-1 text-sm text-rose-600">{error.message}</p>
          </div>
        )}

        <p className="mt-3 rounded-xl bg-slate-50 px-4 py-3 text-sm font-bold text-slate-700" role="status">{notice}</p>

        <div className="mt-6 grid gap-5 xl:grid-cols-[0.8fr_1.2fr]">
          <form onSubmit={createUser} className="grid content-start gap-3 rounded-2xl border border-slate-200 p-4">
            <h3 className="font-black text-slate-900">مستخدم جديد</h3>
            <label className="grid gap-1 text-sm font-bold text-slate-700">
              البريد الإلكتروني
              <input type="email" dir="ltr" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={255} required className="rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-600" />
            </label>
            <label className="grid gap-1 text-sm font-bold text-slate-700">
              اسم العرض
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={200} className="rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-600" />
            </label>
            <label className="grid gap-1 text-sm font-bold text-slate-700">
              الحالة
              <select value={status} onChange={(e) => setStatus(e.target.value as UserStatus)} className="rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-600">
                <option value="INVITED">بانتظار القبول (افتراضي)</option>
                <option value="ACTIVE">نشط</option>
                <option value="INACTIVE">غير نشط</option>
                <option value="SUSPENDED">موقوف</option>
                <option value="ARCHIVED">مؤرشف</option>
              </select>
            </label>
            <button disabled={busy !== null || !isReady} className="rounded-xl bg-teal-800 px-4 py-2.5 font-black text-white disabled:opacity-50">{busy === "create" ? "جارٍ الحفظ…" : "إنشاء في Backend"}</button>
          </form>

          <div className="grid content-start gap-3">
            {users.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">لا توجد بيانات محملة.</div>
            ) : users.map((item) => (
              <article key={item.id} className="rounded-2xl border border-slate-200 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h3 className="truncate font-black text-slate-900" dir="ltr">{item.email}</h3>
                    {item.displayName && <p className="mt-1 text-sm text-slate-600">{item.displayName}</p>}
                    <p dir="ltr" className="mt-2 font-mono text-[11px] text-slate-400">{item.id}</p>
                  </div>
                  <span className={`rounded-full px-3 py-1 text-xs font-black ring-1 ring-inset ${statusClass[item.status]}`}>{statusLabel[item.status]}</span>
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  {item.status !== "ACTIVE" && <button type="button" disabled={busy !== null} onClick={() => transition(item, "activate")} className="rounded-lg border border-emerald-200 px-3 py-1.5 text-xs font-black text-emerald-700 disabled:opacity-50">تفعيل</button>}
                  {item.status !== "INACTIVE" && <button type="button" disabled={busy !== null} onClick={() => transition(item, "deactivate")} className="rounded-lg border border-amber-200 px-3 py-1.5 text-xs font-black text-amber-700 disabled:opacity-50">تعطيل</button>}
                  {item.status !== "SUSPENDED" && <button type="button" disabled={busy !== null} onClick={() => transition(item, "suspend")} className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-black text-rose-700 disabled:opacity-50">إيقاف</button>}
                  {item.status !== "ARCHIVED" && <button type="button" disabled={busy !== null} onClick={() => transition(item, "archive")} className="rounded-lg border border-violet-200 px-3 py-1.5 text-xs font-black text-violet-700 disabled:opacity-50">أرشفة</button>}
                </div>
              </article>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
