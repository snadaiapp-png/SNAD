import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "backend-runtime-certification.yml"


class BackendRuntimeCertificationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8") if WORKFLOW.exists() else ""

    def test_certification_is_isolated_and_postgresql_backed(self):
        self.assertIn("name: Backend Runtime Certification", self.text)
        self.assertIn("infra/backend-clean-room-v1", self.text)
        self.assertIn("postgres:16-alpine", self.text)
        self.assertNotIn("environment: Production", self.text)
        self.assertNotIn("RENDER_API_KEY", self.text)
        self.assertNotIn("PRODUCTION_DATABASE_", self.text)

    def test_runtime_never_owns_flyway(self):
        self.assertIn("flyway:migrate", self.text)
        self.assertIn("FLYWAY_ENABLED: \"false\"", self.text)
        self.assertIn("flyway_schema_history", self.text)
        self.assertIn("RUNTIME_SCHEMA_MUTATION=BLOCKED", self.text)

    def test_image_is_certified_under_current_memory_ceiling(self):
        self.assertIn("docker build", self.text)
        self.assertIn("--memory=512m", self.text)
        self.assertIn("docker stats --no-stream", self.text)
        self.assertIn("--network host", self.text)

    def test_health_and_shutdown_are_verified(self):
        self.assertIn("/actuator/health/liveness", self.text)
        self.assertIn("/actuator/health/readiness", self.text)
        self.assertIn("docker stop --time 30", self.text)
        self.assertIn("backend-runtime-certification-evidence", self.text)

    def test_production_integration_guard_remains_enabled_with_valid_inputs(self):
        self.assertIn("SANAD_SERVICE_AUTH_JWT_SECRET", self.text)
        self.assertIn("SANAD_WORKFLOW_ENGINE_BASE_URL", self.text)
        self.assertIn("SANAD_AI_GATEWAY_BASE_URL", self.text)
        self.assertNotIn("SANAD_PRODUCTION_GUARD_ENABLED=false", self.text)
        self.assertNotIn("-e SANAD_PRODUCTION_GUARD_ENABLED=false", self.text)


if __name__ == "__main__":
    unittest.main()
