"use client";
import { redirect } from "next/navigation";
// Notifications — security notifications exist under System Health
export default function NotificationsPage() {
  redirect("/system-health");
}
