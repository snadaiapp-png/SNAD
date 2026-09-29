import type { Metadata } from "next";
import type { ReactNode } from "react";
import { ManagementIamI18nAugmenter } from "./_client/management-iam-i18n-augmenter";
import { ManagementIamNav } from "./_client/management-iam-nav";

export const metadata: Metadata = {
  title: "مركز القيادة التنفيذية | SNAD",
  description: "لوحة القيادة التنفيذية للإدارة العليا",
};

export default function ManagementLayout({ children }: { children: ReactNode }) {
  return (
    <ManagementIamI18nAugmenter>
      <ManagementIamNav />
      {children}
    </ManagementIamI18nAugmenter>
  );
}
