package com.sanad.platform.audit;

import com.sanad.platform.audit.domain.AuditEvent;
import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.repository.AuditEventRepository;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditHashChainService;
import com.sanad.platform.audit.service.AuditService;
import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
import com.sanad.platform.security.tenant.support.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2.3 — Verifies that the audit event hash is canonical: the
 * hash computed BEFORE persistence (inside {@link AuditService#record})
 * equals the hash computed AFTER the event is reloaded from the database.
 *
 * <p>This guards against subtle canonicalization drift that could break
 * the integrity verification path:</p>
 * <ul>
 *   <li>Field reordering during JPA hydration</li>
 *   <li>Timestamp precision loss across PostgreSQL
 *       {@code TIMESTAMP WITH TIME ZONE} ↔ Java {@code Instant}</li>
 *   <li>Null-coalescing differences between the in-memory entity and the
 *       reloaded entity</li>
 *   <li>Sequence-number type widening ({@code Long} ↔ {@code long})</li>
 * </ul>
 *
 * <p>If any of these occur, the recomputed hash would diverge from the
 * stored {@code event_hash}, and the chain verification would falsely
 * report tampering. This test proves the canonical form is stable
 * across the persist/reload boundary.</p>
 *
 * <p>Stage 05A.1 §13 — Each test establishes a JWT_CLAIM-sourced
 * {@link TenantContext} (the same source the filter chain establishes).</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditHashCanonicalizationIntegrationTest {

    @Autowired private AuditService auditService;
    @Autowired private AuditHashChainService hashChainService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private TenantContextProvider contextProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private TenantTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    /**
     * Establishes a JWT_CLAIM-sourced TenantContext (same source the filter
     * chain establishes) with the verified user/tenant IDs from the fixture.
     */
    private void setJwtClaimContext() {
        String token = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        String jti = claims != null ? claims.getId() : "jti-" + UUID.randomUUID();
        contextProvider.setContext(new TenantContext(
                fixture.tenantAId(), fixture.userAId(), jti, 0L,
                Set.of(), TenantContext.TenantContextSource.JWT_CLAIM,
                "hash-canonical-req-" + UUID.randomUUID()));
    }

    /**
     * Records an audit event, reloads it from the database, recomputes the
     * hash from the reloaded entity, and verifies the recomputed hash
     * matches the stored {@code event_hash}.
     *
     * <p>This proves canonicalization is stable across the persist/reload
     * boundary — the in-memory entity and the reloaded entity produce
     * identical SHA-256 hashes.</p>
     */
    @Test
    @DisplayName("hashBeforePersistence_equalsHashAfterReload: recomputed hash from reloaded event matches stored event_hash")
    void hashBeforePersistence_equalsHashAfterReload() {
        // === Arrange: build the audit context (does not require TenantContext) ===
        String resourceId = "hash-canonical-resource-" + UUID.randomUUID();
        AuditContext ctx = AuditContext.builder(
                        "TEST.HASH.CANONICAL", "TestResource", "CREATE")
                .outcome(AuditOutcome.SUCCESS)
                .resourceId(resourceId)
                .build();

        // === Act: record the event (hash computed INSIDE record()) ===
        setJwtClaimContext();
        AuditEvent recorded;
        try {
            recorded = auditService.record(ctx);
        } finally {
            contextProvider.clear();
        }

        // === Reload the event from the database ===
        // findByTenantIdAndId is tenant-scoped via RLS, so we re-establish
        // the TenantContext for the lookup.
        setJwtClaimContext();
        AuditEvent reloaded;
        try {
            reloaded = auditEventRepository.findByTenantIdAndId(
                    fixture.tenantAId(), recorded.getId())
                    .orElseThrow(() -> new AssertionError(
                            "reloaded audit event must exist for tenant "
                                    + fixture.tenantAId() + " and id " + recorded.getId()));
        } finally {
            contextProvider.clear();
        }

        // === Assert 1: the stored event_hash matches what was computed at record time ===
        assertThat(reloaded.getEventHash())
                .as("stored event_hash must match the hash returned by AuditService.record()")
                .isEqualTo(recorded.getEventHash());

        // === Assert 2: the reloaded event's previousHash matches the recorded previousHash ===
        assertThat(reloaded.getPreviousHash())
                .as("reloaded previousHash must match the recorded previousHash")
                .isEqualTo(recorded.getPreviousHash());

        // === Assert 3: recompute the hash from the RELOADED entity and verify equality ===
        String recomputedFromReloaded = hashChainService.computeEventHash(
                reloaded, reloaded.getPreviousHash());
        assertThat(recomputedFromReloaded)
                .as("hash recomputed from the reloaded entity must equal the stored event_hash "
                        + "(canonicalization is stable across persist/reload boundary)")
                .isEqualTo(reloaded.getEventHash());

        // === Assert 4: recompute the hash from the ORIGINAL (in-memory) entity ===
        // This proves the in-memory and reloaded entities produce the same hash.
        String recomputedFromOriginal = hashChainService.computeEventHash(
                recorded, recorded.getPreviousHash());
        assertThat(recomputedFromOriginal)
                .as("hash recomputed from the in-memory entity must equal the stored event_hash")
                .isEqualTo(reloaded.getEventHash());

        // === Assert 5: both recomputations agree ===
        assertThat(recomputedFromReloaded)
                .as("in-memory and reloaded entity must produce identical hashes")
                .isEqualTo(recomputedFromOriginal);
    }

    /**
     * Records TWO audit events for the same tenant and verifies that the
     * second event's hash chains correctly off the first. This proves
     * canonicalization is stable even when the previousHash is non-genesis.
     */
    @Test
    @DisplayName("hashChainStableAcrossMultipleWrites: two sequential events — second event's hash recomputes correctly off first")
    void hashChainStableAcrossMultipleWrites() {
        // === Build contexts (do not require TenantContext) ===
        AuditContext ctx1 = AuditContext.builder(
                        "TEST.HASH.CHAIN.1", "TestResource", "CREATE")
                .outcome(AuditOutcome.SUCCESS)
                .resourceId("chain-resource-1-" + UUID.randomUUID())
                .build();
        AuditContext ctx2 = AuditContext.builder(
                        "TEST.HASH.CHAIN.2", "TestResource", "UPDATE")
                .outcome(AuditOutcome.SUCCESS)
                .resourceId("chain-resource-2-" + UUID.randomUUID())
                .build();

        // === Record first event ===
        setJwtClaimContext();
        AuditEvent first;
        try {
            first = auditService.record(ctx1);
        } finally {
            contextProvider.clear();
        }

        // === Record second event ===
        setJwtClaimContext();
        AuditEvent second;
        try {
            second = auditService.record(ctx2);
        } finally {
            contextProvider.clear();
        }

        // === Reload both events ===
        setJwtClaimContext();
        AuditEvent reloadedFirst;
        AuditEvent reloadedSecond;
        try {
            reloadedFirst = auditEventRepository.findByTenantIdAndId(
                    fixture.tenantAId(), first.getId())
                    .orElseThrow(() -> new AssertionError("first event must be reloadable"));
            reloadedSecond = auditEventRepository.findByTenantIdAndId(
                    fixture.tenantAId(), second.getId())
                    .orElseThrow(() -> new AssertionError("second event must be reloadable"));
        } finally {
            contextProvider.clear();
        }

        // === Assert: second event's previousHash == first event's eventHash ===
        assertThat(reloadedSecond.getPreviousHash())
                .as("second event's previousHash must equal first event's eventHash (linear chain)")
                .isEqualTo(reloadedFirst.getEventHash());

        // === Assert: recomputing the second event's hash from the reloaded entity matches its stored hash ===
        String recomputedSecond = hashChainService.computeEventHash(
                reloadedSecond, reloadedSecond.getPreviousHash());
        assertThat(recomputedSecond)
                .as("second event's recomputed hash must equal its stored event_hash")
                .isEqualTo(reloadedSecond.getEventHash());

        // === Assert: recomputing the first event's hash from the reloaded entity matches its stored hash ===
        String recomputedFirst = hashChainService.computeEventHash(
                reloadedFirst, reloadedFirst.getPreviousHash());
        assertThat(recomputedFirst)
                .as("first event's recomputed hash must equal its stored event_hash")
                .isEqualTo(reloadedFirst.getEventHash());
    }
}
