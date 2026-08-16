from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
IMAGE_WORKFLOW = ROOT / ".github/workflows/publish-render-image.yml"


class ReleaseWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.image = IMAGE_WORKFLOW.read_text(encoding="utf-8")

    def test_image_builder_has_no_render_or_database_authority(self):
        lowered = self.image.lower()
        self.assertNotIn("api.render.com", lowered)
        self.assertNotIn("render_api_key", lowered)
        self.assertNotIn("render_service_id", lowered)
        self.assertNotRegex(lowered, r"\bflyway:(?:migrate|repair|clean)\b")
        self.assertNotIn("production_database_", lowered)

    def test_image_builder_publishes_exact_source_sha_only(self):
        self.assertIn("${{ github.sha }}", self.image)
        self.assertNotRegex(self.image, r"(?im)^\s*.*:latest\s*$")

    def test_image_builder_requires_build_digest(self):
        self.assertIn("steps.build.outputs.digest", self.image)
        self.assertRegex(self.image, r"test\s+-n\s+.*steps\.build\.outputs\.digest")

    def test_image_builder_uploads_sanitized_json_evidence(self):
        self.assertIn("backend-image-evidence.json", self.image)
        self.assertIn("actions/upload-artifact@v4", self.image)
        for field in ("sourceSha", "imageTag", "digest", "deploymentPerformed"):
            self.assertIn(field, self.image)
        self.assertRegex(self.image, r"deploymentPerformed.*false")

    def test_image_builder_is_not_bound_to_production_environment(self):
        self.assertNotRegex(self.image, r"(?mi)^\s*environment:\s*Production\s*$")


if __name__ == "__main__":
    unittest.main()
