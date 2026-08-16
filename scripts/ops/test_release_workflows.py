from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
IMAGE_WORKFLOW = ROOT / ".github/workflows/publish-render-image.yml"
DB_WORKFLOW = ROOT / ".github/workflows/database-migrate.yml"
POM = ROOT / "apps/sanad-platform/pom.xml"


class ReleaseWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.image = IMAGE_WORKFLOW.read_text(encoding="utf-8")
        cls.db = DB_WORKFLOW.read_text(encoding="utf-8") if DB_WORKFLOW.exists() else ""
        cls.pom = POM.read_text(encoding="utf-8")

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

    def test_pom_has_flyway_maven_plugin_with_postgresql_support(self):
        self.assertIn("<artifactId>flyway-maven-plugin</artifactId>", self.pom)
        self.assertIn("<version>${flyway.version}</version>", self.pom)
        plugin_start = self.pom.find("<artifactId>flyway-maven-plugin</artifactId>")
        plugin_region = self.pom[plugin_start:plugin_start + 2200]
        self.assertIn("<artifactId>flyway-database-postgresql</artifactId>", plugin_region)
        self.assertIn("<artifactId>postgresql</artifactId>", plugin_region)
        self.assertIn("<version>${postgresql.version}</version>", plugin_region)

    def test_database_migrator_is_manual_and_production_protected(self):
        self.assertIn("name: Database Migrate — PostgreSQL Direct", self.db)
        self.assertIn("workflow_dispatch:", self.db)
        self.assertNotRegex(self.db, r"(?mi)^\s*push:\s*$")
        self.assertRegex(self.db, r"(?mi)^\s*environment:\s*Production\s*$")
        self.assertIn("target_sha:", self.db)
        self.assertRegex(self.db, r"(?s)target_sha:.*?required:\s*true")
        self.assertIn("ref: ${{ inputs.target_sha }}", self.db)

    def test_database_migrator_requires_direct_supabase_5432(self):
        lowered = self.db.lower()
        self.assertIn("production_database_jdbc_url", lowered)
        self.assertIn("production_database_username", lowered)
        self.assertIn("production_database_password", lowered)
        self.assertIn(".pooler.supabase.com", lowered)
        self.assertIn("6543", lowered)
        self.assertIn("5432", lowered)
        self.assertRegex(lowered, r"db\.[a-z0-9.-]+")
        self.assertIn("blocked_by_direct_network", lowered)

    def test_database_migrator_forbids_legacy_or_destructive_paths(self):
        lowered = self.db.lower()
        self.assertNotIn("docker", lowered)
        self.assertNotIn("flyway:clean", lowered)
        self.assertNotIn("flyway:repair", lowered)
        self.assertNotIn("baselineonmigrate=true", lowered)
        self.assertNotIn("flyway.baselineonmigrate=true", lowered)
        self.assertNotIn("pg_terminate_backend", lowered)

    def test_database_migrator_runs_info_validate_migrate_validate(self):
        self.assertIn("flyway:info", self.db)
        self.assertGreaterEqual(self.db.count("flyway:validate"), 2)
        self.assertIn("flyway:migrate", self.db)
        info = self.db.find("flyway:info")
        first_validate = self.db.find("flyway:validate", info + 1)
        migrate = self.db.find("flyway:migrate", first_validate + 1)
        second_validate = self.db.find("flyway:validate", migrate + 1)
        self.assertTrue(0 <= info < first_validate < migrate < second_validate)

    def test_database_migrator_uploads_sanitized_evidence(self):
        self.assertIn("database-migration-evidence.json", self.db)
        self.assertIn("actions/upload-artifact@v4", self.db)
        for field in ("sourceSha", "databaseRoute", "validationBefore", "migration", "validationAfter"):
            self.assertIn(field, self.db)
        self.assertNotIn("${{ secrets.PRODUCTION_DATABASE_PASSWORD }}", self.db.split("database-migration-evidence.json")[-1])


if __name__ == "__main__":
    unittest.main()
