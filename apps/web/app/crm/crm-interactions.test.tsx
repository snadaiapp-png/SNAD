// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CrmPipelineBoard, adjacentStageId } from "./crm-pipeline-board";
import { CrmVirtualTable, visibleWindow } from "./crm-virtual-table";
import type { CrmOpportunity, CrmPipeline, CrmStage } from "@/lib/api/crm";

const pipeline: CrmPipeline = {
  id: "pipeline-1",
  name: "Enterprise",
  currency_code: "SAR",
  active: true,
};

const stages: CrmStage[] = [
  { id: "stage-1", pipeline_id: pipeline.id, name: "New", sequence: 1, probability: 10 },
  { id: "stage-2", pipeline_id: pipeline.id, name: "Won", sequence: 2, probability: 100, terminal_state: "WON" },
];

const opportunity: CrmOpportunity = {
  id: "opportunity-1",
  account_id: "account-1",
  pipeline_id: pipeline.id,
  stage_id: "stage-1",
  name: "ERP rollout",
  amount: 250000,
  currency_code: "SAR",
  probability: 10,
  status: "OPEN",
  updated_at: "2026-07-02T00:00:00Z",
};

afterEach(cleanup);

describe("CRM pipeline accessibility", () => {
  it("resolves adjacent stages in sequence order", () => {
    expect(adjacentStageId([...stages].reverse(), "stage-1", 1)).toBe("stage-2");
    expect(adjacentStageId(stages, "stage-1", -1)).toBeNull();
  });

  it("moves an opportunity with the explicit keyboard alternative", async () => {
    const onMove = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <CrmPipelineBoard
        pipelines={[pipeline]}
        stages={{ [pipeline.id]: stages }}
        opportunities={[opportunity]}
        accountNames={new Map([["account-1", "Acme"]])}
        busy={false}
        onMove={onMove}
      />,
    );

    await user.click(screen.getByRole("button", { name: "نقل ERP rollout إلى المرحلة التالية" }));
    expect(onMove).toHaveBeenCalledWith("opportunity-1", "stage-2");
  });

  it("moves an opportunity with Alt plus arrow keys", () => {
    const onMove = vi.fn().mockResolvedValue(undefined);
    render(
      <CrmPipelineBoard
        pipelines={[pipeline]}
        stages={{ [pipeline.id]: stages }}
        opportunities={[opportunity]}
        accountNames={new Map([["account-1", "Acme"]])}
        busy={false}
        onMove={onMove}
      />,
    );

    fireEvent.keyDown(screen.getByRole("article"), { key: "ArrowLeft", altKey: true });
    expect(onMove).toHaveBeenCalledWith("opportunity-1", "stage-2");
  });
});

describe("CRM table virtualization", () => {
  it("calculates an overscanned visible window", () => {
    expect(visibleWindow(1000, 5800, 58, 420, 5)).toEqual({
      first: 95,
      last: 113,
      top: 5510,
      bottom: 51446,
    });
  });

  it("renders only visible rows while publishing the full row count", () => {
    const rows = Array.from({ length: 500 }, (_, index) => ({
      id: `row-${index}`,
      name: `Record ${index}`,
    }));
    render(
      <CrmVirtualTable
        rows={rows}
        label="Virtual CRM records"
        height={116}
        rowHeight={58}
        columns={[{ key: "name", header: "Name", render: (row) => row.name }]}
      />,
    );

    const table = screen.getByRole("table");
    expect(table).toHaveAttribute("aria-rowcount", "501");
    expect(screen.getByText("Record 0")).toBeInTheDocument();
    expect(screen.queryByText("Record 499")).not.toBeInTheDocument();
  });
});
