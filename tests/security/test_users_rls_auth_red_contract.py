"""RED architecture gates for protecting public.users with fail-closed RLS.

These tests intentionally fail on the current baseline. They make unsafe
migration-only changes impossible to mistake for a completed auth fix.
They are source-level tripwires, NOT a substitute for PostgreSQL Direct
runtime contracts or the G1 live certificate.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "apps/sanad-platform/src/main/java/com/sanad/platform"
AUTH = JAVA / "security/service/AuthService.java"
REPO = JAVA / "user/repository/UserRepository.java"
HANDLER = JAVA / "security/rls/TenantRlsConnectionHandler.java"


class UsersRlsAuthenticationRedContract(unittest.TestCase):
    def test_unauthenticated_identity_resolution_must_not_use_unscoped_users_jpa(self):
        auth = AUTH.read_text(encoding="utf-8")
        repo = REPO.read_text(encoding="utf-8")
        # The governed contract: identity resolution goes through the
        # tenant-scoped repository lookups (findByTenantIdAndEmail /
        # findByTenantIdAndUsername). The historical tenantless method name
        # (findAllByEmail) no longer exists in the repository surface; the
        # tripwire now pins the SCOPED lookup as the required path.
        self.assertIn("findByTenantIdAndEmail", repo,
                      "The governed tenant-scoped email lookup must exist.")
        self.assertIn("findByTenantIdAndUsername", repo,
                      "The governed tenant-scoped username lookup must exist.")
        self.assertIn("userRepository.findByTenantIdAndEmail(", auth,
                      "AuthService identity resolution must use the tenant-scoped lookup.")
        self.assertNotIn(
            "userRepository.findAllByEmail(", auth,
            "RED: tenantless email login currently queries RLS-protected users "
            "without an authenticated tenant; replace it with an explicitly "
            "scoped, audited and least-privilege pre-auth identity resolver.",
        )
        self.assertNotIn(
            "userRepository.findAllByUsername(", auth,
            "RED: tenantless username login must use the governed resolver.",
        )

    def test_tenant_rls_connection_must_not_silently_skip_missing_tenant(self):
        handler = HANDLER.read_text(encoding="utf-8")
        self.assertNotIn(
            "if (tenantId == null) {\n            return;",
            handler,
            "RED: missing tenant inside transaction currently passes through "
            "without a database tenant GUC. Explicitly classify privileged "
            "pre-auth paths and fail closed for ordinary tenant data.",
        )


if __name__ == "__main__":
    unittest.main()
