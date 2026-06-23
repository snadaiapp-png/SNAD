"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { membershipsApi, type MembershipLifecycleAction, type OrganizationMembershipResponse, type MembershipStatus } from "@/lib/api/memberships";
import { organizationsApi, type OrganizationResponse } from "@/lib/api/organizations";
import { useTenantContext } from "@/lib/auth/tenant-context";
import { toUserFacingError, type UserFacingError } from "@/lib/api/user-facing-errors";

const statusLabel: Record<MembershipStatus, string> = {
  ACTIVE: "نشط", INACTIVE: "غير نشط", INVITED: "بانتظار القبول", REMOVED: "محذوف",
};
const statusClass: Record<MembershipStatus, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  INACTIVE: "bg-slate-100 text-slate-600 ring-slate-500/20",
  INVITED: "bg-amber-50 text-amber-700 ring-amber-600/20",
  REMOVED: "bg-zinc-100 text-zinc-500 ring-zinc-400/20",
};

export default function MembershipsLivePanel() {
  const { tenantId, isReady } = useTenantContext();

  // Organization selector state
  const [organizations, setOrganizations] = useState<OrganizationResponse[]>([]);
  const [selectedOrgId, setSelectedOrgId] = useState("");
  const [orgsLoading, setOrgsLoading] = useState(false);
  const [orgsError, setOrgsError] = useState<UserFacingError | null>(null);

  // Memberships state
  const [memberships, setMemberships] = useState<OrganizationMembershipResponse[]>([]);
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<UserFacingError | null>(null);

  // ---- Load organizations when tenant context is ready ----
  const loadOrganizations = useCallback(async () => {
    if (!tenantId) return;
    setOrgsLoading(true);
    setOrgsError(null);
    try {
      const result = await organizationsApi.list(tenantId);
      setOrganizations(result);
      if (result.length === 0) {
        setNotice("لا توجد مؤسسات مسجلة بعد. أنشئ مؤسسة أولاً من قسم المؤسسات.");
      } else if (result.length === 1) {
        // Auto-select if only one organization exists
        setSelectedOrgId(result[0].id);
        setNotice(null);
      } else {
        setNotice("اختر مؤسسة من القائمة لتحميل العضويات.");
      }
    } catch (err) {
      setOrgsError(toUserFacingError(err));
    } finally {
      setOrgsLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (isReady && tenantId) {
      loadOrganizations();
    }
  }, [isReady, tenantId, loadOrganizations]);

  // ---- Load memberships when an organization is selected ----
  const loadMemberships = useCallback(async () => {
    if (!tenantId || !selectedOrgId) return;
    setBusy("load");
    setError(null);
    try {
      const result = await membershipsApi.list(tenantId, selectedOrgId);
      setMemberships(result);
      if (result.length === 0) {
        setNotice("لا توجد عضويات في هذه المؤسسة بعد. دع عضواً جديداً.");
      } else {
        setNotice(`تم تحميل ${result.length} عضوية.`);
      }
    } catch (err) {
      setError(toUserFacingError(err));
      setMemberships([]);
    } finally {
      setBusy(null);
    }
  }, [tenantId, selectedOrgId]);

  // Auto-load memberships when organization changes
  useEffect(() => {
    if (selectedOrgId && tenantId) {
      loadMemberships();
    } else {
      setMemberships([]);
    }
  }, [selectedOrgId, tenantId, loadMemberships]);

  // ---- Invite a new member ----
  async function inviteMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId || !selectedOrgId) return;
    setBusy("invite");
    setError(null);
    try {
      const saved = await membershipsApi.invite(tenantId, selectedOrgId, {
        email,
        displayName: displayName || null,
      });
      setMemberships((items) => [saved, ...items]);
      setEmail("");
      setDisplayName("");
      setNotice("تمت دعوة العضو بنجاح.");
    } catch (err) {
      setError(toUserFacingError(err));
    } finally {
      setBusy(null);
    }
  }

  // ---- Transition membership lifecycle ----
  async function transition(item: OrganizationMembershipResponse, action: MembershipLifecycleAction) {
    if (!tenantId || !selectedOrgId) return;
    setBusy(item.id);
    setError(null);
    try {
      const saved = await membershipsApi.transition(tenantId, selectedOrgId, item.id, action);
      setMemberships((items) =>
        items.map((current) => (current.id === saved.id ? saved : current))
      );
      setNotice("تم تحديث حالة العضوية.");
    } catch (err) {
      setError(toUserFacingError(err));
    } finally {
      setBusy(null);
    }
  }

  // ---- Find the selected organization name ----
  const selectedOrgName = organizations.find((o) => o.id === selectedOrgId)?.name ?? "";

  // ---- Render ----
  if (!isReady) {
    return (
      <section className="mx-auto max-w-[1600px] px-4 py-6 sm:px-7 lg:px-9">
        <div className="rounded-3xl border border-teal-900/10 bg-white p-5 shadow-sm sm:p-7">
          <p className="text-sm text-slate-500">جارٍ تحميل سياق المستأجر…</p>
        </div>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-[1600px] px-4 py-6 sm:px-7 lg:px-9" aria-labelledby="live-memberships-title">
      <div className="rounded-3xl border border-teal-900/10 bg-white p-5 shadow-sm sm:p-7">
        {/* Header */}
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-black tracking-[0.16em] text-teal-700">MEMBERSHIPS · LIVE API</p>
            <h2 id="live-memberships-title" className="mt-2 text-2xl font-black text-slate-900">إدارة عضويات المؤسسات</h2>
            <p className="mt-2 max-w-3xl text-sm text-slate-500">
              سياق المستأجر يُشتق تلقائياً من الجلسة. اختر مؤسسة لعرض وإدارة عضوياتها.
            </p>
          </div>
          <span className="rounded-full bg-amber-50 px-3 py-1.5 text-xs font-black text-amber-800 ring-1 ring-amber-700/20">Pilot</span>
        </div>

        {/* Organization Selector */}
        <div className="mt-5 flex flex-wrap items-end gap-3">
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            المؤسسة
            {orgsLoading ? (
              <div className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm text-slate-400">جارٍ التحميل…</div>
            ) : organizations.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 px-3 py-2.5 text-sm text-slate-400">لا توجد مؤسسات</div>
            ) : (
              <select
                value={selectedOrgId}
                onChange={(e) => setSelectedOrgId(e.target.value)}
                className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
              >
                <option value="">— اختر مؤسسة —</option>
                {organizations
                  .filter((o) => o.status === "ACTIVE")
                  .map((org) => (
                    <option key={org.id} value={org.id}>
                      {org.name}
                    </option>
                  ))}
              </select>
            )}
          </label>
          <button
            type="button"
            onClick={loadMemberships}
            disabled={busy !== null || !selectedOrgId}
            className="rounded-xl bg-teal-800 px-5 py-2.5 font-black text-white disabled:opacity-50"
          >
            {busy === "load" ? "جارٍ التحميل…" : "تحميل العضويات"}
          </button>
          <button
            type="button"
            onClick={loadOrganizations}
            disabled={orgsLoading}
            className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-bold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
          >
            تحديث القائمة
          </button>
        </div>

        {/* Organizations loading error */}
        {orgsError && (
          <div className="mt-3 rounded-xl bg-rose-50 px-4 py-3 ring-1 ring-rose-600/20" role="alert">
            <p className="text-sm font-black text-rose-700">{orgsError.title}</p>
            <p className="mt-1 text-sm text-rose-600">{orgsError.message}</p>
          </div>
        )}

        {/* Memberships error */}
        {error && (
          <div className="mt-3 rounded-xl bg-rose-50 px-4 py-3 ring-1 ring-rose-600/20" role="alert">
            <p className="text-sm font-black text-rose-700">{error.title}</p>
            <p className="mt-1 text-sm text-rose-600">{error.message}</p>
          </div>
        )}

        {/* Status notice */}
        {notice && (
          <p className="mt-3 rounded-xl bg-slate-50 px-4 py-3 text-sm font-bold text-slate-700" role="status">
            {notice}
          </p>
        )}

        {/* Main content: Invite form + Memberships list */}
        {selectedOrgId && (
          <div className="mt-6 grid gap-5 xl:grid-cols-[0.8fr_1.2fr]">
            {/* Invite form */}
            <form onSubmit={inviteMember} className="grid content-start gap-3 rounded-2xl border border-slate-200 p-4">
              <h3 className="font-black text-slate-900">دعوة عضو جديد</h3>
              <p className="text-xs text-slate-500">إلى مؤسسة: <span className="font-bold text-slate-700">{selectedOrgName}</span></p>
              <label className="grid gap-1 text-sm font-bold text-slate-700">
                البريد الإلكتروني
                <input
                  type="email"
                  dir="ltr"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  maxLength={255}
                  required
                  className="rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-600"
                />
              </label>
              <label className="grid gap-1 text-sm font-bold text-slate-700">
                اسم العرض
                <input
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  maxLength={200}
                  className="rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-600"
                />
              </label>
              <button
                disabled={busy !== null || !selectedOrgId}
                className="rounded-xl bg-teal-800 px-4 py-2.5 font-black text-white disabled:opacity-50"
              >
                {busy === "invite" ? "جارٍ الدعوة…" : "دعوة عضو"}
              </button>
            </form>

            {/* Memberships list */}
            <div className="grid content-start gap-3">
              {memberships.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
                  {selectedOrgId
                    ? "لا توجد عضويات في هذه المؤسسة بعد."
                    : "اختر مؤسسة لعرض العضويات."}
                </div>
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
                        <button
                          type="button"
                          disabled={busy !== null}
                          onClick={() => transition(item, "activate")}
                          className="rounded-lg border border-emerald-200 px-3 py-1.5 text-xs font-black text-emerald-700 disabled:opacity-50"
                        >
                          تفعيل
                        </button>
                      )}
                      {item.status !== "INACTIVE" && (
                        <button
                          type="button"
                          disabled={busy !== null}
                          onClick={() => transition(item, "deactivate")}
                          className="rounded-lg border border-amber-200 px-3 py-1.5 text-xs font-black text-amber-700 disabled:opacity-50"
                        >
                          تعطيل
                        </button>
                      )}
                      {item.status !== "REMOVED" && (
                        <button
                          type="button"
                          disabled={busy !== null}
                          onClick={() => transition(item, "remove")}
                          className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-black text-rose-700 disabled:opacity-50"
                          title="حذف ناعم"
                        >
                          حذف (ناعم)
                        </button>
                      )}
                    </div>
                  </article>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
