package com.sanad.platform.audit;

import com.sanad.platform.audit.domain.AuditActorType;
import com.sanad.platform.audit.domain.AuditEvent;
import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditHashChainService;
import com.sanad.platform.audit.service.AuditIntegrityVerificationService;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2 §6 — Verifies that the V26 → V30 migration upgrade path is
 * safe when pre-existing audit rows exist.
 *
 * <p>The hash chain verification service must handle mixed schema versions:
 * <ul>
 *   <li>schema_version = 1 (legacy): event hash does NOT include
 *       {@code sequence_number}. Used for events created before the V27
 *       migration introduced sequence numbers.</li>
 *   <li>schema_version = 2 (current): event hash includes
 *       {@code sequence_number} for linear chain enforcement.</li>
 * </ul>
 *
 * <p>Two scenarios are verified:</p>
 * <ol>
 *   <li>{@code migrationUpgrade_preservesExistingAuditHistory} — a legacy
 *       v1 event inserted directly via the fixture DataSource (simulating
 *       a pre-migration row) is still verifiable after all migrations
 *       (V1–V31) have been applied. The chain head row is initialized
 *       manually to match the legacy event.</li>
 *   <li>{@code migrationUpgrade_newEventsConnectToLegacyChain} — a legacy
 *       v1 event is inserted first, then a new v2 event is written via
 *       {@link AuditService#record}. The new event's {@code previousHash}
 *       must equal the legacy event's {@code eventHash}, and the integrity
 *       verification must report {@code valid=true} across the mixed
 *       v1/v2 chain.</li>
 * </ol>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete tenants
 * (FK RESTRICT from audit_events). Each test uses the fixture's unique
 * tenant IDs (UUID.randomUUID per test invocation), so there is no conflict
 * between tests.</p>
 *
 * <p>All DB writes use {@link PreparedStatement}. Timestamps use
 * {@link Timestamp#from(Instant)}.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditMigrationUpgradeIntegrationTest {

    @Autowired private AuditService auditService;
    @Autowired private AuditHashChainService hashChainService;
    @Autowired private AuditIntegrityVerificationService integrityService;
    @Autowired private TenantContextProvider contextProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    private TenantTestFixture fixture;
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
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
                "migration-upgrade-req-" + UUID.randomUUID()));
    }

    /**
     * Inserts a legacy v1 audit event directly via the fixture DataSource,
     * simulating a pre-V27 migration row. The event hash is computed
     * WITHOUT sequence_number (schema_version=1 semantics).
     *
     * <p>Also inserts the corresponding {@code audit_chain_heads} row with
     * {@code head_sequence=1} and {@code head_hash} = the v1 event hash.</p>
     *
     * @return the v1 event hash (64-char hex)
     */
    private String insertLegacyV1Event(UUID tenantId) throws Exception {
        Instant now = Instant.now();
        UUID eventId = UUID.randomUUID();
        String genesisHash = hashChainService.getGenesisHash();

        // Construct a legacy v1 event. The hash chain service omits
        // sequence_number from the hash when schema_version < 2.
        AuditEvent legacyEvent = new AuditEvent(
                tenantId,
                AuditActorType.USER,
                "TEST.LEGACY.V1",
                "TestResource",
                "MIGRATE",
                AuditOutcome.SUCCESS,
                now,
                now,
                "" // placeholder — set below
        );
        legacyEvent.setSchemaVersion(1);
        legacyEvent.setSequenceNumber(1L);
        legacyEvent.setPreviousHash(genesisHash);
        legacyEvent.setActorUserId(fixture.userAId());

        String v1Hash = hashChainService.computeEventHash(legacyEvent, genesisHash);
        legacyEvent.setEventHash(v1Hash);

        // INSERT the legacy event with schema_version=1.
        String insertEventSql = "INSERT INTO audit_events (id, tenant_id, actor_type, "
                + "actor_user_id, action, resource_type, operation, outcome, "
                + "occurred_at, recorded_at, created_at, previous_hash, event_hash, "
                + "hash_algorithm, schema_version, sequence_number) "
                + "VALUES (?, ?, 'USER', ?, ?, ?, ?, 'SUCCESS', ?, ?, ?, ?, ?, "
                + "'SHA-256', 1, 1)";
        Timestamp nowTs = Timestamp.from(now);
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertEventSql)) {
            ps.setObject(1, eventId);
            ps.setObject(2, tenantId);
            ps.setObject(3, fixture.userAId());
            ps.setString(4, legacyEvent.getAction());
            ps.setString(5, legacyEvent.getResourceType());
            ps.setString(6, legacyEvent.getOperation());
            ps.setTimestamp(7, nowTs);
            ps.setTimestamp(8, nowTs);
            ps.setTimestamp(9, nowTs);
            ps.setString(10, genesisHash);
            ps.setString(11, v1Hash);
            ps.executeUpdate();
        }

        // Insert the chain head row pointing at the legacy event.
        // Use INSERT ON CONFLICT DO NOTHING in case V27 already created a
        // chain head row for this tenant (it does so for tenants that
        // existed at migration time — but fixture tenants are created
        // AFTER migration, so this row should not exist).
        String insertHeadSql = "INSERT INTO audit_chain_heads (tenant_id, "
                + "head_sequence, head_hash, updated_at) "
                + "VALUES (?, 1, ?, NOW()) "
                + "ON CONFLICT (tenant_id) DO UPDATE SET "
                + "head_sequence = EXCLUDED.head_sequence, "
                + "head_hash = EXCLUDED.head_hash, "
                + "updated_at = NOW()";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertHeadSql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, v1Hash);
            ps.executeUpdate();
        }

        return v1Hash;
    }

    /**
     * Reads the stored head_hash from audit_chain_heads for the given tenant.
     */
    private String readStoredHeadHash(UUID tenantId) throws Exception {
        String sql = "SELECT head_hash FROM audit_chain_heads WHERE tenant_id = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("head_hash");
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("migrationUpgrade_preservesExistingAuditHistory: legacy v1 event remains verifiable after V1-V31 migrations")
    void migrationUpgrade_preservesExistingAuditHistory() throws Exception {
        // Insert a legacy v1 event (simulating a pre-migration row).
        String v1Hash = insertLegacyV1Event(fixture.tenantAId());

        // The chain head must point at the v1 hash.
        String storedHeadHash = readStoredHeadHash(fixture.tenantAId());
        assertThat(storedHeadHash)
                .as("audit_chain_heads.head_hash must equal the legacy v1 event hash")
                .isEqualTo(v1Hash);

        // Verify the chain integrity. The verification service walks the
        // events ordered by sequence_number ASC. For the v1 event, it
        // recomputes the hash WITHOUT sequence_number (schema_version=1).
        setJwtClaimContext();
        AuditIntegrityVerificationService.VerificationResult result;
        try {
            result = integrityService.verifyChain(fixture.tenantAId());
        } finally {
            contextProvider.clear();
        }

        assertThat(result.valid())
                .as("integrity must be valid for a legacy v1 event after migration upgrade")
                .isTrue();
        assertThat(result.eventsChecked())
                .as("exactly 1 event must be checked")
                .isEqualTo(1);
        assertThat(result.firstBrokenEventId())
                .as("no broken event should be reported")
                .isNull();
        assertThat(result.calculatedHeadHash())
                .as("calculatedHeadHash must equal the v1 event hash")
                .isEqualTo(v1Hash);
        assertThat(result.storedHeadHash())
                .as("storedHeadHash must equal the v1 event hash")
                .isEqualTo(v1Hash);
        assertThat(result.calculatedHeadHash())
                .as("calculatedHeadHash must equal storedHeadHash")
                .isEqualTo(result.storedHeadHash());

        // Verify the event row itself has schema_version=1 and sequence_number=1.
        String eventSql = "SELECT schema_version, sequence_number, event_hash "
                + "FROM audit_events WHERE tenant_id = ? "
                + "ORDER BY sequence_number ASC LIMIT 1";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(eventSql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("exactly one legacy event must be present")
                        .isTrue();
                assertThat(rs.getInt("schema_version"))
                        .as("legacy event must have schema_version=1")
                        .isEqualTo(1);
                assertThat(rs.getLong("sequence_number"))
                        .as("legacy event must have sequence_number=1")
                        .isEqualTo(1L);
                assertThat(rs.getString("event_hash"))
                        .as("legacy event hash must match the computed v1 hash")
                        .isEqualTo(v1Hash);
            }
        }
    }

    @Test
    @DisplayName("migrationUpgrade_newEventsConnectToLegacyChain: v2 event written after v1 legacy event → chain connects, integrity valid")
    void migrationUpgrade_newEventsConnectToLegacyChain() throws Exception {
        // 1. Insert a legacy v1 event first.
        String v1Hash = insertLegacyV1Event(fixture.tenantAId());

        // 2. Write a new v2 event via AuditService.record(). The service
        //    reads the chain head (head_sequence=1, head_hash=v1Hash),
        //    computes nextSequence=2, previousHash=v1Hash, and writes
        //    a v2 event (schema_version=2 by entity default).
        setJwtClaimContext();
        try {
            AuditContext ctx = AuditContext.builder(
                            "TEST.NEW.V2", "TestResource", "CREATE")
                    .outcome(AuditOutcome.SUCCESS)
                    .resourceId("new-v2-resource-" + UUID.randomUUID())
                    .build();
            auditService.record(ctx);
        } finally {
            contextProvider.clear();
        }

        // 3. Verify exactly 2 events exist, with sequences 1 and 2.
        String seqSql = "SELECT sequence_number, schema_version, previous_hash, event_hash "
                + "FROM audit_events WHERE tenant_id = ? "
                + "ORDER BY sequence_number ASC";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(seqSql)) {
            ps.setObject(1, fixture.tenantAId());
            try (ResultSet rs = ps.executeQuery()) {
                // First event: legacy v1
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("sequence_number")).isEqualTo(1L);
                assertThat(rs.getInt("schema_version"))
                        .as("first event must be legacy v1 (schema_version=1)")
                        .isEqualTo(1);
                String firstHash = rs.getString("event_hash");
                assertThat(firstHash).isEqualTo(v1Hash);

                // Second event: new v2
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("sequence_number")).isEqualTo(2L);
                assertThat(rs.getInt("schema_version"))
                        .as("second event must be new v2 (schema_version=2)")
                        .isEqualTo(2);
                String secondPreviousHash = rs.getString("previous_hash");
                String secondHash = rs.getString("event_hash");

                // The new event's previousHash must equal the legacy v1 event hash.
                assertThat(secondPreviousHash)
                        .as("new v2 event previousHash must equal legacy v1 event hash (chain connects)")
                        .isEqualTo(v1Hash);

                // The new event hash must be a 64-char hex string.
                assertThat(secondHash)
                        .as("new v2 event hash must be 64-char hex")
                        .hasSize(64);

                // No more events.
                assertThat(rs.next())
                        .as("exactly 2 events must be present")
                        .isFalse();
            }
        }

        // 4. Verify the chain head was updated to point at the new v2 event.
        String storedHeadHash = readStoredHeadHash(fixture.tenantAId());
        assertThat(storedHeadHash)
                .as("audit_chain_heads.head_hash must be updated to the v2 event hash")
                .isNotEqualTo(v1Hash)
                .isNotNull();

        // 5. Verify the integrity of the mixed v1/v2 chain.
        setJwtClaimContext();
        AuditIntegrityVerificationService.VerificationResult result;
        try {
            result = integrityService.verifyChain(fixture.tenantAId());
        } finally {
            contextProvider.clear();
        }

        assertThat(result.valid())
                .as("integrity must be valid for a mixed v1/v2 chain after migration upgrade")
                .isTrue();
        assertThat(result.eventsChecked())
                .as("exactly 2 events must be checked")
                .isEqualTo(2);
        assertThat(result.firstBrokenEventId())
                .as("no broken event should be reported")
                .isNull();
        assertThat(result.calculatedHeadHash())
                .as("calculatedHeadHash must equal storedHeadHash")
                .isEqualTo(result.storedHeadHash());
        assertThat(result.calculatedHeadHash())
                .as("calculatedHeadHash must equal the v2 event hash")
                .isEqualTo(storedHeadHash);
    }
}
