"use client";
import { redirect } from "next/navigation";
// Identity is managed under Control Plane / Management
export default function IdentityPage() {
  redirect("/management");
}
