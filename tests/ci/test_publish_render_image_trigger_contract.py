#!/usr/bin/env python3
"""Release-control contract for immutable backend image publication."""

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

    def test_every_main_push_publishes_exact_sha_image(self):
        push = cls_push = self.doc[True]["push"]
        self.assertEqual(cls_push.get("branches"), ["main"])
        self.assertNotIn(
            "paths",
            cls_push,
            "Exact-main production releases require an immutable backend image for every main SHA",
        )

    def test_workflow_keeps_manual_recovery_dispatch(self):
        self.assertIn("workflow_dispatch", self.doc[True])


if __name__ == "__main__":
    unittest.main(verbosity=2)
