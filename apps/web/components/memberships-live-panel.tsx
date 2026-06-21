"use client";

import { FormEvent, useState } from "react";
import {
  membershipsApi,
  type MembershipLifecycleAction,
  type OrganizationMembershipResponse,
  type MembershipStatus,
} from "@/lib/api/memberships";
import { toUserFacingError, type UserFacingError } from "@/lib/api/user-facing-errors";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const statusLabel: Record<MembershipStatus, string> = {
  ACTIVE: "نشط",
  INACTIVE: "غير نشط",
  INVITED: "بانتظار القبول",
  REMOVED: "محذوف",
};

const statusClass: Record<MembershipStatus, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  INACTIVE: "bg-slate-100 text-slate-600 ring-slate-500/20",
  INVITED: "bg-amber-50 text-amber-700 ring-amber-600/20",
  REMOVED: "bg-zinc-100 text-zinc-500 ring-zinc-400/20",
};

export default function MembershipsLivePanel() {
  const [tenantId, setTenantId] = useState("");
  const [organizationId, setOrganizationId] = useState("");
  const [memberships, setMemberships] = useState<OrganizationMembershipResponse[]>([]);
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState("أدخل Tenant UUID و Organization UUID لتحميل العضويات الفعلية.");
  const [error, setError] = useState<UserFacingError | null>(null);

  const normalizedTenantId = tenantId.trim();
  const normalizedOrgId = organizationId.trim();
  const tenantIsValid = UUID_PATTERN.test(normalizedTenantId);
  const orgIsValid = UUID_PATTERN.test(normalizedOrgId);
  const bothValid = tenantIsValid && orgIsValid;

  async function loadMemberships() {
    if (!bothValid) {
      setError({ title: "UUID غير صالح", message: "Tenant UUID و Organization UUID يجب أن يكونا صالحين.", kind: "validation" });
      return;
    }
    setBusy("load");
    setError(null);
    try {
      const result = await membershipsApi.list(normalizedTenantId, normalizedOrgId);
      setMemberships(result);
      setNotice(`تم تحميل ${result.length} عضوية من Backend.`);
    } catch (err) {
      setError(toUserFacingError(err));
      setMemberships([]);
    } finally {
      setBusy(null);
    }
  }

  async function inviteMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!bothValid) {
      setError({ title: "UUID غير صالح", message: "Tenant UUID و Organization UUID يجب أن يكونا صالحين.", kind: "validation" });
      return;
    }
    setBusy("invite");
    setError(null);
    try {
      const saved = await membershipsApi.invite(normalizedTenantId, normalizedOrgId, {
        email,
        displayName: displayName || null,
      });
      setMemberships((items) => [saved, ...items]);
      setEmail("");
      setDisplayName("");
      setNotice("تمت دعوة العضو في Backend بنجاح.");
    } catch (err) {
      setError(toUserFacingError(err));
    } finally {
      setBusy(null);
    }
  }

  async function transition(item: OrganizationMembershipResponse, action: MembershipLifecycleAction) {
    setBusy(item.id);
    setError(null);
    try {
      const saved = await membershipsApi.transition(normalizedTenantId, normalizedOrgId, item.id, action);
      setMemberships((items) => items.map((current) => (current.id === saved.id ? saved : current)));
      setNotice("تم تحديث حالة العضوية.");
    } catch (err) {
      setError(toUserFacingError(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="mx-auto max-w-[1600px] px-4 py-6 sm:px-7 lg:px-9" aria-labelledby="live-memberships-title">
      <div className="rounded-3xl border border-teal-900/10 bg-white p-5 shadow-sm sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-black tracking-[0.16em] text-teal-700">EXEC-PROMPT-031 · LIVE API</p>
            <h2 id="live-memberships-title" className="mt-2 text-2xl font-black text-slate-900">إدارة عضويات المؤسسات الفعلية</h2>
            <p className="mt-2 max-w-3xl text-sm text-slate-500">يُمرر Tenant UUID و Organization UUID صراحة إلى Backend. الحذف هنا ناعم — يبقى السجل لأغراض الأرشفة.</p>
          </div>
          <span className="rounded-full bg-amber-50 px-3 py-1.5 text-xs font-black text-amber-800 ring-1 ring-amber-700/20">Pilot only</span>
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            Tenant UUID
            <input
              dir="ltr"
              value={tenantId}
              onChange={(event) => setTenantId(event.target.value)}
              placeholder="00000000-0000-4000-8000-000000000000"
              className="rounded-xl border border-slate-200 px-3 py-2.5 font-mono text-sm outline-none focus:border-teal-600"
            />
          </label>
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            Organization UUID
            <input
              dir="ltr"
              value={organizationId}
              onChange={(event) => setOrganizationId(event.target.value)}
              placeholder="00000000-0000-4000-8000-000000000000"
              className="rounded-xl border border-slate-200 px-3 py-2.5 font-mono text-sm outline-none focus:border-teal-600"
            />
          </label>
        </div>
        <button type="button" onClick={loadMemberships} disabled={busy !== null || !bothValid} className="mt-3 rounded-xl bg-teal-800 px-5 py-2.5 font-black text-white disabled:opacity-50">
          {busy === "load" ? "جارٍ التحميل…" : "تحميل العضويات"}
        </button>

        {error && (
          <div className="mt-3 rounded-xl bg-rose-50 px-4 py-3 ring-1 ring-rose-600/20" role="alert">
            <p className="text-sm font-black text-rose-700">{error.title}</p>
            <p className="mt-1 text-sm text-rose-600">{error.message}</p>
          </div>
        )}

        <p className="mt-3 rounded-xl bg-slate-50 px-4 py-3 text-sm font-bold text-slate-700" role="status">{notice}</p>

        <div className="mt-6 grid gap-5 xl:grid-cols-[0.8fr_1.2fr]">
          <form onSubmit={inviteMember} className="grid content-start gap-3 rounded-2xl border border-slate-200 p-4">
            <h3 className="font-black text-slate-900">دعوة عضو جديد</h3>
            <label className="grid gap-1 text-sm font-bold text-slate-700">
              البريد الإلكتروني
              <input
                type="email"
                dir="ltr"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                maxLength={255}
                required
                className="rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-600"
              />
            </label>
            <label className="grid gap-1 text-sm font-bold text-slate-700">
              اسم العرض
              <input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                maxLength={200}
                className="rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-600"
              />
            </label>
            <button disabled={busy !== null || !bothValid} className="rounded-xl bg-teal-800 px-4 py-2.5 font-black text-white disabled:opacity-50">
              {busy === "invite" ? "جارٍ الدعوة…" : "دعوة في Backend"}
            </button>
          </form>

          <div className="grid content-start gap-3">
            {memberships.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">لا توجد بيانات محملة.</div>
            ) : (
              memberships.map((item) => (
                <article key={item.id} className="rounded-2xl border border-slate-200 p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h3 className="truncate font-black text-slate-900" dir="ltr">{item.email}</h3>
                      {item.displayName && <p className="mt-1 text-sm text-slate-600">{item.displayName}</p>}
                      <p dir="ltr" className="mt-2 font-mono text-[11px] text-slate-400">membership: {item.id}</p>
                      {item.userId && <p dir="ltr" className="font-mono text-[11px] text-slate-400">user: {item.userId}</p>}
                    </div>
                    <span className={`rounded-full px-3 py-1 text-xs font-black ring-1 ring-inset ${statusClass[item.status]}`}>
                      {statusLabel[item.status]}
                    </span>
                  </div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {item.status !== "ACTIVE" && (
                      <button type="button" disabled={busy !== null} onClick={() => transition(item, "activate")} className="rounded-lg border border-emerald-200 px-3 py-1.5 text-xs font-black text-emerald-700 disabled:opacity-50">تفعيل</button>
                    )}
                    {item.status !== "INACTIVE" && (
                      <button type="button" disabled={busy !== null} onClick={() => transition(item, "deactivate")} className="rounded-lg border border-amber-200 px-3 py-1.5 text-xs font-black text-amber-700 disabled:opacity-50">تعطيل</button>
                    )}
                    {item.status !== "REMOVED" && (
                      <button type="button" disabled={busy !== null} onClick={() => transition(item, "remove")} className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-black text-rose-700 disabled:opacity-50" title="حذف ناعم — يبقى السجل للأرشفة">حذف (ناعم)</button>
                    )}
                  </div>
                </article>
              ))
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
