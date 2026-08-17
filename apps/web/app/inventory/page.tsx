"use client";
import { redirect } from "next/navigation";
// Inventory is part of ERP module — redirect there
export default function InventoryPage() {
  redirect("/erp");
}
