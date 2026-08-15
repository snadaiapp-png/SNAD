package com.sanad.platform.admin;

import com.sanad.platform.admin.api.TenantDomainDtos.CreateDomainRequest;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainResponse;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainSummary;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainType;
import com.sanad.platform.admin.api.TenantDomainDtos.Origin;
import com.sanad.platform.admin.api.TenantDomainDtos.Status;
import com.sanad.platform.admin.api.TenantDomainDtos.UpdateDomainRequest;
import com.sanad.platform.admin.api.TenantDomainDtos.VerificationMethod;
import com.sanad.platform.admin.api.TenantDomainDtos.VerifyDomainRequest;
import com.sanad.platform.admin.service.TenantDomainService;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the Tenant Domain Management capability (V20260815.20+).
 *
 * Verifies the full CRUD + verification lifecycle, the single-primary invariant,
 * tenant isolation, and the {@link TenantDomainService#generateDefaultHostname}
 * derivation logic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class TenantDomainServiceIntegrationTest {

    @Autowired private TenantDomainService domainService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "td-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    void createDomain_persistsWithUnverifiedStatus() {
        var req = new CreateDomainRequest(
                "example.com", DomainType.APPLICATION, Origin.CUSTOM, VerificationMethod.DNS_TXT);
        var created = domainService.createDomain(tenantId, req, null);

        assertThat(created.id()).isNotNull();
        assertThat(created.tenantId()).isEqualTo(tenantId);
        assertThat(created.hostname()).isEqualTo("example.com");
        assertThat(created.domainType()).isEqualTo(DomainType.APPLICATION);
        assertThat(created.origin()).isEqualTo(Origin.CUSTOM);
        assertThat(created.status()).isEqualTo(Status.UNVERIFIED);
        assertThat(created.verificationToken()).isNotBlank();
        assertThat(created.verificationMethod()).isEqualTo(VerificationMethod.DNS_TXT);
        assertThat(created.isPrimary()).isFalse();
    }

    @Test
    void createDomain_normalizesHostnameToLowerCase() {
        var req = new CreateDomainRequest(
                "Example.COM", DomainType.APPLICATION, Origin.CUSTOM, VerificationMethod.DNS_TXT);
        var created = domainService.createDomain(tenantId, req, null);
        assertThat(created.hostname()).isEqualTo("example.com");
    }

    @Test
    void createDomain_rejectsDuplicateHostnameForSameTenant() {
        var req = new CreateDomainRequest(
                "dup.example.com", DomainType.APPLICATION, Origin.CUSTOM, VerificationMethod.DNS_TXT);
        domainService.createDomain(tenantId, req, null);
        assertThatThrownBy(() -> domainService.createDomain(tenantId, req, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("hostname already claimed");
    }

    @Test
    void createDomain_allowsSameHostnameForDifferentTenants() {
        var otherTenant = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Other', ?, 'ACTIVE', ?, ?)",
                otherTenant, "ot-" + otherTenant.toString().substring(0, 8), now, now);

        var req = new CreateDomainRequest(
                "shared.example.com", DomainType.APPLICATION, Origin.CUSTOM, VerificationMethod.DNS_TXT);
        var first = domainService.createDomain(tenantId, req, null);
        var second = domainService.createDomain(otherTenant, req, null);

        assertThat(first.tenantId()).isEqualTo(tenantId);
        assertThat(second.tenantId()).isEqualTo(otherTenant);
    }

    @Test
    void createDomain_rejectsInvalidHostname() {
        var req = new CreateDomainRequest(
                "ex am ple.com", DomainType.APPLICATION, Origin.CUSTOM, VerificationMethod.DNS_TXT);
        assertThatThrownBy(() -> domainService.createDomain(tenantId, req, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verifyDomain_transitionsUnverifiedToVerified() {
        var created = createDomain("verify.example.com");
        var req = new VerifyDomainRequest(created.verificationToken());
        var verified = domainService.verifyDomain(tenantId, created.id(), req, null);

        assertThat(verified.status()).isEqualTo(Status.VERIFIED);
        assertThat(verified.verifiedAt()).isNotNull();
    }

    @Test
    void verifyDomain_rejectsWrongToken() {
        var created = createDomain("wrong-token.example.com");
        var req = new VerifyDomainRequest("snad-verify-wrongtoken1234567890");
        assertThatThrownBy(() -> domainService.verifyDomain(tenantId, created.id(), req, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void activateDomain_requiresVerifiedState() {
        var created = createDomain("not-verified.example.com");
        assertThatThrownBy(() -> domainService.activateDomain(tenantId, created.id(), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);

        // Verify, then activate
        domainService.verifyDomain(tenantId, created.id(),
                new VerifyDomainRequest(created.verificationToken()), null);
        var activated = domainService.activateDomain(tenantId, created.id(), null);
        assertThat(activated.status()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void updateDomain_settingPrimaryDemotesOtherPrimariesOfSameType() {
        var first = createDomain("first.example.com");
        var second = createDomain("second.example.com");

        domainService.updateDomain(tenantId, first.id(),
                new UpdateDomainRequest(null, true), null);
        domainService.updateDomain(tenantId, second.id(),
                new UpdateDomainRequest(null, true), null);

        var firstReloaded = domainService.getDomain(tenantId, first.id());
        var secondReloaded = domainService.getDomain(tenantId, second.id());

        assertThat(firstReloaded.isPrimary()).isFalse();
        assertThat(secondReloaded.isPrimary()).isTrue();
    }

    @Test
    void updateDomain_primaryIndependenceAcrossTypes() {
        var app = createDomain("app.example.com", DomainType.APPLICATION);
        var store = createDomain("store.example.com", DomainType.STORE);

        domainService.updateDomain(tenantId, app.id(),
                new UpdateDomainRequest(null, true), null);
        domainService.updateDomain(tenantId, store.id(),
                new UpdateDomainRequest(null, true), null);

        // Both should be primary of their own type
        assertThat(domainService.getDomain(tenantId, app.id()).isPrimary()).isTrue();
        assertThat(domainService.getDomain(tenantId, store.id()).isPrimary()).isTrue();
    }

    @Test
    void deactivateDomain_clearsPrimaryAndSetsInactive() {
        var created = createDomain("deact.example.com");
        domainService.updateDomain(tenantId, created.id(),
                new UpdateDomainRequest(null, true), null);
        domainService.verifyDomain(tenantId, created.id(),
                new VerifyDomainRequest(created.verificationToken()), null);

        var deactivated = domainService.deactivateDomain(tenantId, created.id(), "test", null);

        assertThat(deactivated.status()).isEqualTo(Status.INACTIVE);
        assertThat(deactivated.isPrimary()).isFalse();
        assertThat(deactivated.failureReason()).isEqualTo("test");
    }

    @Test
    void listDomains_filtersByType() {
        createDomain("a.example.com", DomainType.APPLICATION);
        createDomain("s.example.com", DomainType.STORE);
        createDomain("w.example.com", DomainType.WEBSITE);

        List<DomainResponse> onlyStores = domainService.listDomains(tenantId, DomainType.STORE);
        assertThat(onlyStores).hasSize(1);
        assertThat(onlyStores.get(0).domainType()).isEqualTo(DomainType.STORE);
        assertThat(onlyStores.get(0).hostname()).isEqualTo("s.example.com");

        List<DomainResponse> all = domainService.listDomains(tenantId, null);
        assertThat(all).hasSize(3);
    }

    @Test
    void summarize_reportsCorrectCounts() {
        var d1 = createDomain("d1.example.com");
        var d2 = createDomain("d2.example.com");
        createDomain("d3.example.com");

        // Verify + activate d1, then make it primary
        domainService.verifyDomain(tenantId, d1.id(),
                new VerifyDomainRequest(d1.verificationToken()), null);
        domainService.activateDomain(tenantId, d1.id(), null);
        domainService.updateDomain(tenantId, d1.id(),
                new UpdateDomainRequest(null, true), null);

        // Verify d2 (status=VERIFIED, not ACTIVE)
        domainService.verifyDomain(tenantId, d2.id(),
                new VerifyDomainRequest(d2.verificationToken()), null);

        DomainSummary summary = domainService.summarize(tenantId);

        assertThat(summary.totalCount()).isEqualTo(3);
        assertThat(summary.activeCount()).isEqualTo(1);
        assertThat(summary.verifiedCount()).isEqualTo(2); // VERIFIED + ACTIVE
        assertThat(summary.unverifiedCount()).isEqualTo(1);
        assertThat(summary.inactiveCount()).isEqualTo(0);
        assertThat(summary.hasPrimary()).isTrue();
        assertThat(summary.primaryHostname()).isEqualTo("d1.example.com");
    }

    @Test
    void deleteDomain_removesRow() {
        var created = createDomain("delete.example.com");
        domainService.deleteDomain(tenantId, created.id(), null);
        assertThatThrownBy(() -> domainService.getDomain(tenantId, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void generateDefaultHostname_returnsNullWhenBaseDomainUnset() {
        // No SANAD_BASE_DOMAIN env or system property in tests by default.
        String result = domainService.generateDefaultHostname("acme", DomainType.APPLICATION);
        // Either null (env unset) or a derived value (if env set in CI).
        if (System.getenv("SANAD_BASE_DOMAIN") == null
                && System.getProperty("sanad.tenancy.domains.base-domain") == null) {
            assertThat(result).isNull();
        }
    }

    @Test
    void generateDefaultHostname_producesCorrectShapeWhenBaseDomainSet() {
        // Force-set the system property to verify the derivation logic.
        System.setProperty("sanad.tenancy.domains.base-domain", "snad.example");
        try {
            assertThat(domainService.generateDefaultHostname("acme", DomainType.APPLICATION))
                    .isEqualTo("acme.snad.example");
            assertThat(domainService.generateDefaultHostname("acme", DomainType.STORE))
                    .isEqualTo("store.acme.snad.example");
            assertThat(domainService.generateDefaultHostname("acme", DomainType.WEBSITE))
                    .isEqualTo("www.acme.snad.example");
        } finally {
            System.clearProperty("sanad.tenancy.domains.base-domain");
        }
    }

    private DomainResponse createDomain(String hostname) {
        return createDomain(hostname, DomainType.APPLICATION);
    }

    private DomainResponse createDomain(String hostname, DomainType type) {
        var req = new CreateDomainRequest(
                hostname, type, Origin.CUSTOM, VerificationMethod.DNS_TXT);
        return domainService.createDomain(tenantId, req, null);
    }
}
