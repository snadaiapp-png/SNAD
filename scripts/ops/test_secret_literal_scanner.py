import importlib.util
import pathlib
import sys
import unittest

SCRIPT = pathlib.Path(__file__).with_name("audit_github_workflows.py")
spec = importlib.util.spec_from_file_location("audit_github_workflows_secret_test", SCRIPT)
audit = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = audit
spec.loader.exec_module(audit)


class SecretLiteralScannerTest(unittest.TestCase):
    def test_unquoted_database_password_literal_is_flagged_without_echoing_value(self):
        secret = "ExampleStrongPassword!123"
        text = f"Environment=DATABASE_PASSWORD={secret}\n"
        finding = audit.scan_text("scripts/production/example.service", text)
        self.assertIn("plaintext_password_literal", finding.secret_candidate_types)
        self.assertNotIn(secret, repr(finding.to_dict()))

    def test_secret_reference_is_not_flagged_as_literal(self):
        text = "DATABASE_PASSWORD=${{ secrets.PRODUCTION_DATABASE_PASSWORD }}\n"
        finding = audit.scan_text("scripts/production/example.sh", text)
        self.assertNotIn("plaintext_password_literal", finding.secret_candidate_types)

    def test_python_password_variable_from_environment_is_not_literal(self):
        text = "db_pass = os.environ['PROD_DB_PASSWORD']\nconn(password=db_pass)\n"
        finding = audit.scan_text(".github/workflows/read-only-db-check.yml", text)
        self.assertNotIn("plaintext_password_literal", finding.secret_candidate_types)


if __name__ == "__main__":
    unittest.main()
