// @vitest-environment jsdom

/**
 * WS5 Task 9 — Employee Directory contract tests (RED first).
 *
 * Pins:
 * - directory renders SAFE canonical fields only (join of Employment +
 *   Person summaries); fixture fields like National ID or compensation
 *   amounts must NEVER render in directory rows even if the transport
 *   accidentally returns them;
 * - Arabic-first labels, search and status filtering;
 * - loading / empty / forbidden states;
 * - no PII fetches are made by the directory surface.
 */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { hrmV2ApiMock, authMock } = vi.hoisted(() => ({
  hrmV2ApiMock: {
    listEmployments: vi.fn(),
    listPeople: vi.fn(),
  },
  authMock: { state: "AUTHENTICATED", capabilities: [] as string[] },
}));

vi.mock("@/lib/api/hr-v2-api", () => ({
  hrmV2Api: hrmV2ApiMock,
  // Mirror the real parser contract: parse structured HRM_* envelopes into a
  // plain {code, status, violations, requestId} object (like the real one).
  parseHrmV2Error: vi.fn((err: unknown) => {
    const details = (err as { details?: { status?: number; body?: { code?: unknown; message?: unknown; violations?: unknown } } })?.details;
    const code = details?.body?.code;
    if (typeof code === "string" && code.startsWith("HRM_")) {
      return Object.assign(new Error(typeof details?.body?.message === "string" ? details.body.message : code), {
        code,
        status: typeof details?.status === "number" ? details.status : 0,
        violations: Array.isArray(details?.body?.violations) ? details?.body?.violations : null,
        requestId: null,
      });
    }
    return null;
  }),
  HrmV2ApiError: class HrmV2ApiError extends Error {},
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: authMock.state, me: { capabilities: authMock.capabilities } }),
}));

vi.mock("next/navigation", () => ({
  useRouter: vi.fn(() => ({ replace: vi.fn(), push: vi.fn() })),
  usePathname: () => "/hr/employees",
}));

import EmployeeDirectoryPage from "./page";

const EMPLOYMENTS = [
  {
    employmentId: "e-1",
    personId: "p-1",
    legalEntityId: "le-1",
    employeeNumber: "EMP-0001",
    workerClassificationCode: "FULL_TIME",
    currentStatus: "ACTIVE",
    employmentStartDate: "2025-01-01",
    terminationDate: null,
    rehireOfEmployeeId: null,
    version: 3,
  },
  {
    employmentId: "e-2",
    personId: "p-2",
    legalEntityId: "le-1",
    employeeNumber: "EMP-0002",
    workerClassificationCode: "PART_TIME",
    currentStatus: "ONBOARDING",
    employmentStartDate: "2026-09-01",
    terminationDate: null,
    rehireOfEmployeeId: null,
    version: 1,
  },
];

const PEOPLE = [
  {
    personId: "p-1",
    userId: "u-1",
    firstName: "سالم",
    middleName: null,
    lastName: "العتيبي",
    displayName: "سالم العتيبي",
    version: 5,
  },
  {
    personId: "p-2",
    userId: null,
    firstName: "نورة",
    middleName: null,
    lastName: "القحطاني",
    displayName: "نورة القحطاني",
    version: 2,
  },
];

beforeEach(() => {
  hrmV2ApiMock.listEmployments.mockReset();
  hrmV2ApiMock.listPeople.mockReset();
  hrmV2ApiMock.listEmployments.mockResolvedValue(EMPLOYMENTS);
  hrmV2ApiMock.listPeople.mockResolvedValue(PEOPLE);
  authMock.state = "AUTHENTICATED";
  authMock.capabilities = ["HRM.EMPLOYEE.VIEW"];
});

afterEach(() => cleanup());

describe("Employee Directory", () => {
  it("lists employees with Arabic headers and safe canonical fields only", async () => {
    render(<EmployeeDirectoryPage />);

    expect(await screen.findByRole("table")).toBeInTheDocument();
    const table = screen.getByRole("table");
    expect(within(table).getByText("سالم العتيبي")).toBeInTheDocument();
    expect(within(table).getByText("EMP-0001")).toBeInTheDocument();
    expect(within(table).getByText("نشِط")).toBeInTheDocument();
    expect(within(table).getByText("نورة القحطاني")).toBeInTheDocument();
    expect(within(table).getByText("تأهيل")).toBeInTheDocument();
  });

  it("never renders restricted fields even if the transport leaks them", async () => {
    // Simulate a transport-level leak: extra restricted fields ride along
    // with the response. The directory type is a safe summary; the page
    // must not display them.
    hrmV2ApiMock.listEmployments.mockResolvedValue(
      EMPLOYMENTS.map((e) => ({ ...e, nationalId: "1098765432", baseSalary: 25000 })),
    );
    hrmV2ApiMock.listPeople.mockResolvedValue(
      PEOPLE.map((p) => ({ ...p, dateOfBirth: "1990-01-01" })),
    );

    render(<EmployeeDirectoryPage />);
    await screen.findByText("سالم العتيبي");

    expect(screen.queryByText("1098765432")).not.toBeInTheDocument();
    expect(screen.queryByText("25000")).not.toBeInTheDocument();
    expect(screen.queryByText("1990-01-01")).not.toBeInTheDocument();
  });

  it("filters rows by the search box (name or employee number)", async () => {
    const user = userEvent.setup();
    render(<EmployeeDirectoryPage />);
    await screen.findByText("سالم العتيبي");

    await user.type(screen.getByRole("searchbox", { name: "بحث في السجل" }), "نورة");
    expect(screen.getByText("نورة القحطاني")).toBeInTheDocument();
    expect(screen.queryByText("سالم العتيبي")).not.toBeInTheDocument();
  });

  it("filters rows by status", async () => {
    const user = userEvent.setup();
    render(<EmployeeDirectoryPage />);
    await screen.findByText("سالم العتيبي");

    await user.selectOptions(screen.getByRole("combobox", { name: "الحالة الوظيفية" }), "ACTIVE");
    expect(screen.getByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.queryByText("نورة القحطاني")).not.toBeInTheDocument();
  });

  it("shows the Arabic empty state when no rows match", async () => {
    const user = userEvent.setup();
    render(<EmployeeDirectoryPage />);
    await screen.findByText("سالم العتيبي");

    await user.type(screen.getByRole("searchbox", { name: "بحث في السجل" }), "لا-يوجد-زايد");
    expect(await screen.findByText("لا توجد نتائج مطابقة")).toBeInTheDocument();
  });

  it("renders the forbidden state when the backend denies access", async () => {
    hrmV2ApiMock.listEmployments.mockRejectedValue({
      details: { status: 403, body: { code: "HRM_SCOPE_DENIED", message: "denied" } },
    });

    render(<EmployeeDirectoryPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("لا تملك الصلاحية");
  });

  it("shows a loading state before data arrives and never fetches PII", async () => {
    let resolveList!: (v: unknown) => void;
    hrmV2ApiMock.listEmployments.mockImplementation(() => new Promise((r) => { resolveList = r; }));

    render(<EmployeeDirectoryPage />);
    expect(screen.getByRole("status")).toBeInTheDocument();

    // The loader runs deferred to the next macrotask (codebase pattern).
    await waitFor(() => expect(hrmV2ApiMock.listEmployments).toHaveBeenCalledTimes(1));
    resolveList([]);
    await waitFor(() => expect(screen.getByText("لا يوجد موظفون بعد")).toBeInTheDocument());
    expect(hrmV2ApiMock.listPeople).toHaveBeenCalledTimes(1);
    expect(hrmV2ApiMock.listEmployments).toHaveBeenCalledTimes(1);
  });
});
