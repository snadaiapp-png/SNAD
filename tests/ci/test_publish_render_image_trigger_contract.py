#!/usr/bin/env python3
"""Release-control contract for immutable backend image publication.

Updated for the daemonless OCI publication path (#1498 / G1 CAB 2026-11-10):
publication is MANUAL + PROTECTED ONLY (workflow_dispatch, Production
environment approval, exact-current-main SHA binding, Buildah/Skopeo, no
Docker). The previous push-triggered automatic publication contract was
deliberately replaced by the fail-closed protected path; a stale copy of
this test asserting the old trigger was left RED on main and is corrected
here to pin the NEW contract.
"""

from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "publish-render-image.yml"


class PublishRenderImageTriggerContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")
        cls.doc = yaml.safe_load(cls.text)

    def test_publication_is_manual_and_protected_only(self):
        triggers = self.doc[True]
        self.assertIn("workflow_dispatch", triggers, "manual dispatch must remain available")
        self.assertNotIn(
            "push",
            triggers,
            "Exact-main production images are published ONLY through the protected manual path "
            "(CAB 2026-11-10: green CI is not publication approval)",
        )
        self.assertNotIn("pull_request", triggers)

    def test_publication_requires_production_environment_approval(self):
        jobs = self.doc["jobs"]
        for job in jobs.values():
            self.assertEqual(
                job.get("environment"),
                "Production",
                "OCI publication must run behind the Production environment reviewer approval",
            )

    def test_publication_binds_exact_current_main(self):
        self.assertIn(
            'test "$current_main" = "$GITHUB_SHA"',
            self.text,
            "publication must fail closed unless dispatched from the exact current main head",
        )

    def test_publication_is_docker_free(self):
        lowered = self.text.lower()
        self.assertNotIn("docker build", lowered)
        self.assertNotIn("docker login", lowered)
        self.assertNotIn("docker push", lowered)
        self.assertNotIn("docker/build-push-action", lowered)
        self.assertIn("buildah", lowered, "daemonless Buildah build required")
        self.assertIn("skopeo", lowered, "registry verification via Skopeo required")


if __name__ == "__main__":
    unittest.main(verbosity=2)
