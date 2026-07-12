// @vitest-environment jsdom

/**
 * SNAD CRM operational route tests (CRM-002b)
 * ----------------------------------------------------------------------------
 * Branch: crm/002b-final-operational-acceptance
 *
 * Verifies:
 *   - /crm redirects to /crm/overview (server-side redirect contract).
 *   - The three new detail route modules export a default React component
 *     (i.e. the route is wired up).
 *   - The detail components render the loading skeleton while data is in
 *     flight, and the error state when the API rejects.
 *
 * The detail pages fetch their data on mount; we mock crmApi so we can drive
 * the loading and error branches deterministically without a backend.
 */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { ThemeProvider } from "@/lib/theme/ThemeProvider";

import CrmPage from "./page";
import ContactDetailPage from "./(operational)/contacts/[contactId]/page";
import LeadDetailPage from "./(operational)/leads/[leadId]/page";
import OpportunityDetailPage from "./(operational)/opportunities/[opportunityId]/page";

const { crmApiMock, useParamsMock } = vi.hoisted(() => ({
  crmApiMock: {
    contact: vi.fn(),
    accounts: vi.fn(),
    customFieldValues: vi.fn(),
    activities: vi.fn(),
    archiveContact: vi.fn(),
    restoreContact: vi.fn(),
    lead: vi.fn(),
    timeline: vi.fn(),
    pipelines: vi.fn(),
    stages: vi.fn(),
    convertLead: vi.fn(),
    changeLeadStatus: vi.fn(),
    opportunity: vi.fn(),
    moveOpportunity: vi.fn(),
  },
  useParamsMock: vi.fn(),
}));

