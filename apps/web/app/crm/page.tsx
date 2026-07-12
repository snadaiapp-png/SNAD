import { redirect } from "next/navigation";

/**
 * CRM root entry — redirect to the operational overview.
 *
 * Previously this page rendered CrmWorkspaceV2 + CrmAdvancedView directly.
 * Those components are now superseded by the route-based CRM pages under
 * /crm/(operational)/* — see apps/web/app/crm/(operational)/layout.tsx.
 *
 * The redirect is server-side so users navigating to /crm land on the
 * overview KPI dashboard without an intermediate client-side render.
 */
export default function CrmPage() {
  redirect("/crm/overview");
}
