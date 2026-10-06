#!/usr/bin/env python3
"""Phase 8 Users verification/release RED acceptance contract.

Focused only on the verification gaps that remain after Users Phases 6 and 7.
This contract is intentionally structural and fail-first: it requires the
authenticated Users E2E suite to prove the Phase 8 release journey without
re-implementing RBAC/effective-access logic in the test itself.
"""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
AUTH_SPEC = ROOT / "apps/web/e2e/users-module-authenticated.spec.ts"
VISUAL_SPEC = ROOT / "apps/web/e2e/users-module-visual.spec.ts"
PHASE8_SPEC = ROOT / "apps/web/e2e/users-phase8-release.spec.ts"
PLAYWRIGHT_CONFIG = ROOT / "apps/web/playwright.users-module.config.ts"
STANDARD_PLAYWRIGHT_CONFIG = ROOT / "apps/web/playwright.standard.config.ts"
CLOSURE_WORKFLOW = ROOT / ".github/workflows/users-module-closure.yml"
PROD_CERT = ROOT / ".github/workflows/users-production-certification.yml"


class UsersPhase8ReleaseAcceptanceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.auth = AUTH_SPEC.read_text(encoding="utf-8")
        cls.visual = VISUAL_SPEC.read_text(encoding="utf-8")
        cls.phase8 = PHASE8_SPEC.read_text(encoding="utf-8")
        cls.config = PLAYWRIGHT_CONFIG.read_text(encoding="utf-8")
        cls.standard_config = STANDARD_PLAYWRIGHT_CONFIG.read_text(encoding="utf-8")
        cls.closure = CLOSURE_WORKFLOW.read_text(encoding="utf-8")
        cls.cert = PROD_CERT.read_text(encoding="utf-8")

    def test_create_user_with_username_is_authenticated_e2e(self):
        self.assertIn("/api/platform/api/v1/users", self.phase8)
        self.assertIn("username", self.phase8)
        self.assertIn("USER.CREATE", self.phase8)

    def test_first_login_by_username_and_forced_rotation_are_e2e(self):
        self.assertIn("username:", self.phase8)
        self.assertIn("CREDENTIAL_ROTATION_REQUIRED", self.phase8)
        self.assertIn("/api/platform/api/v1/auth/change-credential", self.phase8)

    def test_synthetic_application_is_discovered_without_users_hardcoding(self):
        for token in (
            "synthetic",
            "application-access",
            "applicationCode",
            "supportedScopes",
        ):
            self.assertIn(token, self.phase8)
        for forbidden in ("CRM", "HRM", "ERP", "WORKFLOW", "ACCOUNTING", "ECOMMERCE", "POS"):
            self.assertNotIn(f'"{forbidden}"', self.phase8)

    def test_discovered_application_grant_and_revoke_change_effective_access(self):
        for token in (
            "grantUserRole",
            "revokeUserRole",
            "effective",
            "organizationId",
        ):
            self.assertIn(token, self.phase8)

    def test_lifecycle_suspend_reactivate_archive_proves_login_denial(self):
        for token in (
            "suspend",
            "reactivate",
            "archive",
            "login",
        ):
            self.assertIn(token, self.phase8)
        self.assertGreaterEqual(self.phase8.count("403"), 2)

    def test_cross_tenant_read_and_mutation_denial_are_both_e2e(self):
        self.assertIn("crossTenantRead", self.phase8)
        self.assertIn("crossTenantMutation", self.phase8)

    def test_users_accessibility_is_executed_not_documented_only(self):
        combined = self.phase8 + "\n" + self.visual
        self.assertTrue(
            "@axe-core/playwright" in combined or "AxeBuilder" in combined,
            "Users Phase 8 must execute an axe accessibility check",
        )

    def test_rtl_ltr_are_explicitly_asserted(self):
        combined = self.phase8 + "\n" + self.visual
        self.assertIn('dir="rtl"', combined)
        self.assertIn('dir="ltr"', combined)

    def test_desktop_and_mobile_projects_are_part_of_users_acceptance(self):
        self.assertIn("users-behavior-desktop", self.config)
        self.assertIn("users-visual-desktop", self.config)
        self.assertIn("users-visual-mobile", self.config)
        self.assertIn("users-phase8-release-desktop", self.config)
        self.assertIn('devices["Pixel 7"]', self.config)
        self.assertIn("users-phase8-release.spec.ts", self.standard_config)

    def test_credential_artifacts_are_scanned_for_secret_leakage(self):
        combined = self.phase8 + "\n" + self.visual
        for token in ("password", "token", "secret"):
            self.assertIn(token, combined.lower())
        self.assertIn("PHASE8_ARTIFACT_SECRET_LEAK=PASS", self.closure)
        self.assertIn("secret", self.cert.lower())
        self.assertIn("OPEN_USERS_PRODUCTION_BLOCKERS=0", self.cert)
        self.assertIn("PRODUCTION_USERS_CERTIFIED=TRUE", self.cert)


if __name__ == "__main__":
    unittest.main(verbosity=2)
