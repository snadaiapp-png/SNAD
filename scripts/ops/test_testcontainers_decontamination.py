import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
POM = ROOT / "apps" / "sanad-platform" / "pom.xml"
CI = ROOT / ".github" / "workflows" / "ci.yml"
POSTGRES_ACCEPTANCE = ROOT / ".github" / "workflows" / "postgres-acceptance.yml"
DEV_SECURITY = ROOT / ".github" / "workflows" / "development-security-acceptance.yml"
JAVA_TEST_ROOT = ROOT / "apps" / "sanad-platform" / "src" / "test" / "java"
CLEAN_ROOM_BRANCH = "infra/backend-clean-room-v1"


class TestcontainersDecontaminationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pom = POM.read_text(encoding="utf-8")
        cls.ci = CI.read_text(encoding="utf-8")
        cls.postgres_acceptance = POSTGRES_ACCEPTANCE.read_text(encoding="utf-8")
        cls.dev_security = DEV_SECURITY.read_text(encoding="utf-8")

    def test_maven_has_no_testcontainers_dependencies(self):
        self.assertNotIn("<testcontainers.version>", self.pom)
        self.assertNotIn("<groupId>org.testcontainers</groupId>", self.pom)
        self.assertNotIn("<artifactId>junit-jupiter</artifactId>", self._testcontainers_blocks())
        self.assertNotIn("<artifactId>postgresql</artifactId>", self._testcontainers_blocks())

    def test_active_java_tests_do_not_create_testcontainers(self):
        forbidden = (
            "import org.testcontainers",
            "@Testcontainers",
            "new PostgreSQLContainer",
            "PostgreSQLContainer<",
            "new GenericContainer",
            "GenericContainer<",
        )
        offenders = []
        for path in sorted(JAVA_TEST_ROOT.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            if any(marker in text for marker in forbidden):
                offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual([], offenders, f"Active Testcontainers Java tests: {offenders}")

    def test_required_ci_jobs_use_postgresql_service_without_testcontainers_controls(self):
        self.assertIn("name: Maven Test Suite", self.ci)
        self.assertIn("name: CRM Integration Tests", self.ci)
        self.assertGreaterEqual(self.ci.count("image: postgres:16-alpine"), 2)
        self.assertGreaterEqual(self.ci.count("SPRING_DATASOURCE_URL"), 2)
        self.assertNotIn("TESTCONTAINERS_", self.ci)
        self.assertNotIn("Verify Docker availability (for Testcontainers)", self.ci)
        self.assertNotIn("/tmp/testcontainers", self.ci)
        self.assertNotIn("testcontainers-logs", self.ci.lower())

    def test_postgresql_acceptance_is_service_backed_not_testcontainers_backed(self):
        text = self.postgres_acceptance
        self.assertIn("name: PostgreSQL Acceptance", text)
        self.assertIn("image: postgres:16-alpine", text)
        self.assertIn("SPRING_DATASOURCE_URL", text)
        self.assertIn("RefreshTokenConcurrencyPostgresTest", text)
        self.assertNotIn("@Testcontainers", text)
        self.assertNotIn("TESTCONTAINERS_", text)
        self.assertNotIn("docker version", text)
        self.assertNotIn("docker info", text)
        self.assertNotIn("PostgreSQL Testcontainers Acceptance", text)
        self.assertNotIn("Run all Testcontainers tests", text)

    def test_development_security_acceptance_provisions_postgresql_without_docker_gate(self):
        text = self.dev_security
        self.assertIn("name: Development Security Acceptance", text)
        self.assertIn("image: postgres:16-alpine", text)
        self.assertIn("SPRING_DATASOURCE_URL", text)
        self.assertIn("RefreshTokenConcurrencyPostgresTest", text)
        self.assertNotIn("name: Verify Docker", text)
        self.assertNotIn("docker version", text)
        self.assertNotIn("docker info", text)

    def test_r11_acceptance_workflows_can_verify_clean_room_branch(self):
        self.assertIn(CLEAN_ROOM_BRANCH, self.postgres_acceptance)
        self.assertIn(CLEAN_ROOM_BRANCH, self.dev_security)

    def test_active_ci_can_certify_disabled_acceptance_workflows(self):
        self.assertIn("name: R11 PostgreSQL Acceptance Certification", self.ci)
        self.assertIn("name: R11 Development Security Certification", self.ci)
        self.assertGreaterEqual(self.ci.count("github.head_ref == 'infra/backend-clean-room-v1'"), 2)
        self.assertGreaterEqual(self.ci.count("RefreshTokenConcurrencyPostgresTest"), 2)

    def _testcontainers_blocks(self):
        blocks = []
        current = []
        in_dependency = False
        for line in self.pom.splitlines():
            if "<dependency>" in line:
                in_dependency = True
                current = [line]
                continue
            if in_dependency:
                current.append(line)
                if "</dependency>" in line:
                    block = "\n".join(current)
                    if "org.testcontainers" in block:
                        blocks.append(block)
                    in_dependency = False
                    current = []
        return "\n".join(blocks)


if __name__ == "__main__":
    unittest.main()
