package com.sanad.platform.hr.compliance;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / Master Task 4 / WS3 Task 3 RED contract — ComplianceEngine with safe
 * Global Mode semantics. PostgreSQL Direct only.
 *
 * <p>The engine, its decision/rule domain types, the rule-handler contract and the
 * decision provenance repository do not exist yet. Following the repository's
 * clean-RED convention (see HrCountryPolicyResolverTest), this test compiles without
 * production classes and uses reflection so a RED run fails only because Task 3
 * behavior is missing — never because of a compilation error.</p>
 */
class HrComplianceEngineTest {

    private static final String APP_PACKAGE = "com.sanad.platform.hr.compliance";

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) {
                available = c.isValid(5);
            }
        } catch (Throwable ignored) {
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateFreshDatabase() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        connection = ds.getConnection();
        connection.setAutoCommit(true);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    // ==================== DECISION MATRIX ====================

    @Test
    void localizedGenericHrWithoutViolationIsCompliant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "SA", date.minusMonths(1), null);
        seedActivePack("SA", "SA-HR-FOUNDATION", "1", date.minusDays(1), null);

        Object decision = evaluate(tenantId, employmentId, "HRM.EMPLOYEE.VIEW",
                operationType("GENERIC_HR"), date, null, List.of());
        assertThat(decisionType(decision)).isEqualTo("COMPLIANT");
    }

    @Test
    void globalModeGenericHrIsAllowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        registerCountry("EG");
        seedJurisdiction(tenantId, employmentId, "EG", date.minusMonths(1), null);

        Object decision = evaluate(tenantId, employmentId, "HRM.EMPLOYEE.VIEW",
                operationType("GENERIC_HR"), date, null, List.of());
        assertThat(decisionType(decision)).isEqualTo("GLOBAL_MODE_ALLOWED");
    }

    @Test
    void globalModeStatutoryOperationFailsClosed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        registerCountry("EG");
        seedJurisdiction(tenantId, employmentId, "EG", date.minusMonths(1), null);

        Object decision = evaluate(tenantId, employmentId, "HRM.STATUTORY.LOCAL_ACTION",
                operationType("LOCAL_STATUTORY"), date, null, List.of());
        assertThat(decisionType(decision)).isEqualTo("LEGAL_REVIEW_REQUIRED");
    }

    @Test
    void localizedMandatoryHardViolationIsBlocked() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "SA", date.minusMonths(1), null);
        UUID packId = seedActivePack("SA", "SA-HR-FOUNDATION", "1", date.minusDays(1), null);
        seedRule(packId, "HARD_RULE", "HRM.STATUTORY.LOCAL_ACTION", "MANDATORY_HARD", false, date.minusDays(1), null);

        Object decision = evaluate(tenantId, employmentId, "HRM.STATUTORY.LOCAL_ACTION",
                operationType("LOCAL_STATUTORY"), date, null, List.of(violatingHandler("HRM.STATUTORY.LOCAL_ACTION")));
        assertThat(decisionType(decision)).isEqualTo("BLOCKED");
    }

    @Test
    void localizedMandatoryWithExceptionViolationRequiresControlledException() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "SA", date.minusMonths(1), null);
        UUID packId = seedActivePack("SA", "SA-HR-FOUNDATION", "1", date.minusDays(1), null);
        seedRule(packId, "EXCEPTION_RULE", "HRM.STATUTORY.LOCAL_ACTION", "MANDATORY_WITH_EXCEPTION", true,
                date.minusDays(1), null);

        Object decision = evaluate(tenantId, employmentId, "HRM.STATUTORY.LOCAL_ACTION",
                operationType("LOCAL_STATUTORY"), date, null, List.of(violatingHandler("HRM.STATUTORY.LOCAL_ACTION")));
        assertThat(decisionType(decision)).isEqualTo("CONTROLLED_EXCEPTION_REQUIRED");
    }

    @Test
    void localizedGuidanceViolationIsCompliantWithWarningMetadata() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "SA", date.minusMonths(1), null);
        UUID packId = seedActivePack("SA", "SA-HR-FOUNDATION", "1", date.minusDays(1), null);
        seedRule(packId, "GUIDANCE_RULE", "HRM.STATUTORY.LOCAL_ACTION", "REGULATORY_GUIDANCE", false,
                date.minusDays(1), null);

        Object decision = evaluate(tenantId, employmentId, "HRM.STATUTORY.LOCAL_ACTION",
                operationType("LOCAL_STATUTORY"), date, null, List.of(violatingHandler("HRM.STATUTORY.LOCAL_ACTION")));
        assertThat(decisionType(decision)).isEqualTo("COMPLIANT");
        assertThat(decisionWarnings(decision)).isNotEmpty();
    }

    @Test
    void statutoryOperationWithMissingRuleOrHandlerFailsClosed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "SA", date.minusMonths(1), null);
        seedActivePack("SA", "SA-HR-FOUNDATION", "1", date.minusDays(1), null);

        // No rule and no handler registered for the statutory operation.
        Object decision = evaluate(tenantId, employmentId, "HRM.STATUTORY.UNMAPPED",
                operationType("LOCAL_STATUTORY"), date, null, List.of());
        assertThat(decisionType(decision)).isEqualTo("LEGAL_REVIEW_REQUIRED");
    }

    @Test
    void missingEffectiveEmploymentJurisdictionFailsClosed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        seedEmploymentJurisdictionless(tenantId);

        assertThatThrownBy(() -> evaluate(tenantId, employmentId, "HRM.EMPLOYEE.VIEW",
                operationType("GENERIC_HR"), LocalDate.of(2026, 9, 3), null, List.of()))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("HRM_LEGAL_REVIEW_REQUIRED");
    }

    @Test
    void draftPackCannotAuthorizeLocalizedStatutoryBehavior() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        registerCountry("EG");
        seedJurisdiction(tenantId, employmentId, "EG", date.minusMonths(1), null);
        seedDraftPack("EG", "EG-HR-FOUNDATION", "1", date.minusDays(1), null);

        Object decision = evaluate(tenantId, employmentId, "HRM.STATUTORY.LOCAL_ACTION",
                operationType("LOCAL_STATUTORY"), date, null, List.of());
        assertThat(decisionType(decision)).isEqualTo("LEGAL_REVIEW_REQUIRED");
    }

    @Test
    void expiredAndNotYetEffectiveRulesAreNotSelected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "SA", date.minusMonths(1), null);
        UUID packId = seedActivePack("SA", "SA-HR-FOUNDATION", "1", date.minusDays(1), null);
        seedRule(packId, "EXPIRED_RULE", "HRM.STATUTORY.LOCAL_ACTION", "MANDATORY_HARD", false,
                date.minusMonths(6), date.minusMonths(3));
        seedRule(packId, "FUTURE_RULE", "HRM.STATUTORY.LOCAL_ACTION", "MANDATORY_HARD", false,
                date.plusMonths(1), null);

        // Neither rule is effective on the decision date: statutory must fail closed.
        Object decision = evaluate(tenantId, employmentId, "HRM.STATUTORY.LOCAL_ACTION",
                operationType("LOCAL_STATUTORY"), date, null, List.of(violatingHandler("HRM.STATUTORY.LOCAL_ACTION")));
        assertThat(decisionType(decision)).isEqualTo("LEGAL_REVIEW_REQUIRED");
    }

    @Test
    void wrongCountryPackRuleIsNeverSelected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "SA", date.minusMonths(1), null);
        seedActivePack("SA", "SA-HR-FOUNDATION", "1", date.minusDays(1), null);
        registerCountry("AE");
        UUID aePackId = seedActivePack("AE", "AE-HR-FOUNDATION", "1", date.minusDays(1), null);
        seedRule(aePackId, "AE_RULE", "HRM.STATUTORY.LOCAL_ACTION", "MANDATORY_HARD", false,
                date.minusDays(1), null);

        Object decision = evaluate(tenantId, employmentId, "HRM.STATUTORY.LOCAL_ACTION",
                operationType("LOCAL_STATUTORY"), date, null, List.of(violatingHandler("HRM.STATUTORY.LOCAL_ACTION")));
        assertThat(decisionType(decision)).isEqualTo("LEGAL_REVIEW_REQUIRED");
    }

    @Test
    void resolutionUsesSuppliedEffectiveDateNotServerDate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate historicalDate = LocalDate.of(2026, 1, 15);
        seedJurisdiction(tenantId, employmentId, "SA", historicalDate.minusMonths(1), null);
        UUID packId = seedActivePack("SA", "SA-HR-FOUNDATION", "1", historicalDate.minusDays(1), null);
        // Rule window is long past relative to the server current date but effective
        // on the supplied historical decision date: it must still be selected.
        seedRule(packId, "HISTORICAL_RULE", "HRM.STATUTORY.LOCAL_ACTION", "MANDATORY_HARD", false,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));

        Object decision = evaluate(tenantId, employmentId, "HRM.STATUTORY.LOCAL_ACTION",
                operationType("LOCAL_STATUTORY"), historicalDate, null,
                List.of(violatingHandler("HRM.STATUTORY.LOCAL_ACTION")));
        assertThat(decisionType(decision)).isEqualTo("BLOCKED");
        assertThat(decisionRuleCode(decision)).isEqualTo("HISTORICAL_RULE");
    }

    // ==================== PROVENANCE ====================

    @Test
    void decisionProvenanceIsPersistedWithPackAndRuleVersions() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employmentId = seedEmployment(tenantId);
        LocalDate date = LocalDate.of(2026, 9, 3);
        seedJurisdiction(tenantId, employmentId, "SA", date.minusMonths(1), null);
        UUID packId = seedActivePack("SA", "SA-HR-FOUNDATION", "1", date.minusDays(1), null);
        seedRule(packId, "PROVENANCE_RULE", "HRM.STATUTORY.LOCAL_ACTION", "MANDATORY_HARD", false,
                date.minusDays(1), null);

        evaluate(tenantId, employmentId, "HRM.STATUTORY.LOCAL_ACTION",
                operationType("LOCAL_STATUTORY"), date, null, List.of(violatingHandler("HRM.STATUTORY.LOCAL_ACTION")));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_compliance_decisions WHERE tenant_id = ? AND operation_code = ?",
                Integer.class, tenantId, "HRM.STATUTORY.LOCAL_ACTION");
        assertThat(count).as("exactly one provenance row per evaluated decision").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT decision_type FROM hr_compliance_decisions WHERE tenant_id = ? AND operation_code = ?",
                String.class, tenantId, "HRM.STATUTORY.LOCAL_ACTION")).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject(
                "SELECT pack_code || '|' || pack_version || '|' || rule_code || '|' || rule_version " +
                        "FROM hr_compliance_decisions WHERE tenant_id = ? AND operation_code = ?",
                String.class, tenantId, "HRM.STATUTORY.LOCAL_ACTION"))
                .isEqualTo("SA-HR-FOUNDATION|1|PROVENANCE_RULE|1");
    }

    // ==================== REFLECTION PLUMBING ====================

    private Object operationType(String name) throws Exception {
        return Class.forName(APP_PACKAGE + ".domain.ComplianceOperationType")
                .getField(name).get(null);
    }

    private Object evaluate(UUID tenantId, UUID employmentId, String operationCode, Object operationType,
                            LocalDate effectiveDate, UUID resourceId, List<Object> handlers) throws Exception {
        Class<?> engineClass = Class.forName(APP_PACKAGE + ".application.ComplianceEngine");
        Class<?> handlerClass = Class.forName(APP_PACKAGE + ".application.ComplianceRuleHandler");
        Class<?> repoClass = Class.forName(APP_PACKAGE + ".infrastructure.JdbcComplianceDecisionRepository");
        Class<?> contextClass = Class.forName(APP_PACKAGE + ".domain.HrCommandContext");
        Class<?> resourceClass = Class.forName(APP_PACKAGE + ".domain.ComplianceResource");

        Object resolver = Class.forName(APP_PACKAGE + ".application.CountryPolicyResolver")
                .getConstructor(JdbcTemplate.class,
                        Class.forName(APP_PACKAGE + ".application.WorkerClassificationResolver"))
                .newInstance(jdbc,
                        Class.forName(APP_PACKAGE + ".application.WorkerClassificationResolver")
                                .getConstructor(JdbcTemplate.class).newInstance(jdbc));
        Object repository = repoClass.getConstructor(JdbcTemplate.class).newInstance(jdbc);

        Object engine = engineClass
                .getConstructor(Class.forName(APP_PACKAGE + ".application.CountryPolicyResolver"),
                        List.class, repoClass)
                .newInstance(resolver, handlers, repository);

        Object context = contextClass.getDeclaredConstructor(UUID.class, UUID.class, UUID.class, UUID.class)
                .newInstance(tenantId, employmentId, UUID.randomUUID(), null);
        Object resource = resourceClass.getDeclaredConstructor(String.class, UUID.class)
                .newInstance("EMPLOYMENT", resourceId == null ? employmentId : resourceId);

        Method evaluate = engineClass.getMethod("evaluate",
                contextClass, String.class,
                Class.forName(APP_PACKAGE + ".domain.ComplianceOperationType"),
                LocalDate.class, resourceClass);
        return evaluate.invoke(engine, context, operationCode, operationType, effectiveDate, resource);
    }

    /**
     * Builds a ComplianceRuleHandler through a dynamic proxy so this RED test compiles
     * without the handler contract existing yet. The handler reports a violation for its
     * configured operation code.
     */
    private Object violatingHandler(String operationCode) throws Exception {
        Class<?> handlerClass = Class.forName(APP_PACKAGE + ".application.ComplianceRuleHandler");
        Class<?> ruleEvalClass = Class.forName(APP_PACKAGE + ".domain.RuleEvaluation");
        Class<?> ruleClass = Class.forName(APP_PACKAGE + ".domain.ComplianceRule");
        Class<?> evalContextClass = Class.forName(APP_PACKAGE + ".domain.ComplianceEvaluationContext");
        ClassLoader loader = handlerClass.getClassLoader();
        return Proxy.newProxyInstance(loader, new Class<?>[]{handlerClass}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "operationCode":
                    return operationCode;
                case "toString":
                    return "ViolatingTestHandler(" + operationCode + ")";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "evaluate":
                    return ruleEvalClass
                            .getDeclaredConstructor(boolean.class, String.class, List.class)
                            .newInstance(true, "TEST_VIOLATION", List.of("test guidance warning"));
                default:
                    throw new IllegalStateException("Unexpected handler method: " + method.getName());
            }
        });
    }

    private String decisionType(Object decision) throws Exception {
        Object value = decision.getClass().getMethod("type").invoke(decision);
        return String.valueOf(value);
    }

    private List<String> decisionWarnings(Object decision) throws Exception {
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) decision.getClass().getMethod("warnings").invoke(decision);
        return warnings == null ? List.of() : warnings;
    }

    private String decisionRuleCode(Object decision) throws Exception {
        Object value = decision.getClass().getMethod("ruleCode").invoke(decision);
        return value == null ? null : String.valueOf(value);
    }

    // ==================== FIXTURES ====================

    private void seedEmploymentJurisdictionless(UUID tenantId) throws Exception {
        // Ensures another tenant exists without a jurisdiction period (used implicitly).
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM tenants WHERE id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
    }

    private UUID seedEmployment(UUID tenantId) throws Exception {
        UUID legalEntityId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        resetTenant();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS3-T3-" + tenantId);
            ps.setString(3, "ws3t3-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(tenantId);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO legal_entities (id,tenant_id,code,name,registered_country_code,statutory_country_code,status) " +
                        "VALUES (?,?,?,?,?,?,'ACTIVE')")) {
            ps.setObject(1, legalEntityId); ps.setObject(2, tenantId); ps.setString(3, "LE-" + legalEntityId.toString().substring(0, 8));
            ps.setString(4, "Task3 Employer"); ps.setString(5, "SA"); ps.setString(6, "SA"); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_people (id,tenant_id,first_name,last_name,display_name) VALUES (?,?, 'Task','Three','Task Three')")) {
            ps.setObject(1, personId); ps.setObject(2, tenantId); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_employees (id,tenant_id,person_id,legal_entity_id,employee_number,first_name,last_name,display_name," +
                        "worker_classification_code,status,hire_date) VALUES (?,?,?,?,?,'Task','Three','Task Three',?,'ACTIVE',DATE '2026-01-01')")) {
            ps.setObject(1, employmentId); ps.setObject(2, tenantId); ps.setObject(3, personId); ps.setObject(4, legalEntityId);
            ps.setString(5, "E-" + employmentId.toString().substring(0, 8)); ps.setString(6, "CANONICAL_EMPLOYEE"); ps.executeUpdate();
        }
        return employmentId;
    }

    private void seedJurisdiction(UUID tenantId, UUID employmentId, String countryCode, LocalDate from, LocalDate to)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_employment_jurisdiction_periods " +
                        "(tenant_id,employment_id,labor_jurisdiction,effective_from,effective_to,approval_status,approval_reference) " +
                        "VALUES (?,?,?,?,?,'APPROVED','TEST-APPROVAL')")) {
            ps.setObject(1, tenantId); ps.setObject(2, employmentId); ps.setString(3, countryCode);
            ps.setObject(4, from); ps.setObject(5, to);
            ps.executeUpdate();
        }
    }

    private UUID seedActivePack(String country, String code, String version, LocalDate from, LocalDate to)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_country_packs (country_code,pack_code,pack_version,status,effective_from,effective_to," +
                        "legal_reviewed_at,legal_reviewed_by,certification_reference) " +
                        "VALUES (?,?,?,'ACTIVE',?,?,NOW(),'legal-review','TEST-CERT') RETURNING id")) {
            ps.setString(1, country); ps.setString(2, code); ps.setString(3, version);
            ps.setObject(4, from); ps.setObject(5, to);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return UUID.fromString(rs.getString(1));
            }
        }
    }

    private void seedDraftPack(String country, String code, String version, LocalDate from, LocalDate to)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_country_packs (country_code,pack_code,pack_version,status,effective_from,effective_to) " +
                        "VALUES (?,?,?,'DRAFT',?,?)")) {
            ps.setString(1, country); ps.setString(2, code); ps.setString(3, version);
            ps.setObject(4, from); ps.setObject(5, to);
            ps.executeUpdate();
        }
    }

    private void seedRule(UUID packId, String ruleCode, String operationCode, String enforcementLevel,
                          boolean exceptionAllowed, LocalDate from, LocalDate to) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_compliance_rules (country_pack_id,rule_code,rule_version,operation_code,enforcement_level," +
                        "exception_allowed,parameters,official_source_uri,legal_citation,source_snapshot_sha256," +
                        "effective_from,effective_to,last_legal_review_at,reviewed_by,status) " +
                        "VALUES (?,?, '1', ?, ?, ?, '{}'::jsonb, 'https://official-source.test/rule', 'Test citation', " +
                        "REPEAT('a', 64), ?, ?, NOW(), 'legal-review', 'ACTIVE')")) {
            ps.setObject(1, packId); ps.setString(2, ruleCode); ps.setString(3, operationCode);
            ps.setString(4, enforcementLevel); ps.setBoolean(5, exceptionAllowed);
            ps.setObject(6, from); ps.setObject(7, to);
            ps.executeUpdate();
        }
    }

    private void registerCountry(String code) {
        jdbc.update("INSERT INTO platform_countries(country_code,name_en,name_ar,status) VALUES (?, ?, ?, 'ACTIVE') ON CONFLICT DO NOTHING",
                code, "Test " + code, "Test " + code);
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString()); ps.execute();
        }
    }

    private void resetTenant() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', '', false)")) {
            ps.execute();
        }
    }
}
