#!/usr/bin/env python3
"""Fail-closed source contract for HRM G3 Task 7 authenticated acceptance."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/g3-authenticated-acceptance.yml"
SEED = ROOT / "apps/sanad-platform/src/test/resources/sql/g3-acceptance-seed.sql"
SPEC = ROOT / "apps/web/e2e/g3-authenticated.spec.ts"
G3_CONFIG = ROOT / "apps/web/playwright-g3.config.ts"
GENERIC_CONFIGS = [
    ROOT / "apps/web/playwright.config.ts",
    ROOT / "apps/web/playwright.standard.config.ts",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    seed = SEED.read_text(encoding="utf-8")
    spec = SPEC.read_text(encoding="utf-8")
    g3_config = G3_CONFIG.read_text(encoding="utf-8")

    lower_workflow = workflow.lower()
    require("services:" not in lower_workflow, "Docker/service database paths are forbidden")
    require("org.testcontainers" not in lower_workflow, "Testcontainers runtime dependencies are forbidden")
    require("sudo systemctl start postgresql" in workflow, "host-native PostgreSQL bootstrap is required")
    require("NOBYPASSRLS" in workflow, "acceptance DB role must not bypass RLS")
    require("--sanad.rls.enabled=true" in workflow, "backend must start with RLS enabled")
    require("github.event.pull_request.head.sha || github.sha" in workflow, "workflow must bind checkout to exact candidate SHA")
    require('test "$ACTUAL_SHA" = "$CANDIDATE_SHA"' in workflow, "workflow must verify exact checkout identity")
    require("Verify canonical G3 seed contract" in workflow, "runtime seed contract gate is required")

    for needle in (
        "hr_people",
        "hr_employees",
        "legal_entity_id",
        "worker_classification_code",
        "hire_date",
        "hr_employee_assignments",
        "reports_to_assignment_id",
        "HRM.PERFORMANCE.GOAL.SELF_VIEW",
        "HRM.PERFORMANCE.GOAL.SELF_UPDATE",
        "HRM.PERFORMANCE.GOAL.TEAM_MANAGE",
        "HRM.PERFORMANCE.REVIEW.SELF_VIEW",
        "HRM.PERFORMANCE.REVIEW.SELF_SUBMIT",
        "HRM.PERFORMANCE.REVIEW.TEAM_MANAGE",
    ):
        require(needle in seed, f"seed missing canonical contract token: {needle}")

    require("test.skip" not in spec, "required G3 acceptance cannot soft-skip")
    require("page.evaluate" not in spec and "fetch(" not in spec, "browser acceptance cannot compensate with raw page fetch")
    for needle in (
        'request().method() === "PATCH"',
        "/progress",
        'request().method() === "POST"',
        "/submit",
        "/acknowledge",
        "/performance/goals/team/",
        "/performance/reviews/team",
        "forbidden.status()",
        "toBe(403)",
    ):
        require(needle in spec, f"G3 acceptance spec missing required journey evidence: {needle}")

    require("workers: 1" in g3_config, "stateful G3 journey must use one worker")
    require("retries: 0" in g3_config or "retries: process.env.CI ? 0 : 0" in g3_config, "certification path must use zero retries")

    for config in GENERIC_CONFIGS:
        text = config.read_text(encoding="utf-8")
        require('"**/g3-authenticated.spec.ts"' in text, f"{config.name} must exclude the stateful G3 spec")
        require('"**/g2-authenticated.spec.ts"' in text, f"{config.name} must preserve the G2 exclusion")

    print("G3_AUTHENTICATED_ACCEPTANCE_SOURCE_CONTRACT=PASS")


if __name__ == "__main__":
    main()
