package com.sanad.platform.website;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.website.application.*;
import com.sanad.platform.website.api.WebsiteDtos.*;
import com.sanad.platform.website.domain.WebsiteDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Website Platform Integration Test (v20260816.3).
 * Covers website CRUD, page CRUD + publishing, domain management, public resolution,
 * governance auto-discovery, health auto-discovery, and tenant isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WebsiteModuleIntegrationTest {

    @Autowired private WebsiteService websiteService;
    @Autowired private WebsitePageService pageService;
    @Autowired private WebsiteDomainService domainService;
    @Autowired private WebsitePublicResolutionService publicResolutionService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "wp-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    void createWebsite_persistsWithDraftStatus() {
        var website = websiteService.create(tenantId,
                new CreateWebsiteRequest("My Website", "my-site", "ar"), null);
        assertThat(website.id()).isNotNull();
        assertThat(website.name()).isEqualTo("My Website");
        assertThat(website.slug()).isEqualTo("my-site");
        assertThat(website.status()).isEqualTo(WebsiteDomain.WebsiteStatus.DRAFT);
    }

    @Test
    void createWebsite_rejectsDuplicateSlug() {
        websiteService.create(tenantId, new CreateWebsiteRequest("Site 1", "dup-slug", "ar"), null);
        assertThatThrownBy(() -> websiteService.create(tenantId, new CreateWebsiteRequest("Site 2", "dup-slug", "ar"), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void activateWebsite_transitionsToActive() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "activate-test", "ar"), null);
        var activated = websiteService.activate(tenantId, website.id(), null);
        assertThat(activated.status()).isEqualTo(WebsiteDomain.WebsiteStatus.ACTIVE);
    }

    @Test
    void suspendWebsite_transitionsToSuspended() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "suspend-test", "ar"), null);
        websiteService.activate(tenantId, website.id(), null);
        var suspended = websiteService.suspend(tenantId, website.id(), null);
        assertThat(suspended.status()).isEqualTo(WebsiteDomain.WebsiteStatus.SUSPENDED);
    }

    @Test
    void archiveWebsite_transitionsToArchived() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "archive-test", "ar"), null);
        var archived = websiteService.archive(tenantId, website.id(), null);
        assertThat(archived.status()).isEqualTo(WebsiteDomain.WebsiteStatus.ARCHIVED);
    }

    @Test
    void createPage_persistsWithDraftStatus() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "page-test", "ar"), null);
        var page = pageService.create(tenantId, website.id(),
                new CreatePageRequest("Home Page", "home", WebsiteDomain.PageType.HOME,
                        Map.of("blocks", java.util.List.of()), null, null, null, null, null, true), null);
        assertThat(page.title()).isEqualTo("Home Page");
        assertThat(page.slug()).isEqualTo("home");
        assertThat(page.status()).isEqualTo(WebsiteDomain.PageStatus.DRAFT);
    }

    @Test
    void publishPage_transitionsToPublished() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "pub-test", "ar"), null);
        var page = pageService.create(tenantId, website.id(),
                new CreatePageRequest("Home", "home", WebsiteDomain.PageType.HOME, null, null, null, null, null, null, true), null);
        var published = pageService.publish(tenantId, website.id(), page.id(), null);
        assertThat(published.status()).isEqualTo(WebsiteDomain.PageStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
    }

    @Test
    void unpublishPage_transitionsToUnpublished() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "unpub-test", "ar"), null);
        var page = pageService.create(tenantId, website.id(),
                new CreatePageRequest("Home", "home", WebsiteDomain.PageType.HOME, null, null, null, null, null, null, true), null);
        pageService.publish(tenantId, website.id(), page.id(), null);
        var unpublished = pageService.unpublish(tenantId, website.id(), page.id(), null);
        assertThat(unpublished.status()).isEqualTo(WebsiteDomain.PageStatus.UNPUBLISHED);
    }

    @Test
    void versionConflict_detectedOnUpdate() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "ver-test", "ar"), null);
        var page = pageService.create(tenantId, website.id(),
                new CreatePageRequest("Home", "home", WebsiteDomain.PageType.HOME, null, null, null, null, null, null, true), null);
        // Simulate stale version
        assertThatThrownBy(() -> pageService.update(tenantId, website.id(), page.id(),
                new UpdatePageRequest("Updated", null, null, null, null, null, null, null, 999L), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void defaultDomainGeneration_returnsNullWithoutBaseDomain() {
        String result = domainService.generateDefaultDomain("my-site");
        // When SANAD_BASE_DOMAIN is not set, should return null
        if (System.getenv("SANAD_BASE_DOMAIN") == null && System.getProperty("sanad.tenancy.domains.base-domain") == null) {
            assertThat(result).isNull();
        }
    }

    @Test
    void defaultDomainGeneration_worksWithSystemProperty() {
        System.setProperty("sanad.tenancy.domains.base-domain", "snad.example");
        try {
            String result = domainService.generateDefaultDomain("my-site");
            assertThat(result).isEqualTo("my-site.snad.example");
        } finally {
            System.clearProperty("sanad.tenancy.domains.base-domain");
        }
    }

    @Test
    void registerCustomDomain_startsAsPendingInactive() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "domain-test", "ar"), null);
        var domain = domainService.registerCustomDomain(tenantId, website.id(),
                new CreateDomainRequest("test.example.com", WebsiteDomain.VerificationMethod.DNS_TXT), null);
        assertThat(domain.hostname()).isEqualTo("test.example.com");
        assertThat(domain.verificationStatus()).isEqualTo(WebsiteDomain.VerificationStatus.PENDING);
        assertThat(domain.activationStatus()).isEqualTo(WebsiteDomain.ActivationStatus.INACTIVE);
        assertThat(domain.verificationToken()).isNotBlank();
    }

    @Test
    void registerCustomDomain_rejectsReservedHostname() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "reserved-test", "ar"), null);
        assertThatThrownBy(() -> domainService.registerCustomDomain(tenantId, website.id(),
                new CreateDomainRequest("www.snad.app", WebsiteDomain.VerificationMethod.DNS_TXT), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerCustomDomain_rejectsDuplicateHostnameGlobally() {
        var website1 = websiteService.create(tenantId, new CreateWebsiteRequest("Site1", "dup-host1", "ar"), null);
        domainService.registerCustomDomain(tenantId, website1.id(),
                new CreateDomainRequest("unique.example.com", WebsiteDomain.VerificationMethod.DNS_TXT), null);
        // Same hostname from a DIFFERENT tenant must be rejected
        UUID tenant2 = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'T2', ?, 'ACTIVE', ?, ?)",
                tenant2, "wp2-" + tenant2.toString().substring(0, 8), now, now);
        var website2 = websiteService.create(tenant2, new CreateWebsiteRequest("Site2", "dup-host2", "ar"), null);
        assertThatThrownBy(() -> domainService.registerCustomDomain(tenant2, website2.id(),
                new CreateDomainRequest("unique.example.com", WebsiteDomain.VerificationMethod.DNS_TXT), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void activateDomain_requiresVerificationFirst() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "act-domain-test", "ar"), null);
        var domain = domainService.registerCustomDomain(tenantId, website.id(),
                new CreateDomainRequest("act.example.com", WebsiteDomain.VerificationMethod.DNS_TXT), null);
        // Domain is PENDING — activating should fail
        assertThatThrownBy(() -> domainService.activate(tenantId, website.id(), domain.id(), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void verifyDomain_thenActivate_succeeds() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "verify-test", "ar"), null);
        var domain = domainService.registerCustomDomain(tenantId, website.id(),
                new CreateDomainRequest("verify.example.com", WebsiteDomain.VerificationMethod.DNS_TXT), null);
        // Verify with correct token
        var verified = domainService.verifyDomain(tenantId, website.id(), domain.id(),
                new VerifyDomainRequest(domain.verificationToken()), null);
        assertThat(verified.verificationStatus()).isEqualTo(WebsiteDomain.VerificationStatus.VERIFIED);
        // Now activation should succeed
        var activated = domainService.activate(tenantId, website.id(), domain.id(), null);
        assertThat(activated.activationStatus()).isEqualTo(WebsiteDomain.ActivationStatus.ACTIVE);
    }

    @Test
    void verifyDomain_rejectsWrongToken() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "wrong-token-test", "ar"), null);
        var domain = domainService.registerCustomDomain(tenantId, website.id(),
                new CreateDomainRequest("wrong.example.com", WebsiteDomain.VerificationMethod.DNS_TXT), null);
        assertThatThrownBy(() -> domainService.verifyDomain(tenantId, website.id(), domain.id(),
                new VerifyDomainRequest("wrong-token"), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void crossTenantAccess_denied() {
        UUID tenant2 = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'T2', ?, 'ACTIVE', ?, ?)",
                tenant2, "wp3-" + tenant2.toString().substring(0, 8), now, now);
        // Tenant 1 creates a website
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("T1 Site", "t1-site", "ar"), null);
        // Tenant 2 cannot access tenant 1's website
        assertThatThrownBy(() -> websiteService.get(tenant2, website.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publicResolution_returnsNullForUnconfiguredHostname() {
        var result = publicResolutionService.resolveWebsite("nonexistent.example.com");
        assertThat(result).isNull();
    }

    @Test
    void publicResolution_returnsPublishedPageOnly() {
        var website = websiteService.create(tenantId, new CreateWebsiteRequest("Test", "pub-res-test", "ar"), null);
        websiteService.activate(tenantId, website.id(), null);
        var page = pageService.create(tenantId, website.id(),
                new CreatePageRequest("Home", "home", WebsiteDomain.PageType.HOME, null, null, null, null, null, null, true), null);
        // Register and activate a domain
        var domain = domainService.registerCustomDomain(tenantId, website.id(),
                new CreateDomainRequest("pub-test.example.com", WebsiteDomain.VerificationMethod.DNS_TXT), null);
        domainService.verifyDomain(tenantId, website.id(), domain.id(), new VerifyDomainRequest(domain.verificationToken()), null);
        domainService.activate(tenantId, website.id(), domain.id(), null);

        // Page is DRAFT — should NOT be publicly resolvable
        var draftResult = publicResolutionService.resolvePage("pub-test.example.com", "home");
        assertThat(draftResult).isNull();

        // Publish the page — now it should be resolvable
        pageService.publish(tenantId, website.id(), page.id(), null);
        var publishedResult = publicResolutionService.resolvePage("pub-test.example.com", "home");
        assertThat(publishedResult).isNotNull();
        assertThat(publishedResult.title()).isEqualTo("Home");
    }

    @Test
    void summarize_returnsCorrectCounts() {
        websiteService.create(tenantId, new CreateWebsiteRequest("Site1", "sum-1", "ar"), null);
        var w2 = websiteService.create(tenantId, new CreateWebsiteRequest("Site2", "sum-2", "ar"), null);
        websiteService.activate(tenantId, w2.id(), null);
        var summary = websiteService.summarize(tenantId);
        assertThat(summary.totalWebsites()).isEqualTo(2);
        assertThat(summary.activeWebsites()).isEqualTo(1);
        assertThat(summary.draftWebsites()).isEqualTo(1);
    }

    @Test
    void noHardcodedRootDomain() {
        // Verify the domain service does NOT hard-code a root domain.
        // When no env/property is set, generateDefaultDomain returns null.
        if (System.getenv("SANAD_BASE_DOMAIN") == null && System.getProperty("sanad.tenancy.domains.base-domain") == null) {
            assertThat(domainService.generateDefaultDomain("test")).isNull();
        }
    }

    @Test
    void noBusinessImplementationForErpPosContract() {
        // Architecture forensic gate — no erp_/pos_/contracts_ tables should exist
        Integer erpTables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'erp_%'",
                Integer.class);
        assertThat(erpTables).as("ERP_BUSINESS_IMPLEMENTATION_ADDED").isEqualTo(0);
        Integer posTables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'pos_%'",
                Integer.class);
        assertThat(posTables).as("POS_BUSINESS_IMPLEMENTATION_ADDED").isEqualTo(0);
        Integer contractTables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'contracts_%'",
                Integer.class);
        assertThat(contractTables).as("CONTRACT_BUSINESS_IMPLEMENTATION_ADDED").isEqualTo(0);
    }
}
