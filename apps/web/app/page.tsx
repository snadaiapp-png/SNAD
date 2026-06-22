"use client";

import { AuthProvider } from "@/lib/auth/auth-provider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";
import { AuthBoundary } from "@/components/auth/auth-boundary";
import OrganizationLivePanel from "@/components/organization-live-panel";
import UsersLivePanel from "@/components/users-live-panel";
import MembershipsLivePanel from "@/components/memberships-live-panel";
import SanadDashboard from "@/components/sanad-dashboard";

export default function Home() {
  return (
    <AuthProvider>
      <TenantContextProvider>
        <section className="border-b border-teal-900/10 bg-[#0f2d2a] px-4 py-3 text-white sm:px-7 lg:px-9">
          <div className="mx-auto flex max-w-[1600px] flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs font-black tracking-[0.18em] text-teal-200">SANAD BUSINESS OPERATING SYSTEM</p>
              <h1 className="mt-1 text-lg font-black sm:text-xl">النسخة الأولية لنظام سند جاهزة للتشغيل والمراجعة</h1>
            </div>
            <span className="rounded-full bg-emerald-400/15 px-3 py-1.5 text-xs font-black text-emerald-200 ring-1 ring-inset ring-emerald-300/30">
              Pilot Preview v0.4
            </span>
          </div>
        </section>
        <AuthBoundary>
          <OrganizationLivePanel />
          <UsersLivePanel />
          <MembershipsLivePanel />
          <SanadDashboard />
        </AuthBoundary>
      </TenantContextProvider>
    </AuthProvider>
  );
}
