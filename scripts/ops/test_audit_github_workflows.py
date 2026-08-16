import importlib.util
import pathlib
import sys
import unittest

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


if __name__ == "__main__":
    unittest.main()
