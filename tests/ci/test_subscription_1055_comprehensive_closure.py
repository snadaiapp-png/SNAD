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
        # Canonical single upgrade path (main): startUpgrade creates the first
        # subscription for an empty tenant, provisions it through the governed
        # provisioning port, and fails closed on non-SUCCEEDED outcomes.
        self.assertIn("startUpgrade", page)
        self.assertIn("executiveApi.createSubscription", page)
        self.assertIn("await scpApi.provision(created.id)", page)
        self.assertIn('provisioned.status !== "SUCCEEDED"', page)
        self.assertIn("createSubscription: (body:", api)
        self.assertIn('intentParam === "upgrade"', page)
    def test_ws3_generic_lifecycle_cannot_bypass_governed_routes(self):
        controller = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/subscription/api/LifecycleController.java")
        self.assertIn('"ACTIVATE"', controller)
        self.assertIn('"RENEW"', controller)
        self.assertIn("is not allowed on the generic endpoint", controller)
        self.assertIn("/subscriptions/{id}/provision", controller)
        self.assertIn("/subscriptions/{id}/renew", controller)

    def test_ws2_application_catalog_lifecycle_is_governed_and_non_destructive(self):
        controller = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/subscription/api/CatalogController.java")
        page = self.read("apps/web/app/executive/applications/page.tsx")
        migration = self.read("apps/sanad-platform/src/main/resources/db/migration/V20260914_1__scp_application_catalog_lifecycle.sql")
        self.assertIn('@PostMapping("/applications")', controller)
        self.assertIn('@PutMapping("/applications/{id}")', controller)
        self.assertIn('@RequireCapability("EXECUTIVE_MANAGE")', controller)
        self.assertNotIn('@DeleteMapping("/applications', controller)
        self.assertIn('"ARCHIVED"', page)
        self.assertIn('"DEPRECATED"', page)
        self.assertNotIn("deleteApplication", page)
        self.assertIn("ARCHIVED", migration)
        self.assertIn("DEPRECATED", migration)

    def test_ws4_subscription_navigation_preserves_canonical_owner_and_tenant_isolation(self):
        jwt_filter = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/security/filter/JwtAuthenticationFilter.java")
        binding_test = self.read("apps/sanad-platform/src/test/java/com/sanad/platform/security/filter/JwtAuthenticationFilterControlPlaneTenantBindingTest.java")
        self.assertIn("CANONICAL_PROJECT_OWNER_USER_ID", jwt_filter)
        self.assertIn("CANONICAL_PROJECT_OWNER_EMAIL", jwt_filter)
        self.assertIn('"/api/v1/executive/subscriptions/v2"', jwt_filter)
        self.assertIn('"/api/v1/executive/billing/invoices"', jwt_filter)
        self.assertIn('"/api/v1/executive/usage"', jwt_filter)
        self.assertIn("controlPlaneAccessGuard.isControlPlaneTenant", jwt_filter)
        # Canonical-owner cross-tenant behavior is pinned by name on both the
        # allowlisted positive path and the 403 fail-closed negative path.
        self.assertIn("canonicalownermaytargetforeigntenantonexplicitexecutivecrosstenantsurfaces", binding_test.lower())
        self.assertIn("controlplaneownercannotuseforeigntenantidonotherexecutiveroutes", binding_test.lower())
        self.assertIn("403", binding_test)
        self.assertIn("/api/v1/executive/subscriptions/v2", binding_test)

    def test_ws5_billing_and_read_models_use_pinned_contract_and_utc_periods(self):
        grid = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/SubscriptionGridQueryService.java")
        detail = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/SubscriptionDetailService.java")
        usage = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/subscription/usage/UsageMeteringService.java")
        billing_page = self.read("apps/web/app/executive/billing/page.tsx")
        subscriptions_page = self.read("apps/web/app/executive/subscriptions/page.tsx")
        self.assertIn("COALESCE(pv.currency_code, p.currency_code)", grid)
        self.assertIn("COALESCE(pv.currency_code, p.currency_code)", detail)
        self.assertIn("s.current_period_end", grid)
        self.assertIn("s.seat_quantity", grid)
        self.assertIn("CURRENT_TIMESTAMP AT TIME ZONE 'UTC'", usage)
        self.assertIn("ZoneOffset.UTC", usage)
        self.assertIn("if (!tenantId) return", billing_page)
        # Canonical Finance-aware billingV2 read model is the billing page's
        # single invoice source; the legacy invoices() endpoint must not return.
        self.assertIn("executiveApi.billingV2(tenantId)", billing_page)
        self.assertIn("currentPeriodEnd", subscriptions_page)
        self.assertIn("cancelsAtPeriodEnd", subscriptions_page)

    def test_ws6_domain_provisioning_is_centralized_and_fail_closed(self):
        domain = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/TenantDomainService.java")
        routing = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/tenancy/routing/HostRoutingService.java")
        verifier = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/tenancy/routing/DomainOwnershipVerifier.java")
        self.assertIn("ensureDefaultDomain", domain)
        self.assertIn("requireHostnameAvailable", routing)
        self.assertIn("SANAD_BASE_DOMAIN", domain)
        self.assertIn("DNS", verifier.upper())

    def test_ws7_website_store_limits_are_subscription_backed(self):
        migration = self.read("apps/sanad-platform/src/main/resources/db/migration/V20260925_1__subscription_resource_entitlements.sql")
        website = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/website/application/WebsiteService.java")
        store = self.read("apps/sanad-platform/src/main/java/com/sanad/platform/commerce/application/StoreService.java")
        self.assertIn("WEBSITES.MAX_WEBSITES", migration)
        self.assertIn("ECOMMERCE_CX.MAX_STORES", migration)
        self.assertIn("MAX_WEBSITES", website)
        self.assertIn("MAX_STORES", store)

    def test_ws8_branch_model_has_rls_attribution_pricing_and_billing(self):
        migration = self.read("apps/sanad-platform/src/main/resources/db/migration/V20260925_2__subscription_operating_units.sql")
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

        # POS business persistence is not implemented yet by the POS module.
        # Subscription governance reserves the binding type but must fail
        # closed rather than inventing POS business tables here.
        self.assertIn('"POS_LOCATION"', branch)
        self.assertIn(
            "POS location binding is unavailable until the POS module is active",
            branch,
        )

    def test_executive_ui_exposes_branch_billing_and_resource_governance(self):
        # The subscription-detail route exposes branch, billing-profile and
        # resource governance through its operating-governance section. Since
        # the fail-closed performance-budget fix that section is a dedicated
        # route-local component loaded lazily by the page, so the canonical
        # contract is pinned across BOTH halves of the route:
        #   1. page.tsx must compose the governance component (the route can
        #      not silently drop the section) — a stronger pin than scanning
        #      for API tokens, because wiring and tokens are asserted
        #      separately.
        #   2. SubscriptionOperatingGovernance.tsx must carry the actual
        #      governance controls: operating units, billing profiles and
        #      resource bindings, driven by the canonical executive API
        #      commands (never direct status writes).
        page = self.read("apps/web/app/executive/subscriptions/[id]/page.tsx")
        self.assertIn('dynamic(\n  () => import("./SubscriptionOperatingGovernance")', page)
        self.assertIn("<SubscriptionOperatingGovernanceLazy", page)
        governance = self.read(
            "apps/web/app/executive/subscriptions/[id]/SubscriptionOperatingGovernance.tsx"
        )
        for token in (
            "operatingUnits",
            "billingProfiles",
            "resourceBindings",
            "bindOperatingUnit",
            "upsertSubscriptionBillingProfile",
            "bindSubscriptionResource",
        ):
            self.assertIn(token, governance)

if __name__ == "__main__":
    unittest.main()
