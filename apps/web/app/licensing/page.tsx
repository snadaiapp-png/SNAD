"use client";
import { redirect } from "next/navigation";
// Licensing is managed under Management (Module Entitlements)
export default function LicensingPage() {
  redirect("/management");
}
