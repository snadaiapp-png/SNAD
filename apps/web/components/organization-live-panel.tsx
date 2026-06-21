"use client";

import { FormEvent, useState } from "react";
import {
  organizationsApi,
  type OrganizationLifecycleAction,
  type OrganizationResponse,
} from "@/lib/api/organizations";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "تعذر إكمال الطلب.";
}

function statusLabel(status: OrganizationResponse["status"]): string {
  return status === "ACTIVE" ? "نشطة" : status === "ARCHIVED" ? "مؤرشفة" : "غير نشطة";
}

export default function OrganizationLivePanel() {
  const [tenantId, setTenantId] = useState("");
  const [organizations, setOrganizations] = useState<OrganizationResponse[]>([]);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState("أدخل Tenant UUID لتحميل المؤسسات الفعلية.");

  const normalizedTenantId = tenantId.trim();
  const tenantIsValid = UUID_PATTERN.test(normalizedTenantId);

  async function loadOrganizations() {
    if (!tenantIsValid) {
      setNotice("Tenant UUID غير صالح.");
      return;
    }
    setBusy("load");
    try {
      const result = await organizationsApi.list(normalizedTenantId);
      setOrganizations(result);
      setNotice(`تم تحميل ${result.length} مؤسسة من Backend.`);
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  async function createOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantIsValid) {
      setNotice("Tenant UUID غير صالح.");
      return;
    }
    setBusy("create");
    try {
      const saved = await organizationsApi.create(normalizedTenantId, { name, description });
      setOrganizations((items) => [saved, ...items]);
      setName("");
      setDescription("");
      setNotice("تم إنشاء المؤسسة في Backend بنجاح.");
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  async function transition(item: OrganizationResponse, action: OrganizationLifecycleAction) {
    setBusy(item.id);
    try {
      const saved = await organizationsApi.transition(normalizedTenantId, item.id, action);
      setOrganizations((items) => items.map((current) => current.id === saved.id ? saved : current));
      setNotice("تم تحديث حالة المؤسسة.");
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="mx-auto max-w-[1600px] px-4 py-6 sm:px-7 lg:px-9" aria-labelledby="live-organizations-title">
      <div className="rounded-3xl border border-teal-900/10 bg-white p-5 shadow-sm sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-black tracking-[0.16em] text-teal-700">EXEC-PROMPT-030 · LIVE API</p>
            <h2 id="live-organizations-title" className="mt-2 text-2xl font-black text-slate-900">إدارة المؤسسات الفعلية</h2>
            <p className="mt-2 max-w-3xl text-sm text-slate-500">يُمرر Tenant UUID صراحة إلى Backend. لا توجد مصادقة أو هوية مستأجر تلقائية في هذه المرحلة.</p>
          </div>
          <span className="rounded-full bg-amber-50 px-3 py-1.5 text-xs font-black text-amber-800 ring-1 ring-amber-700/20">Pilot only</span>
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
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
          <button type="button" onClick={loadOrganizations} disabled={busy !== null} className="self-end rounded-xl bg-teal-800 px-5 py-2.5 font-black text-white disabled:opacity-50">
            {busy === "load" ? "جارٍ التحميل…" : "تحميل المؤسسات"}
          </button>
        </div>

        <p className="mt-3 rounded-xl bg-slate-50 px-4 py-3 text-sm font-bold text-slate-700" role="status">{notice}</p>

        <div className="mt-6 grid gap-5 xl:grid-cols-[0.8fr_1.2fr]">
          <form onSubmit={createOrganization} className="grid content-start gap-3 rounded-2xl border border-slate-200 p-4">
            <h3 className="font-black text-slate-900">مؤسسة جديدة</h3>
            <label className="grid gap-1 text-sm font-bold text-slate-700">
              الاسم
              <input value={name} onChange={(event) => setName(event.target.value)} maxLength={200} required className="rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-600" />
            </label>
            <label className="grid gap-1 text-sm font-bold text-slate-700">
              الوصف
              <textarea value={description} onChange={(event) => setDescription(event.target.value)} maxLength={1000} rows={4} className="rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-600" />
            </label>
            <button disabled={busy !== null || !tenantIsValid} className="rounded-xl bg-teal-800 px-4 py-2.5 font-black text-white disabled:opacity-50">{busy === "create" ? "جارٍ الحفظ…" : "إنشاء في Backend"}</button>
          </form>

          <div className="grid content-start gap-3">
            {organizations.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">لا توجد بيانات محملة.</div>
            ) : organizations.map((item) => (
              <article key={item.id} className="rounded-2xl border border-slate-200 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h3 className="font-black text-slate-900">{item.name}</h3>
                    <p className="mt-1 text-sm text-slate-500">{item.description || "لا يوجد وصف"}</p>
                    <p dir="ltr" className="mt-2 font-mono text-[11px] text-slate-400">{item.id}</p>
                  </div>
                  <span className="rounded-full bg-teal-50 px-3 py-1 text-xs font-black text-teal-800">{statusLabel(item.status)}</span>
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  {item.status !== "ACTIVE" && <button type="button" disabled={busy !== null} onClick={() => transition(item, "activate")} className="rounded-lg border border-emerald-200 px-3 py-1.5 text-xs font-black text-emerald-700">تفعيل</button>}
                  {item.status === "ACTIVE" && <button type="button" disabled={busy !== null} onClick={() => transition(item, "deactivate")} className="rounded-lg border border-amber-200 px-3 py-1.5 text-xs font-black text-amber-700">تعطيل</button>}
                  {item.status !== "ARCHIVED" && <button type="button" disabled={busy !== null} onClick={() => transition(item, "archive")} className="rounded-lg border border-red-200 px-3 py-1.5 text-xs font-black text-red-700">أرشفة</button>}
                </div>
              </article>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
