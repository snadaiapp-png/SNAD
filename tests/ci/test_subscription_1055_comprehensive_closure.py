import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]

class Subscription1055ClosureContract(unittest.TestCase):
    def read(self, rel):
        return (ROOT / rel).read_text(encoding="utf-8")

    def test_ws1_tenant_login_is_audited_and_not_left_blank(self):
        page = self.read("apps/web/app/executive/tenants/page.tsx")
        service = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutivePlatformService.java")
        self.assertIn('window.open("about:blank", "_blank")', page)
        self.assertIn("popup.opener = null", page)
        self.assertNotIn('window.open("about:blank", "_blank", "noopener,noreferrer")', page)
        self.assertIn("requires exactly one login-eligible subscription", service)
        self.assertIn("TENANT_LOGIN_LINK_", service)
        self.assertIn("return defaultDomain.hostname()", service)

    def test_ws3_generic_lifecycle_cannot_bypass_governed_routes(self):
        controller = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/subscription/api/LifecycleController.java")
        self.assertIn('"ACTIVATE"', controller)
        self.assertIn('"RENEW"', controller)
        self.assertIn("is not allowed on the generic endpoint", controller)
        self.assertIn("/subscriptions/{id}/provision", controller)
        self.assertIn("/subscriptions/{id}/renew", controller)

    def test_ws6_domain_provisioning_is_centralized_and_fail_closed(self):
        domain = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/TenantDomainService.java")
        routing = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/tenancy/routing/HostRoutingService.java")
        verifier = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/tenancy/routing/DomainOwnershipVerifier.java")
        self.assertIn("ensureDefaultDomain", domain)
        self.assertIn("requireHostnameAvailable", routing)
        self.assertIn("SANAD_BASE_DOMAIN", domain)
        self.assertIn("DNS", verifier.upper())

    def test_ws7_website_store_limits_are_subscription_backed(self):
        migration = self.read("apps/sanad-platform/src/main/resources/db/migration/V20260922_2__subscription_resource_entitlements.sql")
        website = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/website/application/WebsiteService.java")
        store = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/commerce/application/StoreService.java")
        self.assertIn("WEBSITES.MAX_WEBSITES", migration)
        self.assertIn("ECOMMERCE_CX.MAX_STORES", migration)
        self.assertIn("MAX_WEBSITES", website)
        self.assertIn("MAX_STORES", store)

    def test_ws8_branch_model_has_rls_attribution_pricing_and_billing(self):
        migration = self.read("apps/sanad-platform/src/main/resources/db/migration/V20260922_3__subscription_operating_units.sql")
        service = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/subscription/branch/SubscriptionOperatingUnitService.java")
        change = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/subscription/change/SubscriptionChangeService.java")
        for token in (
            "subscription_operating_units",
            "subscription_unit_applications",
            "subscription_billing_profiles",
            "subscription_resource_bindings",
            "usage_operating_unit_aggregates",
            "FORCE ROW LEVEL SECURITY",
        ):
            self.assertIn(token, migration)
        self.assertIn("billingMode", service)
        self.assertIn("PER_BRANCH", change)

    def test_executive_ui_exposes_branch_billing_and_resource_governance(self):
        page = self.read("apps/web/app/executive/subscriptions/[id]/page.tsx")
        for token in (
            "operatingUnits",
            "billingProfiles",
            "resourceBindings",
            "bindOperatingUnit",
            "upsertSubscriptionBillingProfile",
            "bindSubscriptionResource",
        ):
            self.assertIn(token, page)

if __name__ == "__main__":
    unittest.main()
