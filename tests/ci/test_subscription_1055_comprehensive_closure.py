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
        self.assertIn('url.searchParams.set("tenantLogin", "1")', page)
        self.assertNotIn("return defaultDomain.hostname()", service)
        bff = self.read("apps/web/app/api/platform/[...path]/route.ts")
        self.assertIn("TENANT_REFRESH_COOKIE_PREFIX", bff)
        self.assertIn("TENANT_SESSION_HINT_COOKIE_PREFIX", bff)
        self.assertIn("x-sanad-session-scope", bff)
        self.assertIn("x-sanad-session-tenant", bff)
        self.assertIn("Tenant session target mismatch", bff)
        self.assertIn("Tenant session response mismatch", bff)
        auth = self.read("apps/web/lib/api/auth.ts")
        provider = self.read("apps/web/lib/auth/auth-provider.tsx")
        hints = self.read("apps/web/lib/auth/session-hint.ts")
        self.assertIn("createTenantAuthApi", auth)
        self.assertIn("X-SNAD-Session-Tenant", auth)
        self.assertIn("createTenantAuthApi(tenantSessionId)", provider)
        self.assertIn("TENANT_SESSION_HINT_COOKIE_PREFIX", hints)
        self.assertNotIn("sessionScopeRef", provider)
        self.assertNotIn("requestedTenantIdRef", provider)

    def test_discovered_acceptance_fixtures_respect_subscription_authority(self):
        crm_seed = self.read("apps/sanad-platform/src/test/resources/sql/crm-acceptance-subscription-seed.sql")
        playwright = self.read(".github/workflows/playwright-ci.yml")
        crm_workflow = self.read(".github/workflows/crm-authenticated-acceptance.yml")
        workflow_bootstrap = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/workflow/config/WorkflowE2eBootstrapConfig.java")
        commerce_test = self.read("apps/sanad-platform/src/test/java/com/sanad/platform/commerce/CommerceOrderPostgresConcurrencyTest.java")
        self.assertIn("CRM-ACCEPTANCE", crm_seed)
        self.assertIn("crm-acceptance-subscription-seed.sql", playwright)
        self.assertIn("crm-acceptance-subscription-seed.sql", crm_workflow)
        self.assertIn("WF-E2E-BASE-PLAN-B", workflow_bootstrap)
        self.assertIn("intentionally no", workflow_bootstrap)
        self.assertIn("hasExplicitModuleEntitlement", commerce_test)

    def test_ws1_pending_tenant_can_be_governedly_activated(self):
        page = self.read("apps/web/app/executive/tenants/page.tsx")
        self.assertIn('["PENDING", "TRIAL", "PAST_DUE", "SUSPENDED"]', page)
        self.assertIn('targetStatus: "ACTIVE"', page)
        self.assertIn("scp.tenants.activate", page)

    def test_ws3_upgrade_path_can_create_and_provision_subscription(self):
        page = self.read("apps/web/app/executive/subscriptions/page.tsx")
        api = self.read("apps/web/lib/api/executive-api.ts")
        self.assertIn("createSubscriptionForTenant", page)
        self.assertIn("executiveApi.createSubscription", page)
        self.assertIn("await scpApi.provision(created.id)", page)
        self.assertIn('outcome.status !== "SUCCEEDED"', page)
        self.assertIn("createSubscription: (body:", api)
        self.assertIn('intentParam === "upgrade"', page)
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
        admin = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/SaasAdministrationService.java")
        branch = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/subscription/branch/SubscriptionOperatingUnitService.java")
        self.assertIn("reconcilePerBranchQuantity", admin)
        self.assertIn("BRANCHES.CHANGED", admin)
        self.assertIn("reconcilePerBranchQuantity", branch)
        self.assertIn("FOR UPDATE", branch)
        self.assertIn("Serialize all operating-unit mutations", branch)

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
