import { redirect } from "next/navigation";

/**
 * CRM root entry — redirect to the operational overview.
 *
 * The redirect is server-side so users navigating to /crm land on the
 * overview KPI dashboard without an intermediate client-side render.
 */
export default function CrmPage() {
  redirect("/crm/overview");
}
