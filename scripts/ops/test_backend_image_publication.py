import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "publish-render-image.yml"


class BackendImagePublicationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8") if WORKFLOW.exists() else ""

    def test_clean_room_branch_can_publish_for_certification(self):
        self.assertIn("infra/backend-clean-room-v1", self.text)
        self.assertIn("packages: write", self.text)

    def test_publication_is_sha_and_digest_only(self):
        self.assertIn("${{ github.sha }}", self.text)
        self.assertIn("steps.build.outputs.digest", self.text)
        self.assertNotIn(":latest", self.text)
        self.assertIn("sha256:", self.text)

    def test_supply_chain_attestations_are_explicit(self):
        self.assertIn("provenance: mode=max", self.text)
        self.assertIn("sbom: true", self.text)

    def test_published_digest_is_pulled_and_revision_verified(self):
        self.assertIn("docker pull", self.text)
        self.assertIn("org.opencontainers.image.revision", self.text)
        self.assertIn("IMMUTABLE_DIGEST_GATE=PASS", self.text)
        self.assertIn("REVISION_LABEL_GATE=PASS", self.text)

    def test_superseded_builds_cancel_only_on_clean_room_branch(self):
        self.assertIn(
            "cancel-in-progress: ${{ github.ref == 'refs/heads/infra/backend-clean-room-v1' }}",
            self.text,
        )
        self.assertIn("group: publish-backend-image-${{ github.ref }}", self.text)

    def test_image_publisher_has_no_production_control_plane_authority(self):
        self.assertNotIn("environment: Production", self.text)
        self.assertNotIn("RENDER_API_KEY", self.text)
        self.assertNotIn("RENDER_SERVICE_ID", self.text)
        self.assertNotIn("flyway:migrate", self.text)
        self.assertNotIn("api.render.com", self.text)


if __name__ == "__main__":
    unittest.main()
