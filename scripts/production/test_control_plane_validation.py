#!/usr/bin/env python3
"""Regression tests for the static Render control-plane validator."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
RENDER_BLUEPRINT = ROOT / "render.yaml"
CONTROL_PLANE_WORKFLOW = ROOT / ".github" / "workflows" / "control-plane-validation.yml"


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


if __name__ == "__main__":
    unittest.main(verbosity=2)
