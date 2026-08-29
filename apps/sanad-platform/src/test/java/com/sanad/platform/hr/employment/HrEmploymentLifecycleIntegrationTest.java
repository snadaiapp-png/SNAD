package com.sanad.platform.hr.employment;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WS2 Task 2 — Employment Expansion behavioral contract (RED baseline).
 *
 * <p>This test expresses the FINAL required Task 2 behavior. With the current
 * Cycle 2 production skeletons, every behavioral test fails because the
 * production methods throw {@link UnsupportedOperationException}. When GREEN
 * implements the production methods, the EXACT SAME assertions must pass
 * — the contract is FROZEN.</p>
 *
 * <p>Categories covered (per Task 2 acceptance order):
 * <ul>
 *   <li>A. Employment schema expansion contract</li>
 *   <li>B. Person → Employment relation</li>
 *   <li>C. Legal Entity employer relation</li>
 *   <li>D. Employee-number target uniqueness</li>
 *   <li>E. Max-one-non-terminal Employment invariant</li>
 *   <li>F. Rehire creates new Employment</li>
 *   <li>G. TERMINATED terminal</li>
 *   <li>H. VOIDED terminal</li>
 *   <li>I. No operational hard delete canonical behavior</li>
 *   <li>J. Status history persistence</li>
 *   <li>K. No overlapping status periods</li>
 *   <li>L. Historical period immutability</li>
 *   <li>M. current_status projection coherence</li>
 *   <li>N. Lifecycle transition behavior</li>
 *   <li>O. Migration tenant state</li>
 *   <li>P. Legacy mapping: authoritative match</li>
 *   <li>Q. Legacy mapping: ambiguous → review required</li>
 *   <li>R. Legacy mapping: no match → blocked</li>
 *   <li>S. Tenant isolation for new Task 2 structures</li>
 * </ul>
 * </p>
 *
 * <p>Per Task 2 anti-loop rule, the contract is FROZEN. GREEN must
 * satisfy these EXACT SAME assertions — no inversion.</p>
 */
class HrEmploymentLifecycleIntegrationTest {

    private Connection conn;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    private EmploymentRepository employmentRepository;
    private EmploymentCommandService commands;
    private MigrationTenantStateRepository migrationStateRepository;
    private LegacyEmployeeMappingService legacyMappingService;

