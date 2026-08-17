"use client";
import { redirect } from "next/navigation";
// POS is part of Commerce/Stores — redirect there
export default function PosPage() {
  redirect("/stores");
}
