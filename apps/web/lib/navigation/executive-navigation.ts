/** Executive Management navigation registry. */
export interface NavItem {
  id: string;
  labelKey: string;
  href: string;
  capability: string;
}

export const executiveNavigation: NavItem[] = [
  { id: "tenants", labelKey: "nav.executive.tenants", href: "/executive", capability: "EXECUTIVE_VIEW" },
  { id: "directory", labelKey: "nav.executive.directory", href: "/executive", capability: "EXECUTIVE_VIEW" },
  { id: "plans", labelKey: "nav.executive.plans", href: "/executive", capability: "EXECUTIVE_VIEW" },
  { id: "subscriptions", labelKey: "nav.executive.subscriptions", href: "/executive", capability: "EXECUTIVE_VIEW" },
  { id: "billing", labelKey: "nav.executive.billing", href: "/executive", capability: "EXECUTIVE_BILLING" },
];
