"use client";
import { redirect } from "next/navigation";
// Subscriptions are managed under Management (Module Entitlements)
export default function SubscriptionsPage() {
  redirect("/management");
}
