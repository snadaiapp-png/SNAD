#!/usr/bin/env python3
"""Regression tests for the static Render control-plane validator."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
RENDER_BLUEPRINT = ROOT / "render.yaml"
CONTROL_PLANE_WORKFLOW = ROOT / ".github" / "workflows" / "control-plane-validation.yml"
CRM_READINESS_WORKFLOW = ROOT / ".github" / "workflows" / "crm-deployment-readiness.yml"


class ControlPlaneValidationRegressionTest(unittest.TestCase):
    def test_current_render_blueprint_is_image_backed(self) -> None:
        render = RENDER_BLUEPRINT.read_text(encoding="utf-8")

        self.assertRegex(render, r"(?m)^    name: sanad-backend$")
        self.assertRegex(render, r"(?m)^    runtime: image$")
        self.assertRegex(
            render,
            r"(?m)^      url: ghcr\.io/snadaiapp-png/snad-backend:latest$",
        )
        self.assertRegex(render, r"(?m)^    healthCheckPath: /actuator/health$")
        self.assertNotRegex(render, r"(?m)^    (?:repo|branch):")

    def test_control_plane_validator_matches_image_backed_contract(self) -> None:
        workflow = CONTROL_PLANE_WORKFLOW.read_text(encoding="utf-8")

        required_assertions = (
            "grep -q '^    name: sanad-backend$' render.yaml",
            "grep -q '^    runtime: image$' render.yaml",
            "grep -q '^      url: ghcr.io/snadaiapp-png/snad-backend:latest$' render.yaml",
            "grep -q '^    healthCheckPath: /actuator/health$' render.yaml",
            "! grep -Eq '^[[:space:]]+(repo|branch):' render.yaml",
        )
        for assertion in required_assertions:
            with self.subTest(assertion=assertion):
                self.assertIn(assertion, workflow)

        stale_git_backed_assertions = (
            "grep -q 'repo: https://github.com/snadaiapp-png/SNAD' render.yaml",
            "grep -q 'branch: main' render.yaml",
            "grep -q 'autoDeployTrigger: \"off\"' render.yaml",
        )
        for assertion in stale_git_backed_assertions:
            with self.subTest(assertion=assertion):
                self.assertNotIn(assertion, workflow)

    def test_required_crm_readiness_runs_for_every_main_pull_request(self) -> None:
        workflow = CRM_READINESS_WORKFLOW.read_text(encoding="utf-8")
        pull_request_block = workflow.split("  pull_request:\n", 1)[1].split("  push:\n", 1)[0]

        self.assertIn("    branches: [main]", pull_request_block)
        self.assertNotIn(
            "    paths:",
            pull_request_block,
            "A required status check must not be path-filtered or GitHub can leave it forever in 'expected'.",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
