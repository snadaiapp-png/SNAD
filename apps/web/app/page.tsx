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
        <AuthBoundary>
          <section className="border-b border-teal-900/10 bg-brand-primary px-4 py-3 text-white sm:px-7 lg:px-9"><div className="mx-auto flex max-w-[1600px] flex-wrap items-center justify-between gap-3"><div><p className="text-xs font-black tracking-[0.18em] text-brand-gold">SNAD</p><h1 className="mt-1 text-lg font-black sm:text-xl">نظام تشغيل الأعمال الذكي</h1></div><span className="rounded-full bg-white/10 px-3 py-1.5 text-xs font-black ring-1 ring-inset ring-white/20">Development Preview</span></div></section>
          <OrganizationLivePanel />
          <UsersLivePanel />
          <MembershipsLivePanel />
          <SanadDashboard />
        </AuthBoundary>
      </TenantContextProvider>
    </AuthProvider>
  );
}
