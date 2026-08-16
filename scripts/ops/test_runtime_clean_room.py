from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP_PROD = ROOT / "apps/sanad-platform/src/main/resources/application-prod.yml"
DOCKERFILE = ROOT / "apps/sanad-platform/Dockerfile"
DOCKERIGNORE = ROOT / "apps/sanad-platform/.dockerignore"
BLUEPRINT = ROOT / "render.yaml"


class RuntimeCleanRoomContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = APP_PROD.read_text(encoding="utf-8")
        cls.docker = DOCKERFILE.read_text(encoding="utf-8")
        cls.ignore = DOCKERIGNORE.read_text(encoding="utf-8")
        cls.blueprint = BLUEPRINT.read_text(encoding="utf-8")

    def test_runtime_flyway_is_disabled_by_default(self):
        self.assertIn("enabled: ${FLYWAY_ENABLED:false}", self.app)
        self.assertIn("FLYWAY_ENABLED", self.blueprint)
        self.assertRegex(self.blueprint, r"(?s)key:\s*FLYWAY_ENABLED.*?value:\s*[\"']?false")

    def test_render_port_is_the_primary_server_and_management_port(self):
        expected = "${PORT:${SERVER_PORT:8080}}"
        self.assertGreaterEqual(self.app.count(expected), 2)

    def test_hikari_defaults_are_startup_safe(self):
        self.assertIn("maximum-pool-size: ${DATABASE_POOL_MAX:3}", self.app)
        self.assertIn("minimum-idle: ${DATABASE_POOL_MIN:0}", self.app)
        self.assertRegex(self.blueprint, r"(?s)key:\s*DATABASE_POOL_MIN.*?value:\s*[\"']?0")

    def test_health_contract_has_dependency_aware_probes(self):
        self.assertIn("liveness:", self.app)
        self.assertIn("readiness:", self.app)
        self.assertRegex(self.app, r"(?s)readiness:.*?include:.*?readinessState.*?db")
        self.assertIn("healthCheckPath: /actuator/health/readiness", self.blueprint)

    def test_docker_uses_container_aware_heap_and_dynamic_port_healthcheck(self):
        self.assertNotRegex(self.docker, r"(?:^|\s)-Xmx\d")
        self.assertIn("-XX:MaxRAMPercentage=", self.docker)
        self.assertIn("${PORT:-8080}/actuator/health/liveness", self.docker)

    def test_docker_context_excludes_common_secret_material(self):
        for entry in (".env*", "*.pem", "*.key"):
            self.assertIn(entry, self.ignore)

    def test_blueprint_is_frozen_and_not_mutable_latest(self):
        self.assertIn("autoDeployTrigger: off", self.blueprint)
        self.assertNotRegex(self.blueprint, r"(?i)imageUrl:\s*\S*:latest(?:\s|$)")

    def test_free_plan_remains_unchanged_until_authorized(self):
        self.assertRegex(self.blueprint, r"(?m)^\s*plan:\s*free\s*$")


if __name__ == "__main__":
    unittest.main()