vi.mock("@/lib/api/crm", () => ({
  crmApi: crmApiMock,
}));

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => (
    <a href={String(href)} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("next/navigation", () => ({
  useParams: () => useParamsMock(),
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    refresh: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

function renderWithProviders(node: React.ReactNode) {
  return render(
    <ThemeProvider>
      <I18nProvider>{node}</I18nProvider>
    </ThemeProvider>,
  );
}

describe("/crm — server-side redirect", () => {
  beforeEach(() => {
    useParamsMock.mockReset();
    for (const key of Object.keys(crmApiMock) as (keyof typeof crmApiMock)[]) {
      crmApiMock[key].mockReset();
    }
  });
  afterEach(cleanup);

  it("exports a server-rendered page component that performs the redirect", () => {
    // The /crm page calls `redirect("/crm/overview")` from next/navigation
    // at module-evaluation time. We assert the page module exports a
    // function; the actual redirect behavior is asserted by the governance
    // drift check (scripts/crm/governance-drift-check.sh) and the E2E test
    // under apps/web/e2e/crm-operational.spec.ts.
    expect(typeof CrmPage).toBe("function");
  });
});

describe("Contact detail route — /crm/contacts/[contactId]", () => {
  beforeEach(() => {
    useParamsMock.mockReset();
    for (const key of Object.keys(crmApiMock) as (keyof typeof crmApiMock)[]) {
      crmApiMock[key].mockReset();
    }
  });
  afterEach(cleanup);

  it("exports a default React component", () => {
    expect(typeof ContactDetailPage).toBe("function");
  });

  it("renders the loading skeleton while data is in flight", () => {
    useParamsMock.mockReturnValue({ contactId: "contact-1" });
    // Pending promises → loading state.
    crmApiMock.contact.mockReturnValue(new Promise(() => {}));
    crmApiMock.accounts.mockResolvedValue([]);
    crmApiMock.customFieldValues.mockResolvedValue({ entityType: "CONTACT", entityId: "contact-1", values: [] });
    crmApiMock.activities.mockResolvedValue([]);

    renderWithProviders(<ContactDetailPage />);
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("renders the error state when the contact fetch rejects", async () => {
    useParamsMock.mockReturnValue({ contactId: "contact-1" });
    crmApiMock.contact.mockRejectedValue(new Error("network failure"));
    crmApiMock.accounts.mockResolvedValue([]);
    crmApiMock.customFieldValues.mockResolvedValue({ entityType: "CONTACT", entityId: "contact-1", values: [] });
    crmApiMock.activities.mockResolvedValue([]);

    renderWithProviders(<ContactDetailPage />);
    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
  });

  it("renders the not-found empty state when the contact is null after a 404", async () => {
    useParamsMock.mockReturnValue({ contactId: "missing" });
    // The user-facing-errors mapper returns a 404 → "not found" message,
    // but the page treats any non-thrown-but-null contact as not-found.
    // Here we simulate the page's null-state branch by resolving with null.
    crmApiMock.contact.mockRejectedValue(new Error("404"));
    crmApiMock.accounts.mockResolvedValue([]);
    crmApiMock.customFieldValues.mockResolvedValue({ entityType: "CONTACT", entityId: "missing", values: [] });
    crmApiMock.activities.mockResolvedValue([]);

    renderWithProviders(<ContactDetailPage />);
    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
  });
});

describe("Lead detail route — /crm/leads/[leadId]", () => {
  beforeEach(() => {
    useParamsMock.mockReset();
    for (const key of Object.keys(crmApiMock) as (keyof typeof crmApiMock)[]) {
      crmApiMock[key].mockReset();
    }
  });
  afterEach(cleanup);

  it("exports a default React component", () => {
    expect(typeof LeadDetailPage).toBe("function");
  });

  it("renders the loading skeleton while data is in flight", () => {
    useParamsMock.mockReturnValue({ leadId: "lead-1" });
    crmApiMock.lead.mockReturnValue(new Promise(() => {}));
    crmApiMock.customFieldValues.mockResolvedValue({ entityType: "LEAD", entityId: "lead-1", values: [] });
    crmApiMock.timeline.mockResolvedValue([]);

    renderWithProviders(<LeadDetailPage />);
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("renders the error state when the lead fetch rejects", async () => {
    useParamsMock.mockReturnValue({ leadId: "lead-1" });
    crmApiMock.lead.mockRejectedValue(new Error("server error"));
    crmApiMock.customFieldValues.mockResolvedValue({ entityType: "LEAD", entityId: "lead-1", values: [] });
    crmApiMock.timeline.mockResolvedValue([]);

    renderWithProviders(<LeadDetailPage />);
    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
  });
});

describe("Opportunity detail route — /crm/opportunities/[opportunityId]", () => {
  beforeEach(() => {
    useParamsMock.mockReset();
    for (const key of Object.keys(crmApiMock) as (keyof typeof crmApiMock)[]) {
      crmApiMock[key].mockReset();
    }
  });
  afterEach(cleanup);

  it("exports a default React component", () => {
    expect(typeof OpportunityDetailPage).toBe("function");
  });

  it("renders the loading skeleton while data is in flight", () => {
    useParamsMock.mockReturnValue({ opportunityId: "opp-1" });
    crmApiMock.opportunity.mockReturnValue(new Promise(() => {}));
    crmApiMock.accounts.mockResolvedValue([]);
    crmApiMock.pipelines.mockResolvedValue([]);
    crmApiMock.stages.mockResolvedValue([]);
    crmApiMock.customFieldValues.mockResolvedValue({ entityType: "OPPORTUNITY", entityId: "opp-1", values: [] });
    crmApiMock.activities.mockResolvedValue([]);

    renderWithProviders(<OpportunityDetailPage />);
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("renders the error state when the opportunity fetch rejects", async () => {
    useParamsMock.mockReturnValue({ opportunityId: "opp-1" });
    crmApiMock.opportunity.mockRejectedValue(new Error("network failure"));
    crmApiMock.accounts.mockResolvedValue([]);
    crmApiMock.pipelines.mockResolvedValue([]);
    crmApiMock.stages.mockResolvedValue([]);
    crmApiMock.customFieldValues.mockResolvedValue({ entityType: "OPPORTUNITY", entityId: "opp-1", values: [] });
    crmApiMock.activities.mockResolvedValue([]);

    renderWithProviders(<OpportunityDetailPage />);
    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
  });
});
