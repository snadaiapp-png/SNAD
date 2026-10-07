import { redirect } from "next/navigation";

/**
 * Canonical compatibility entry point for the Subscription & Billing module.
 *
 * The product UI lives in the Executive Subscription Control Plane. Keep this
 * legacy top-level route as a stable alias so bookmarks and workspace links do
 * not fall back to generic Management.
 */
export default function SubscriptionsPage() {
  redirect("/executive/subscriptions");
}
