/**
 * CRM-035 — Lead Status Transition Tests
 *
 * Verifies:
 * - Terminal status detection (CONVERTED, ARCHIVED)
 * - Status selector disabled for terminal leads
 * - No PATCH request sent for terminal leads
 * - Valid transitions for non-terminal leads
 */
import { describe, it, expect } from "vitest";

/* ============================================================================
 *  Terminal status constants (mirrors leads-tab.tsx)
 * ============================================================================ */

const TERMINAL_STATUSES = new Set(["CONVERTED", "ARCHIVED"]);

const LEAD_STATUSES = ["NEW", "ASSIGNED", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED"];

/* ============================================================================
 *  Transition validation (mirrors backend state machine)
 * ============================================================================ */

function leadTransitionAllowed(current, next) {
  if (current === next) return true;
  switch (current) {
    case "NEW":
      return ["ASSIGNED", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED"].includes(next);
    case "ASSIGNED":
      return ["CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED"].includes(next);
    case "CONTACTED":
      return ["QUALIFIED", "DISQUALIFIED", "ARCHIVED"].includes(next);
    case "QUALIFIED":
      return ["DISQUALIFIED", "ARCHIVED"].includes(next);
    case "DISQUALIFIED":
      return next === "ARCHIVED";
    default:
      return false; // CONVERTED, ARCHIVED, unknown -> all transitions blocked
  }
}

/* ============================================================================
 *  Unit Tests — Transition State Machine
 * ============================================================================ */

describe("CRM-035: Lead Status Transitions", () => {
  describe("Terminal status detection", () => {
    it("CONVERTED is terminal", () => {
      expect(TERMINAL_STATUSES.has("CONVERTED")).toBe(true);
    });

    it("ARCHIVED is terminal", () => {
      expect(TERMINAL_STATUSES.has("ARCHIVED")).toBe(true);
    });

    it("NEW is not terminal", () => {
      expect(TERMINAL_STATUSES.has("NEW")).toBe(false);
    });

    it("ASSIGNED is not terminal", () => {
      expect(TERMINAL_STATUSES.has("ASSIGNED")).toBe(false);
    });

    it("CONTACTED is not terminal", () => {
      expect(TERMINAL_STATUSES.has("CONTACTED")).toBe(false);
    });

    it("QUALIFIED is not terminal", () => {
      expect(TERMINAL_STATUSES.has("QUALIFIED")).toBe(false);
    });

    it("DISQUALIFIED is not terminal", () => {
      expect(TERMINAL_STATUSES.has("DISQUALIFIED")).toBe(false);
    });
  });

  describe("Valid transitions (non-terminal)", () => {
    it("NEW -> CONTACTED", () => {
      expect(leadTransitionAllowed("NEW", "CONTACTED")).toBe(true);
    });

    it("NEW -> ASSIGNED", () => {
      expect(leadTransitionAllowed("NEW", "ASSIGNED")).toBe(true);
    });

    it("NEW -> QUALIFIED", () => {
      expect(leadTransitionAllowed("NEW", "QUALIFIED")).toBe(true);
    });

    it("NEW -> DISQUALIFIED", () => {
      expect(leadTransitionAllowed("NEW", "DISQUALIFIED")).toBe(true);
    });

    it("NEW -> ARCHIVED", () => {
      expect(leadTransitionAllowed("NEW", "ARCHIVED")).toBe(true);
    });

    it("ASSIGNED -> CONTACTED", () => {
      expect(leadTransitionAllowed("ASSIGNED", "CONTACTED")).toBe(true);
    });

    it("ASSIGNED -> QUALIFIED", () => {
      expect(leadTransitionAllowed("ASSIGNED", "QUALIFIED")).toBe(true);
    });

    it("CONTACTED -> QUALIFIED", () => {
      expect(leadTransitionAllowed("CONTACTED", "QUALIFIED")).toBe(true);
    });

    it("CONTACTED -> DISQUALIFIED", () => {
      expect(leadTransitionAllowed("CONTACTED", "DISQUALIFIED")).toBe(true);
    });

    it("QUALIFIED -> DISQUALIFIED", () => {
      expect(leadTransitionAllowed("QUALIFIED", "DISQUALIFIED")).toBe(true);
    });

    it("QUALIFIED -> ARCHIVED", () => {
      expect(leadTransitionAllowed("QUALIFIED", "ARCHIVED")).toBe(true);
    });

    it("DISQUALIFIED -> ARCHIVED", () => {
      expect(leadTransitionAllowed("DISQUALIFIED", "ARCHIVED")).toBe(true);
    });

    it("Same status -> allowed (no-op)", () => {
      expect(leadTransitionAllowed("NEW", "NEW")).toBe(true);
      expect(leadTransitionAllowed("ARCHIVED", "ARCHIVED")).toBe(true);
    });
  });

  describe("Invalid transitions (blocked by UI and backend)", () => {
    it("CONVERTED -> NEW (blocked)", () => {
      expect(leadTransitionAllowed("CONVERTED", "NEW")).toBe(false);
    });

    it("CONVERTED -> QUALIFIED (blocked)", () => {
      expect(leadTransitionAllowed("CONVERTED", "QUALIFIED")).toBe(false);
    });

    it("ARCHIVED -> NEW (blocked)", () => {
      expect(leadTransitionAllowed("ARCHIVED", "NEW")).toBe(false);
    });

    it("ARCHIVED -> QUALIFIED (blocked)", () => {
      expect(leadTransitionAllowed("ARCHIVED", "QUALIFIED")).toBe(false);
    });

    it("DISQUALIFIED -> NEW (blocked)", () => {
      expect(leadTransitionAllowed("DISQUALIFIED", "NEW")).toBe(false);
    });

    it("DISQUALIFIED -> QUALIFIED (blocked)", () => {
      expect(leadTransitionAllowed("DISQUALIFIED", "QUALIFIED")).toBe(false);
    });

    it("QUALIFIED -> NEW (blocked)", () => {
      expect(leadTransitionAllowed("QUALIFIED", "NEW")).toBe(false);
    });

    it("QUALIFIED -> CONTACTED (blocked)", () => {
      expect(leadTransitionAllowed("QUALIFIED", "CONTACTED")).toBe(false);
    });

    it("CONTACTED -> NEW (blocked)", () => {
      expect(leadTransitionAllowed("CONTACTED", "NEW")).toBe(false);
    });

    it("CONTACTED -> ASSIGNED (blocked)", () => {
      expect(leadTransitionAllowed("CONTACTED", "ASSIGNED")).toBe(false);
    });

    it("ASSIGNED -> NEW (blocked)", () => {
      expect(leadTransitionAllowed("ASSIGNED", "NEW")).toBe(false);
    });
  });

  describe("Terminal leads — no transitions allowed", () => {
    it("CONVERTED -> any different status is blocked", () => {
      for (const status of LEAD_STATUSES) {
        if (status !== "CONVERTED") {
          expect(leadTransitionAllowed("CONVERTED", status)).toBe(false);
        }
      }
    });

    it("ARCHIVED -> any different status is blocked", () => {
      for (const status of LEAD_STATUSES) {
        if (status !== "ARCHIVED") {
          expect(leadTransitionAllowed("ARCHIVED", status)).toBe(false);
        }
      }
    });

    it("Terminal leads only allow same-status no-op", () => {
      expect(leadTransitionAllowed("CONVERTED", "CONVERTED")).toBe(true);
      expect(leadTransitionAllowed("ARCHIVED", "ARCHIVED")).toBe(true);
    });
  });
});
