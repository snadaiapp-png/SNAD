import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "database-direct-certification.yml"


class DatabaseDirectCertificationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8") if WORKFLOW.exists() else ""

    def test_certification_is_direct_only_and_protected(self):
        self.assertIn("name: PostgreSQL Direct — Read-Only Certification", self.text)
        self.assertIn("infra/backend-clean-room-v1", self.text)
        self.assertIn("workflow_dispatch", self.text)
        self.assertIn("environment: Production", self.text)
        self.assertIn("PRODUCTION_DATABASE_JDBC_URL", self.text)
        self.assertIn("PRODUCTION_DATABASE_USERNAME", self.text)
        self.assertIn("PRODUCTION_DATABASE_PASSWORD", self.text)
        self.assertIn("db\\.[a-z0-9]+\\.supabase\\.co", self.text)
        self.assertIn(":5432", self.text)
        self.assertIn(".pooler.supabase.com", self.text)
        self.assertIn(":6543", self.text)
        self.assertIn("BLOCKED_NON_DIRECT_DATABASE_ROUTE", self.text)
        self.assertIn("BLOCKED_BY_DIRECT_NETWORK", self.text)

    def test_database_sessions_are_forced_read_only(self):
        self.assertIn("default_transaction_read_only=on", self.text)
        self.assertIn("readOnly=true", self.text)
        self.assertIn("readOnlyMode=always", self.text)
        self.assertIn("SHOW default_transaction_read_only", self.text)
        self.assertIn("READ_ONLY_SESSION_GATE=PASS", self.text)

    def test_flyway_is_info_and_validate_only(self):
        self.assertIn("flyway:info", self.text)
        self.assertIn("flyway:validate", self.text)
        self.assertIn("*:pending", self.text)
        self.assertNotIn("flyway:migrate", self.text)
        self.assertNotIn("flyway:repair", self.text)
        self.assertNotIn("flyway:clean", self.text)
        self.assertNotIn("flyway:baseline", self.text)

    def test_schema_history_is_fingerprinted_before_and_after(self):
        self.assertIn("history_before", self.text)
        self.assertIn("history_after", self.text)
        self.assertIn("flyway_schema_history", self.text)
        self.assertIn("READ_ONLY_HISTORY_PARITY=PASS", self.text)
        self.assertIn("DATABASE_MUTATION_DETECTED", self.text)

    def test_only_sanitized_metadata_evidence_is_persisted(self):
        self.assertIn("database-direct-certification-evidence", self.text)
        self.assertIn("databaseRoute", self.text)
        self.assertIn("POSTGRESQL_DIRECT", self.text)
        self.assertNotIn("RENDER_API_KEY", self.text)
        self.assertNotIn("api.render.com", self.text)


if __name__ == "__main__":
    unittest.main()
