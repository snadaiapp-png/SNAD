import importlib.util
import pathlib
import sys
import unittest

from test_runtime_clean_room import RuntimeCleanRoomContractTest  # noqa: F401

SCRIPT = pathlib.Path(__file__).with_name("audit_github_workflows.py")
spec = importlib.util.spec_from_file_location("audit_github_workflows", SCRIPT)
audit = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = audit
spec.loader.exec_module(audit)


class AuditTests(unittest.TestCase):
    def test_render_env_writer_is_high_risk(self):
        text = """name: change render
on: workflow_dispatch
steps:
- run: curl -X PUT https://api.render.com/v1/services/$ID/env-vars/FLYWAY_ENABLED
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertTrue(finding.writes_render)
        self.assertTrue(finding.writes_render_env)
        self.assertIn(finding.risk, {"HIGH", "CRITICAL"})

    def test_psql_update_is_database_writer(self):
        text = """name: db fix
on: workflow_dispatch
steps:
- run: psql \"$URL\" -c \"UPDATE users SET status='ACTIVE'\"
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertTrue(finding.writes_database)

    def test_secret_candidate_reports_type_not_value(self):
        secret = "rnd_" + "A" * 30
        text = f"""name: bad
on: workflow_dispatch
steps:
- run: echo {secret}
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertIn("render_api_token_literal", finding.secret_candidate_types)
        self.assertNotIn(secret, repr(finding.to_dict()))

    def test_wrong_password_placeholder_is_not_flagged(self):
        text = """name: smoke
on: workflow_dispatch
steps:
- run: curl -d '{\"password\":\"wrong\"}' https://example.invalid/login
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertNotIn("plaintext_password_literal", finding.secret_candidate_types)

    def test_push_trigger_is_extracted(self):
        text = """name: auto
on:
  push:
    branches: [main]
  workflow_dispatch:
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertIn("push", finding.triggers)
        self.assertIn("workflow_dispatch", finding.triggers)

    def test_github_issues_update_is_not_sql_update(self):
        text = """name: production synthetic
on: workflow_dispatch
steps:
- run: |
    psql \"$URL\" -c \"SELECT 1\"
    node - <<'JS'
    github.rest.issues.update({ owner: 'o', repo: 'r', issue_number: 1, state: 'closed' })
    JS
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertFalse(finding.writes_database)

    def test_render_deploys_get_is_not_render_writer(self):
        text = """name: render read
on: workflow_dispatch
steps:
- run: curl -H \"Authorization: Bearer $RENDER_API_KEY\" https://api.render.com/v1/services/$ID/deploys?limit=20
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertFalse(finding.writes_render)
        self.assertFalse(finding.deploys_image)

    def test_render_env_get_is_not_render_writer(self):
        text = """name: render env read
on: workflow_dispatch
steps:
- run: curl -H \"Authorization: Bearer $RENDER_API_KEY\" https://api.render.com/v1/services/$ID/env-vars?limit=100
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertFalse(finding.writes_render)
        self.assertFalse(finding.writes_render_env)

    def test_flyway_history_verification_is_not_database_writer(self):
        text = """name: verify
on: workflow_dispatch
steps:
- run: |
    # Flyway stores checksum; validate-on-migrate would catch checksum drift.
    psql \"$URL\" -c \"SELECT version, success FROM flyway_schema_history\"
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertTrue(finding.runs_flyway)
        self.assertFalse(finding.writes_database)

    def test_flyway_container_migrate_is_database_writer(self):
        text = """name: migrate
on: workflow_dispatch
steps:
- run: |
    docker run --rm flyway/flyway:10 \\
      -url=\"$DB_URL\" -user=\"$DB_USER\" \\
      migrate
"""
        finding = audit.scan_text(".github/workflows/x.yml", text)
        self.assertTrue(finding.writes_database)

    def test_isolated_ci_database_writer_is_not_production_writer(self):
        text = """name: isolated pg test
on: pull_request
services:
  postgres:
    image: postgres:17
steps:
- run: psql \"$TEST_DATABASE_URL\" -c \"CREATE TABLE probe(id bigint)\"
"""
        finding = audit.scan_text(".github/workflows/isolated-db.yml", text)
        self.assertTrue(finding.is_github_workflow)
        self.assertTrue(finding.writes_database)
        self.assertFalse(finding.is_production_writer)
        self.assertEqual("ISOLATED_CI", finding.writer_authority)

    def test_unknown_production_database_writer_is_fail_closed(self):
        text = """name: legacy prod db
on: workflow_dispatch
jobs:
  mutate:
    environment: Production
    steps:
      - run: psql \"$PRODUCTION_DATABASE_URL\" -c \"UPDATE users SET status='ACTIVE'\"
"""
        finding = audit.scan_text(".github/workflows/legacy-prod-db.yml", text)
        self.assertTrue(finding.is_production_writer)
        self.assertEqual("UNEXPECTED_PRODUCTION", finding.writer_authority)

    def test_canonical_database_migrator_is_allowed_production_writer(self):
        text = """name: canonical migrate
on: workflow_dispatch
jobs:
  migrate:
    environment: Production
    steps:
      - run: ./mvnw flyway:migrate
"""
        finding = audit.scan_text(".github/workflows/database-migrate.yml", text)
        self.assertTrue(finding.is_production_writer)
        self.assertEqual("CANONICAL", finding.writer_authority)

    def test_canonical_render_deployer_is_allowed_production_writer(self):
        text = """name: canonical deploy
on: workflow_dispatch
jobs:
  deploy:
    environment: Production
    steps:
      - run: curl -X POST https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys
"""
        finding = audit.scan_text(".github/workflows/render-deploy.yml", text)
        self.assertTrue(finding.is_production_writer)
        self.assertEqual("CANONICAL", finding.writer_authority)

    def test_summary_separates_production_and_isolated_writers(self):
        isolated = audit.scan_text(
            ".github/workflows/isolated.yml",
            "on: pull_request\nsteps:\n- run: psql '$URL' -c 'CREATE TABLE t(id int)'\n",
        )
        unexpected = audit.scan_text(
            ".github/workflows/legacy.yml",
            "environment: Production\nsteps:\n- run: psql '$URL' -c 'UPDATE t SET id=2'\n",
        )
        summary = audit.summarize([isolated, unexpected])
        self.assertEqual(1, summary["isolated_ci_database_writers"])
        self.assertEqual(1, summary["production_database_writers"])
        self.assertEqual(1, summary["unexpected_production_writers"])


if __name__ == "__main__":
    unittest.main()
