#!/usr/bin/env python3
"""Workflow Y2 production release orchestrator contract tests."""

import os
import unittest

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ORCHESTRATOR = os.path.join(
    REPO_ROOT,
    ".github",
    "workflows",
    "workflow-y2-production-release-orchestrator.yml",
)


class TestWorkflowY2ProductionOrchestrator(unittest.TestCase):
    def workflow_text(self):
        self.assertTrue(
            os.path.exists(ORCHESTRATOR),
            "Production orchestrator must exist before Workflow Y2 can auto-release after image publication",
        )
        with open(ORCHESTRATOR, "r", encoding="utf-8") as handle:
            return handle.read()

    def test_orchestrator_is_fail_closed_and_dispatches_canonical_release(self):
        workflow = self.workflow_text()

        required_fragments = (
            "workflow_run:",
            "Publish Render Backend Image",
            "types: [completed]",
            "github.event.workflow_run.conclusion == 'success'",
            "github.event.workflow_run.event == 'push'",
            "github.event.workflow_run.head_branch == 'main'",
            "github.event.workflow_run.head_repository.full_name == github.repository",
            "PRODUCTION-RELEASE-AUTHORIZED",
            "refs/heads/main",
            "github.event.workflow_run.head_sha",
            "commits/${HEAD_SHA}/pulls",
            "production-release.yml",
            "commit_sha=$HEAD_SHA",
            "pull_request_number=$PR_NUMBER",
            "rollback_on_failure=true",
            "gh run watch",
        )
        for fragment in required_fragments:
            self.assertIn(fragment, workflow, f"Missing fail-closed orchestrator contract: {fragment}")

        self.assertIn("actions: write", workflow)
        self.assertIn("contents: read", workflow)
        self.assertIn("pull-requests: read", workflow)

        forbidden_fragments = (
            "api.render.com/v1/services",
            "RENDER_API_KEY",
            "RENDER_SERVICE_ID",
            "imageUrl",
            "DATABASE_PASSWORD",
        )
        for fragment in forbidden_fragments:
            self.assertNotIn(
                fragment,
                workflow,
                f"Orchestrator must not bypass canonical production-release workflow: {fragment}",
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