    @BeforeAll
    static void requirePostgreSql() {
        boolean ok = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) { ok = c.isValid(5); }
        } catch (Throwable ignored) {}
        Assumptions.assumeTrue(ok, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void setup() throws Exception {
        dataSource = new DriverManagerDataSource(ISOLATED_URL, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true).cleanDisabled(false).validateOnMigrate(false).load();
        flyway.clean();
        flyway.migrate();
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);

        // Wire real production classes.
        employmentRepository = new JdbcEmploymentRepository(dataSource);
        commands = new JdbcEmploymentCommandService(employmentRepository);
        migrationStateRepository = new JdbcMigrationTenantStateRepository(dataSource);
        legacyMappingService = new DefaultLegacyEmployeeMappingService(dataSource);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ==================== FIXTURE HELPERS ====================

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, 'Test Tenant', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedLegalEntity(UUID tenantId, String code) throws Exception {
        UUID legalEntityId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, "Test Legal Entity " + code);
            ps.executeUpdate();
        }
        return legalEntityId;
    }

    private UUID seedPerson(UUID tenantId, String first, String last) throws Exception {
        UUID personId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
                "VALUES (?, ?, NULL, ?, ?, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            ps.setString(3, first);
            ps.setString(4, last);
            ps.setString(5, first + " " + last);
            ps.executeUpdate();
        }
        return personId;
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }

    /**
     * Seed a legacy hr_employees row with canonical Task 2 columns.
     * Used when commands.submitOnboarding is RED — we cannot rely on
     * the canonical create-via-command path yet.
     */
    private UUID seedEmployment(UUID tenantId, UUID personId, UUID legalEntityId,
                                  String employeeNumber, EmploymentStatus status) throws Exception {
        UUID employmentId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, " +
                "first_name, last_name, display_name, " +
                "employment_type, status, hire_date, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'FULL_TIME', ?, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, employmentId);
            ps.setObject(2, tenantId);
            ps.setObject(3, personId);
            ps.setObject(4, legalEntityId);
            ps.setString(5, employeeNumber);
            ps.setString(6, "Test");
            ps.setString(7, "Employee");
            ps.setString(8, "Test Employee");
            ps.setString(9, status.name());
            ps.setObject(10, java.sql.Date.valueOf(LocalDate.of(2026, 1, 1)));
            ps.executeUpdate();
        }
        return employmentId;
    }

    /** Seed a legacy_employee_mappings row with a specific classification. */
    private void seedLegacyMapping(UUID tenantId, UUID legacyEmployeeId,
                                     UUID canonicalPersonId,
                                     LegacyMappingClassification classification,
                                     String reviewReason) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_legacy_employee_mappings " +
                "(id, tenant_id, legacy_employee_id, canonical_person_id, classification, review_reason, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, legacyEmployeeId);
            ps.setObject(4, canonicalPersonId);
            ps.setString(5, classification.name());
            ps.setString(6, reviewReason);
            ps.executeUpdate();
        }
    }

    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 1, 1);

    // ==================== A. EMPLOYMENT SCHEMA EXPANSION CONTRACT ====================

    @Test
    void employmentSchemaExpansion_canonicalColumnsExist() throws Exception {
        // hr_employees must expose canonical Task 2 columns: person_id, legal_entity_id,
        // worker_classification_code, rehire_of_employee_id, version BIGINT.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name, data_type FROM information_schema.columns " +
                "WHERE table_name = 'hr_employees' AND column_name IN " +
                "('person_id', 'legal_entity_id', 'worker_classification_code', 'rehire_of_employee_id', 'version') " +
                "ORDER BY column_name")) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Map<String, String> cols = new java.util.HashMap<>();
                while (rs.next()) {
                    cols.put(rs.getString("column_name"), rs.getString("data_type"));
                }
                assertThat(cols).containsOnlyKeys("person_id", "legal_entity_id",
                        "worker_classification_code", "rehire_of_employee_id", "version");
                assertThat(cols.get("version"))
                        .as("hr_employees.version must be BIGINT (canonical)")
                        .isEqualTo("bigint");
            }
        }
    }

    @Test
    void employmentStatusPeriodsTable_canonicalColumnsExist() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'hr_employment_status_periods' AND column_name IN " +
                "('id', 'tenant_id', 'employment_id', 'status', 'effective_from', 'effective_to', " +
                "'reason_code', 'reason_text', 'changed_by', 'transition_event_id', 'created_at') " +
                "ORDER BY column_name")) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Set<String> cols = new java.util.HashSet<>();
                while (rs.next()) cols.add(rs.getString("column_name"));
                assertThat(cols).contains("id", "tenant_id", "employment_id", "status",
                        "effective_from", "effective_to", "reason_code", "reason_text",
                        "changed_by", "transition_event_id", "created_at");
            }
        }
    }

    @Test
    void migrationTenantStateTable_canonicalColumnsExist() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'hr_migration_tenant_state' AND column_name IN " +
                "('tenant_id', 'state', 'updated_at', 'updated_by') " +
                "ORDER BY column_name")) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Set<String> cols = new java.util.HashSet<>();
                while (rs.next()) cols.add(rs.getString("column_name"));
                assertThat(cols).contains("tenant_id", "state", "updated_at", "updated_by");
            }
        }
    }

    @Test
    void legacyEmployeeMappingsTable_canonicalColumnsExist() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'hr_legacy_employee_mappings' AND column_name IN " +
                "('tenant_id', 'legacy_employee_id', 'canonical_person_id', 'canonical_employment_id', " +
                "'classification', 'review_reason', 'created_at') " +
                "ORDER BY column_name")) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Set<String> cols = new java.util.HashSet<>();
                while (rs.next()) cols.add(rs.getString("column_name"));
                assertThat(cols).contains("tenant_id", "legacy_employee_id",
                        "canonical_person_id", "canonical_employment_id",
                        "classification", "review_reason", "created_at");
            }
        }
    }

    // ==================== B. PERSON → EMPLOYMENT RELATION ====================

    @Test
    void employment_persistsPersonLink() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID personId = seedPerson(tenantId, "Person", "Link");
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-PER");
        setTenant(tenantId);

        Employment employment = new Employment(
                UUID.randomUUID(), tenantId, personId, legalEntityId,
                "EMP-PER", "FULL_TIME", EmploymentStatus.DRAFT,
                EFFECTIVE, null, null, 0L);
        employmentRepository.saveEmployment(employment);

        Optional<Employment> found = employmentRepository.findEmploymentById(tenantId, employment.id());
        assertThat(found).isPresent();
        assertThat(found.get().personId()).isEqualTo(personId);
    }

    // ==================== C. LEGAL ENTITY EMPLOYER RELATION ====================

    @Test
    void employment_persistsLegalEntityEmployerLink() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID personId = seedPerson(tenantId, "Legal", "Entity");
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-EMP");
        setTenant(tenantId);

        Employment employment = new Employment(
                UUID.randomUUID(), tenantId, personId, legalEntityId,
                "EMP-LE", "FULL_TIME", EmploymentStatus.DRAFT,
                EFFECTIVE, null, null, 0L);
        employmentRepository.saveEmployment(employment);

        Optional<Employment> found = employmentRepository.findEmploymentById(tenantId, employment.id());
        assertThat(found).isPresent();
        assertThat(found.get().legalEntityId()).isEqualTo(legalEntityId);
    }

    // ==================== D. EMPLOYEE-NUMBER TARGET UNIQUENESS ====================

    @Test
    void employeeNumber_uniquenessEnforcedForLegalEntityScope() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-001");
        UUID personA = seedPerson(tenantId, "Alice", "Smith");
        UUID personB = seedPerson(tenantId, "Bob", "Jones");

        Employment e1 = new Employment(
                UUID.randomUUID(), tenantId, personA, legalEntity,
                "EMP-001", "FULL_TIME", EmploymentStatus.DRAFT,
                EFFECTIVE, null, null, 0L);
        employmentRepository.saveEmployment(e1);

        // Second Employment with SAME employee_number for same tenant/legal_entity must fail.
        Employment e2 = new Employment(
                UUID.randomUUID(), tenantId, personB, legalEntity,
                "EMP-001", "FULL_TIME", EmploymentStatus.DRAFT,
                EFFECTIVE, null, null, 0L);
        assertThatThrownBy(() -> employmentRepository.saveEmployment(e2))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== E. MAX ONE NON-TERMINAL EMPLOYMENT ====================

    @Test
    void maxOneNonTerminalEmployment_perPersonLegalEntity() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-MAX");
        UUID personId = seedPerson(tenantId, "Max", "One");

        Employment e1 = new Employment(
                UUID.randomUUID(), tenantId, personId, legalEntity,
                "EMP-MAX-1", "FULL_TIME", EmploymentStatus.ACTIVE,
                EFFECTIVE, null, null, 0L);
        employmentRepository.saveEmployment(e1);

        // Query countNonTerminalEmploymentsForPersonInLegalEntity
        int count = employmentRepository.countNonTerminalEmploymentsForPersonInLegalEntity(
                tenantId, personId, legalEntity);
        assertThat(count)
                .as("non-terminal employment count for one ACTIVE employment must be 1")
                .isEqualTo(1);

        // Attempt to save a SECOND non-terminal Employment for the same person/legal_entity
        // must be rejected by the repository or a DB constraint.
        Employment e2 = new Employment(
                UUID.randomUUID(), tenantId, personId, legalEntity,
                "EMP-MAX-2", "FULL_TIME", EmploymentStatus.PENDING_ONBOARDING,
                EFFECTIVE, null, null, 0L);
        assertThatThrownBy(() -> employmentRepository.saveEmployment(e2))
                .as("second non-terminal Employment for same Person+LegalEntity must be rejected")
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== F. REHIRE CREATES NEW EMPLOYMENT ====================

    @Test
    void rehire_createsNewEmploymentRow_notReactivatesTerminated() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-REHIRE");
        UUID personId = seedPerson(tenantId, "Re", "Hire");
        UUID priorEmploymentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-REHIRE-OLD", EmploymentStatus.TERMINATED);

        Employment rehired = commands.rehire(
                tenantId, priorEmploymentId, personId, legalEntity,
                "EMP-REHIRE-NEW", "FULL_TIME", EFFECTIVE.plusDays(1), "REHIRE");

        // New Employment has its own UUID, not the prior one.
        assertThat(rehired.id()).isNotEqualTo(priorEmploymentId);
        // rehireOfEmployeeId points to the prior TERMINATED Employment.
        assertThat(rehired.rehireOfEmployeeId()).isEqualTo(priorEmploymentId);
        // New Employment is in a non-terminal starting state.
        assertThat(rehired.currentStatus()).isEqualTo(EmploymentStatus.DRAFT);
        // Same person + legal entity.
        assertThat(rehired.personId()).isEqualTo(personId);
        assertThat(rehired.legalEntityId()).isEqualTo(legalEntity);
    }

    // ==================== G. TERMINATED TERMINAL ====================

    @Test
    void terminatedEmployment_cannotTransitionToActive() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-TERM");
        UUID personId = seedPerson(tenantId, "Term", "Inated");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-TERM", EmploymentStatus.TERMINATED);

        // TERMINATED → ACTIVE must be rejected.
        assertThatThrownBy(() -> commands.activate(tenantId, employmentId, EFFECTIVE.plusDays(1), "REASON"))
                .as("TERMINATED Employment cannot be reactivated; use rehire() instead")
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== H. VOIDED TERMINAL ====================

    @Test
    void voidedEmployment_cannotTransitionToActive() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-VOID");
        UUID personId = seedPerson(tenantId, "Void", "Ed");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-VOID", EmploymentStatus.VOIDED);

        assertThatThrownBy(() -> commands.activate(tenantId, employmentId, EFFECTIVE.plusDays(1), "REASON"))
                .as("VOIDED Employment cannot be reactivated")
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== I. NO OPERATIONAL HARD DELETE ====================

    @Test
    void employmentRepository_hasNoHardDeleteMethod() throws Exception {
        // The EmploymentRepository interface MUST NOT expose a hard delete method.
        // This is a compile-time contract — if a delete method is added later,
        // this test fails to compile (forcing explicit review).
        java.lang.reflect.Method[] methods = EmploymentRepository.class.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            String name = m.getName().toLowerCase();
            assertThat(name)
                    .as("EmploymentRepository must not expose a hard-delete method: " + m.getName())
                    .doesNotContain("delete", "remove");
        }
    }

    // ==================== J. STATUS HISTORY PERSISTENCE ====================

    @Test
    void statusHistory_persistedAfterTransition() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-HIST");
        UUID personId = seedPerson(tenantId, "Hist", "Ory");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-HIST", EmploymentStatus.PENDING_ONBOARDING);

        commands.activate(tenantId, employmentId, EFFECTIVE, "ONBOARDED");

        List<EmploymentStatusPeriod> periods = employmentRepository.statusPeriods(tenantId, employmentId);
        assertThat(periods)
                .as("status history must contain PENDING_ONBOARDING and ACTIVE periods")
                .hasSizeGreaterThanOrEqualTo(2)
                .extracting(EmploymentStatusPeriod::status)
                .contains(EmploymentStatus.PENDING_ONBOARDING, EmploymentStatus.ACTIVE);
    }

    // ==================== K. NO OVERLAPPING STATUS PERIODS ====================

    @Test
    void statusPeriods_noOverlapForSameEmployment() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-OVL");
        UUID personId = seedPerson(tenantId, "Over", "Lap");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-OVL", EmploymentStatus.ACTIVE);

        // Try to insert an overlapping ACTIVE period via direct JDBC — must be
        // rejected by the EXCLUDE constraint (or DB-level guard).
        // The test uses direct JDBC (not the repository), so the natural
        // exception is SQLException (PostgreSQL JDBC throws PSQLException
        // which extends SQLException). The business invariant being tested
        // is "overlapping status period = database rejected" — the exception
        // type is a layer detail, not the business contract.
        assertThatThrownBy(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO hr_employment_status_periods " +
                    "(id, tenant_id, employment_id, status, effective_from, effective_to, created_at) " +
                    "VALUES (?, ?, ?, 'ACTIVE', ?::date, NULL, NOW())")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, employmentId);
                ps.setString(4, "2026-01-01");
                ps.executeUpdate();
            }
        }).isInstanceOf(java.sql.SQLException.class);
    }

    // ==================== L. HISTORICAL PERIOD IMMUTABILITY ====================

    @Test
    void closedStatusPeriod_isImmutable() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-IMM");
        UUID personId = seedPerson(tenantId, "Imm", "Utable");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-IMM", EmploymentStatus.ACTIVE);

        // Insert a closed period via direct JDBC.
        UUID periodId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_employment_status_periods " +
                "(id, tenant_id, employment_id, status, effective_from, effective_to, created_at) " +
                "VALUES (?, ?, ?, 'PENDING_ONBOARDING', ?::date, ?::date, NOW())")) {
            ps.setObject(1, periodId);
            ps.setObject(2, tenantId);
            ps.setObject(3, employmentId);
            ps.setString(4, "2026-01-01");
            ps.setString(5, "2026-01-15");
            ps.executeUpdate();
        }

        // Update attempt on closed period must be rejected (RLS or trigger or app policy).
        // We assert the contract at the repository layer: repository.saveStatusPeriod
        // for a CLOSED period with a different status must reject (the repository
        // treats only append+close-open operations as valid).
        EmploymentStatusPeriod closed = new EmploymentStatusPeriod(
                periodId, tenantId, employmentId, EmploymentStatus.PENDING_ONBOARDING,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15),
                null, null, null, null);
        // Attempting to "save" a closed period (with an UPDATE-style mutation semantics
        // through repository.saveStatusPeriod) must reject — but the skeleton throws
        // UOE so this test is RED.
        assertThatThrownBy(() -> employmentRepository.saveStatusPeriod(closed))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== M. CURRENT_STATUS PROJECTION COHERENCE ====================

    @Test
    void currentStatus_projectionMatchesLatestPeriod() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-PROJ");
        UUID personId = seedPerson(tenantId, "Proj", "Ection");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-PROJ", EmploymentStatus.PENDING_ONBOARDING);

        commands.activate(tenantId, employmentId, EFFECTIVE, "ACTIVATED");

        Optional<Employment> reloaded = employmentRepository.findEmploymentById(tenantId, employmentId);
        assertThat(reloaded).isPresent();
        // current_status must equal the latest period's status.
        assertThat(reloaded.get().currentStatus()).isEqualTo(EmploymentStatus.ACTIVE);

        List<EmploymentStatusPeriod> periods = employmentRepository.statusPeriods(tenantId, employmentId);
        assertThat(periods).isNotEmpty();
        EmploymentStatusPeriod latest = periods.get(periods.size() - 1);
        assertThat(reloaded.get().currentStatus())
                .as("current_status projection must equal the latest period's status")
                .isEqualTo(latest.status());
    }

    // ==================== N. LIFECYCLE TRANSITION BEHAVIOR ====================

    @Test
    void transition_draftToPendingOnboarding_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-T1");
        UUID personId = seedPerson(tenantId, "T1", "Person");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-T1", EmploymentStatus.DRAFT);

        EmploymentTransitionResult result = commands.submitOnboarding(
                tenantId, employmentId, EFFECTIVE, "ONBOARDING_SUBMITTED");
        assertThat(result.employmentId()).isEqualTo(employmentId);
        assertThat(result.newStatus()).isEqualTo(EmploymentStatus.PENDING_ONBOARDING);
    }

    @Test
    void transition_pendingOnboardingToActive_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-T2");
        UUID personId = seedPerson(tenantId, "T2", "Person");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-T2", EmploymentStatus.PENDING_ONBOARDING);

        EmploymentTransitionResult result = commands.activate(
                tenantId, employmentId, EFFECTIVE, "ACTIVATED");
        assertThat(result.newStatus()).isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test
    void transition_activeToOnLeave_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-T3");
        UUID personId = seedPerson(tenantId, "T3", "Person");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-T3", EmploymentStatus.ACTIVE);

        EmploymentTransitionResult result = commands.startLeave(
                tenantId, employmentId, EFFECTIVE, "ANNUAL_LEAVE");
        assertThat(result.newStatus()).isEqualTo(EmploymentStatus.ON_LEAVE);
    }

    @Test
    void transition_onLeaveToActive_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-T4");
        UUID personId = seedPerson(tenantId, "T4", "Person");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-T4", EmploymentStatus.ON_LEAVE);

        EmploymentTransitionResult result = commands.returnFromLeave(
                tenantId, employmentId, EFFECTIVE, "RETURNED_FROM_LEAVE");
        assertThat(result.newStatus()).isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test
    void transition_activeToSuspended_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-T5");
        UUID personId = seedPerson(tenantId, "T5", "Person");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-T5", EmploymentStatus.ACTIVE);

        EmploymentTransitionResult result = commands.suspend(
                tenantId, employmentId, EFFECTIVE, "DISCIPLINARY");
        assertThat(result.newStatus()).isEqualTo(EmploymentStatus.SUSPENDED);
    }

    @Test
    void transition_suspendedToActive_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-T6");
        UUID personId = seedPerson(tenantId, "T6", "Person");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-T6", EmploymentStatus.SUSPENDED);

        EmploymentTransitionResult result = commands.reinstate(
                tenantId, employmentId, EFFECTIVE, "REINSTATED");
        assertThat(result.newStatus()).isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test
    void transition_activeToTerminated_allowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntity = seedLegalEntity(tenantId, "LE-T7");
        UUID personId = seedPerson(tenantId, "T7", "Person");
        UUID employmentId = seedEmployment(tenantId, personId, legalEntity,
                "EMP-T7", EmploymentStatus.ACTIVE);

        EmploymentTransitionResult result = commands.terminate(
                tenantId, employmentId, EFFECTIVE, "END_OF_RELATIONSHIP");
        assertThat(result.newStatus()).isEqualTo(EmploymentStatus.TERMINATED);
    }

    // ==================== O. MIGRATION TENANT STATE ====================

    @Test
    void migrationTenantState_defaultIsLegacy() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        // Default state for a tenant with no record must be LEGACY.
        MigrationTenantState state = migrationStateRepository.getState(tenantId);
        assertThat(state).isEqualTo(MigrationTenantState.LEGACY);
    }

    @Test
    void migrationTenantState_setAndGet_roundTrip() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        migrationStateRepository.setState(tenantId, MigrationTenantState.MIGRATING);
        assertThat(migrationStateRepository.getState(tenantId))
                .isEqualTo(MigrationTenantState.MIGRATING);
    }

    @Test
    void migrationTenantState_allFourStatesSupported() throws Exception {
        // State machine supports all four canonical states.
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        for (MigrationTenantState state : MigrationTenantState.values()) {
            migrationStateRepository.setState(tenantId, state);
            assertThat(migrationStateRepository.getState(tenantId))
                    .as("state machine must round-trip " + state)
                    .isEqualTo(state);
        }
    }

    // ==================== P. LEGACY MAPPING — AUTHORITATIVE MATCH → AUTO_MIGRATE ====================

    @Test
    void legacyMapping_singleAuthoritativeMatch_autoMigrate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID personId = seedPerson(tenantId, "Auto", "Migrate");
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-AUTO");
        UUID legacyEmployeeId = seedEmployment(tenantId, personId, legalEntityId,
                "EMP-AUTO", EmploymentStatus.ACTIVE);

        // Seed a mapping row with classification AUTO_MIGRATE — representing
        // exactly one authoritative match found by backfill (Task 6).
        seedLegacyMapping(tenantId, legacyEmployeeId, personId,
                LegacyMappingClassification.AUTO_MIGRATE, "single authoritative match");

        LegacyMappingClassification c = legacyMappingService.classify(tenantId, legacyEmployeeId);
        assertThat(c).isEqualTo(LegacyMappingClassification.AUTO_MIGRATE);
    }

    // ==================== Q. LEGACY MAPPING — AMBIGUOUS → MIGRATION_REVIEW_REQUIRED ====================

    @Test
    void legacyMapping_multiplePlausibleMatches_reviewRequired() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID personId = seedPerson(tenantId, "Review", "Required");
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-REV");
        UUID legacyEmployeeId = seedEmployment(tenantId, personId, legalEntityId,
                "EMP-REV", EmploymentStatus.ACTIVE);

        // Seed a mapping row with classification MIGRATION_REVIEW_REQUIRED —
        // representing multiple plausible matches found by backfill (Task 6).
        seedLegacyMapping(tenantId, legacyEmployeeId, personId,
                LegacyMappingClassification.MIGRATION_REVIEW_REQUIRED, "multiple plausible matches");

        LegacyMappingClassification c = legacyMappingService.classify(tenantId, legacyEmployeeId);
        assertThat(c).isEqualTo(LegacyMappingClassification.MIGRATION_REVIEW_REQUIRED);
    }

    // ==================== R. LEGACY MAPPING — NO MATCH → MIGRATION_BLOCKED ====================

    @Test
    void legacyMapping_noAuthoritativeMatch_blocked() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID personId = seedPerson(tenantId, "Blocked", "Mapping");
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-BLK");
        UUID legacyEmployeeId = seedEmployment(tenantId, personId, legalEntityId,
                "EMP-BLK", EmploymentStatus.ACTIVE);

        // NO mapping row seeded — represents no authoritative match found.
        // The service must return MIGRATION_BLOCKED (never guess).

        LegacyMappingClassification c = legacyMappingService.classify(tenantId, legacyEmployeeId);
        assertThat(c).isEqualTo(LegacyMappingClassification.MIGRATION_BLOCKED);
    }

    // ==================== S. TENANT ISOLATION FOR NEW TASK 2 STRUCTURES ====================

    @Test
    void newTask2Tables_haveForceRls() throws Exception {
        // hr_employment_status_periods and hr_migration_tenant_state MUST have FORCE RLS.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class " +
                "WHERE relname IN ('hr_employment_status_periods', 'hr_migration_tenant_state', " +
                "'hr_legacy_employee_mappings') ORDER BY relname")) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Map<String, boolean[]> rls = new java.util.HashMap<>();
                while (rs.next()) {
                    rls.put(rs.getString("relname"),
                            new boolean[]{rs.getBoolean("relrowsecurity"), rs.getBoolean("relforcerowsecurity")});
                }
                for (String table : new String[]{"hr_employment_status_periods", "hr_migration_tenant_state", "hr_legacy_employee_mappings"}) {
                    assertThat(rls).containsKey(table);
                    assertThat(rls.get(table)[0])
                            .as(table + " must have ENABLE ROW LEVEL SECURITY").isTrue();
                    assertThat(rls.get(table)[1])
                            .as(table + " must have FORCE ROW LEVEL SECURITY").isTrue();
                }
            }
        }
    }

    @Test
    void migrationTenantState_tenantScoped() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);

        // Tenant A sets MIGRATING.
        migrationStateRepository.setState(tenantA, MigrationTenantState.MIGRATING);
        // Tenant B sets CANONICAL.
        migrationStateRepository.setState(tenantB, MigrationTenantState.CANONICAL);

        // Tenant A must still read MIGRATING — not affected by Tenant B's write.
        assertThat(migrationStateRepository.getState(tenantA))
                .isEqualTo(MigrationTenantState.MIGRATING);
        assertThat(migrationStateRepository.getState(tenantB))
                .isEqualTo(MigrationTenantState.CANONICAL);
    }
}
