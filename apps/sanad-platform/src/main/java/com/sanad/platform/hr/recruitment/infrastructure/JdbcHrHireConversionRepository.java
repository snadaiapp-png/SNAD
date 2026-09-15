package com.sanad.platform.hr.recruitment.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.hr.recruitment.domain.HrOfferState;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * HRM-G1 T8 — persistence for the governed candidate → hire conversion
 * (design §7; directive T8.2). Every method that participates in the atomic
 * boundary runs on the CALLER's connection (never commits, never closes);
 * the canonical G0 tables are written ONLY through the G0 connection-scoped
 * service variants — this repository touches G1 tables and evidence only.
 *
 * <p>Evidence semantics (§T8.10/§T8.11): audit ledger + outbox rows are
 * appended on the SAME connection via {@link JdbcHrEvidenceWriter}; any
 * failure rolls back the whole hire. Events:
 * {@code HRM.RECRUITMENT.HIRE_COMPLETED} and {@code HRM.ONBOARDING.PLAN_CREATED}
 * fire exactly once per conversion; no PII and no raw compensation ever
 * enters the payloads (ck_hr_domain_event_outbox_no_raw_secrets also holds).</p>
 */
@Repository
public class JdbcHrHireConversionRepository {

    public static final String RESOURCE_TYPE = "HR_OFFER";
    public static final String ACTION_HIRE_CONVERTED = "HRM.RECRUITMENT.HIRE_CONVERTED";
    public static final String EVENT_HIRE_COMPLETED = "HRM.RECRUITMENT.HIRE_COMPLETED";
    public static final String EVENT_PLAN_CREATED = "HRM.ONBOARDING.PLAN_CREATED";
    public static final String POLICY_HIRE_APPROVAL = "HRM.RECRUITMENT.HIRE_APPROVAL";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSource dataSource;
    private final HrTransactionalEvidenceWriter evidenceWriter;

    @Autowired
    public JdbcHrHireConversionRepository(DataSource dataSource, HrTransactionalEvidenceWriter evidenceWriter) {
        this.dataSource = dataSource;
        this.evidenceWriter = evidenceWriter == null ? new JdbcHrEvidenceWriter(dataSource) : evidenceWriter;
    }

    // ==================== tenant policy (§T8.8) ====================

    /**
     * The authoritative hire-approval policy (§T8.8): ON only when the
     * tenant's policy row says so; a missing row resolves to the documented
     * default OFF — never inferred otherwise. Read on its own connection
     * (pre-transaction policy resolution).
     */
    public boolean isHireApprovalPolicyEnabled(UUID tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                boolean enabled = isHireApprovalPolicyEnabledWithinTransaction(connection, tenantId);
                connection.commit();
                return enabled;
            } catch (Exception e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_HIRE_POLICY_LOOKUP_FAILED: " + e.getMessage(), e);
        }
    }

    public boolean isHireApprovalPolicyEnabledWithinTransaction(Connection connection, UUID tenantId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT policy_value FROM hr_tenant_policies "
                        + "WHERE tenant_id = ? AND policy_code = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, POLICY_HIRE_APPROVAL);
            try (ResultSet rs = ps.executeQuery()) {
                // §T8.8: explicit ON is the ONLY enabling value; missing or
                // anything else resolves to the documented default OFF.
                return rs.next() && "ON".equalsIgnoreCase(rs.getString(1).trim());
            }
        }
    }

    // ==================== ledger ====================

    /** Fast replay read (own connection; no writes happen on this path). */
    public Optional<HireConversionLedgerRow> findLedger(UUID tenantId, UUID offerId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Optional<HireConversionLedgerRow> row =
                        findLedgerWithinTransaction(connection, tenantId, offerId);
                connection.commit();
                return row;
            } catch (Exception e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_HIRE_LEDGER_LOOKUP_FAILED: " + e.getMessage(), e);
        }
    }

    public Optional<HireConversionLedgerRow> findLedgerWithinTransaction(Connection connection,
                                                                        UUID tenantId, UUID offerId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, application_id, conversion_state, person_id, person_reused, employment_id, "
                        + "assignment_id, contract_id, compensation_package_id, onboarding_plan_id, "
                        + "employee_number "
                        + "FROM hr_hire_conversions WHERE tenant_id = ? AND offer_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, offerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new HireConversionLedgerRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("conversion_state"),
                        rs.getObject("application_id", UUID.class),
                        rs.getObject("person_id", UUID.class),
                        rs.getBoolean("person_reused"),
                        rs.getObject("employment_id", UUID.class),
                        rs.getObject("assignment_id", UUID.class),
                        rs.getObject("contract_id", UUID.class),
                        rs.getObject("compensation_package_id", UUID.class),
                        rs.getObject("onboarding_plan_id", UUID.class),
                        rs.getString("employee_number")));
            }
        }
    }

    /**
     * §7.1 step 10 — the COMPLETE ledger row. The UNIQUE (tenant_id, offer_id)
     * constraint is the single serialization point for concurrent conversions:
     * a losing racer receives {@link HireConversionLedgerConflict}.
     */
    public UUID insertLedgerWithinTransaction(Connection connection, UUID tenantId, UUID offerId,
                                              UUID applicationId, UUID personId, boolean personReused,
                                              String employeeNumber, UUID employmentId, UUID assignmentId,
                                              UUID contractId, UUID compensationPackageId,
                                              UUID onboardingPlanId) throws SQLException {
        UUID ledgerId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_hire_conversions (id, tenant_id, offer_id, application_id, conversion_state, "
                        + "person_id, person_reused, employee_number, employment_id, assignment_id, "
                        + "contract_id, compensation_package_id, onboarding_plan_id, completed_at) "
                        + "VALUES (?, ?, ?, ?, 'COMPLETE', ?, ?, ?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setObject(1, ledgerId);
            ps.setObject(2, tenantId);
            ps.setObject(3, offerId);
            ps.setObject(4, applicationId);
            ps.setObject(5, personId);
            ps.setBoolean(6, personReused);
            ps.setString(7, employeeNumber);
            ps.setObject(8, employmentId);
            ps.setObject(9, assignmentId);
            ps.setObject(10, contractId);
            ps.setObject(11, compensationPackageId);
            ps.setObject(12, onboardingPlanId);
            ps.executeUpdate();
            return ledgerId;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new HireConversionLedgerConflict(offerId, e);
            }
            throw e;
        }
    }

    // ==================== offer / application / opening (G1 tables) ====================

    /**
     * §7.1 step 1 — lock + load the offer inside the governed transaction
     * (FOR UPDATE). Returns {@code empty} when the offer is invisible to the
     * tenant context (RLS) — the caller fails closed with
     * TENANT_CONTEXT_MISMATCH before ANY write.
     */
    public Optional<HireOfferRow> lockOfferWithinTransaction(Connection connection, UUID tenantId, UUID offerId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT o.id AS offer_id, o.tenant_id AS offer_tenant_id, o.application_id AS app_id, "
                        + "o.state AS offer_state, o.current_version_id AS current_version_id, "
                        + "o.position_id AS offer_position_id, o.expires_at AS offer_expires_at, "
                        + "a.job_opening_id AS job_opening_id, a.candidate_id AS candidate_id "
                        + "FROM hr_offers o JOIN hr_applications a "
                        + "ON a.id = o.application_id AND a.tenant_id = o.tenant_id "
                        + "WHERE o.id = ? AND o.tenant_id = ? FOR UPDATE OF o")) {
            ps.setObject(1, offerId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new HireOfferRow(
                        rs.getObject("offer_id", UUID.class),
                        rs.getObject("offer_tenant_id", UUID.class),
                        rs.getObject("app_id", UUID.class),
                        HrOfferState.valueOf(rs.getString("offer_state")),
                        rs.getObject("current_version_id", UUID.class),
                        rs.getObject("offer_position_id", UUID.class),
                        rs.getObject("job_opening_id", UUID.class),
                        rs.getObject("candidate_id", UUID.class),
                        rs.getObject("offer_expires_at", OffsetDateTime.class)));
            }
        }
    }

    public String applicationStateWithinTransaction(Connection connection, UUID tenantId, UUID applicationId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT state FROM hr_applications WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, applicationId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** §7.1 step 11 — application → HIRED, conditional (stale state fails). */
    public void markApplicationHiredWithinTransaction(Connection connection, UUID tenantId,
                                                      UUID applicationId, UUID actorId, UUID correlationId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_applications SET state = 'HIRED', version = version + 1, updated_at = NOW() "
                        + "WHERE id = ? AND tenant_id = ? AND state = 'OFFER'")) {
            ps.setObject(1, applicationId);
            ps.setObject(2, tenantId);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("HRM_APPLICATION_STAGE_CONFLICT: the application is no longer "
                        + "in stage OFFER; the hire cannot proceed");
            }
            try (PreparedStatement sp = connection.prepareStatement(
                    "INSERT INTO hr_application_stage_periods (tenant_id, application_id, stage) "
                            + "VALUES (?, ?, 'HIRED')")) {
                sp.setObject(1, tenantId);
                sp.setObject(2, applicationId);
                sp.executeUpdate();
            }
            // Candidate pool lifecycle (§6.1): ACTIVE → HIRED on the governed hire.
            try (PreparedStatement cp = connection.prepareStatement(
                    "UPDATE hr_candidates SET pool_state = 'HIRED', version = version + 1, updated_at = NOW() "
                            + "WHERE id = (SELECT candidate_id FROM hr_applications WHERE id = ? AND tenant_id = ?) "
                            + "AND tenant_id = ? AND pool_state = 'ACTIVE'")) {
                cp.setObject(1, applicationId);
                cp.setObject(2, tenantId);
                cp.setObject(3, tenantId);
                cp.executeUpdate();
            }
            ObjectNode before = JSON.createObjectNode();
            before.put("state", "OFFER");
            ObjectNode after = JSON.createObjectNode();
            after.put("state", "HIRED");
            evidenceWriter.writeEvidence(connection,
                    audit(tenantId, actorId, "HRM.RECRUITMENT.APPLICATION_HIRED", applicationId,
                            correlationId, before, after),
                    envelope(tenantId, "HRM.RECRUITMENT.APPLICATION_HIRED", applicationId,
                            actorId, correlationId));
        }
    }

    /** §T8.2 step 13 — derived opening headcount update, guarded (no overflow). */
    public void incrementOpeningHeadcountWithinTransaction(Connection connection, UUID tenantId,
                                                           UUID openingId, UUID actorId, UUID correlationId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_job_openings SET filled_headcount = filled_headcount + 1, version = version + 1, "
                        + "updated_at = NOW() WHERE id = ? AND tenant_id = ? "
                        + "AND filled_headcount < requested_headcount")) {
            ps.setObject(1, openingId);
            ps.setObject(2, tenantId);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("HRM_OPENING_HEADCOUNT_CONFLICT: opening " + openingId
                        + " has no remaining headcount; the hire cannot proceed");
            }
            ObjectNode after = JSON.createObjectNode();
            after.put("filled_headcount_delta", 1);
            evidenceWriter.writeEvidence(connection,
                    audit(tenantId, actorId, "HRM.RECRUITMENT.OPENING_HIRED_HEADCOUNT", openingId,
                            correlationId, JSON.createObjectNode(), after),
                    envelope(tenantId, "HRM.RECRUITMENT.OPENING_HIRED_HEADCOUNT", openingId,
                            actorId, correlationId));
        }
    }

    /** Opening + org-unit resolution for the assignment inputs (G1 read path). */
    public HireOpeningRow resolveOpeningWithinTransaction(Connection connection, UUID tenantId, UUID openingId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT o.id, o.position_id, u.organization_id "
                        + "FROM hr_job_openings o JOIN hr_org_units u ON u.id = o.org_unit_id "
                        + "WHERE o.id = ? AND o.tenant_id = ?")) {
            ps.setObject(1, openingId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new HireOpeningRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("position_id", UUID.class),
                        rs.getObject("organization_id", UUID.class));
            }
        }
    }

    // ==================== onboarding materialization (§T8.6 / §7.1 step 9) ====================

    /**
     * Materializes the ACTIVE tenant template into plan + checklist + tasks.
     * A missing template, an inactive template, or an invalid definition
     * (non-array, no actionable tasks) fails closed with
     * {@code HRM_CONVERSION_ONBOARDING_TEMPLATE_INVALID} BEFORE any hire
     * output survives (the caller rolls the whole conversion back).
     */
    public UUID materializeOnboardingPlanWithinTransaction(Connection connection, UUID tenantId,
                                                           UUID employmentId, UUID actorId,
                                                           UUID correlationId) throws SQLException {
        UUID templateId;
        String templateCode;
        int templateVersion;
        String definition;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, code, version, definition FROM hr_onboarding_checklist_templates "
                        + "WHERE tenant_id = ? AND is_active = TRUE "
                        + "ORDER BY created_at DESC, id LIMIT 1")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                            "HRM_CONVERSION_ONBOARDING_TEMPLATE_INVALID: no ACTIVE onboarding checklist "
                                    + "template exists for this tenant; a hire cannot complete without a plan");
                }
                templateId = rs.getObject("id", UUID.class);
                templateCode = rs.getString("code");
                templateVersion = rs.getInt("version");
                definition = rs.getString("definition");
            }
        }

        ArrayNode tasks = parseTaskDefinition(definition);
        if (tasks.isEmpty()) {
            throw new IllegalStateException(
                    "HRM_CONVERSION_ONBOARDING_TEMPLATE_INVALID: the template definition carries no tasks");
        }

        UUID planId = UUID.randomUUID();
        String planNumber = "ONB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                .toUpperCase();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_onboarding_plans (id, tenant_id, plan_number, employment_id, "
                        + "checklist_template_id, state, version) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)")) {
            ps.setObject(1, planId);
            ps.setObject(2, tenantId);
            ps.setString(3, planNumber);
            ps.setObject(4, employmentId);
            ps.setObject(5, templateId);
            ps.executeUpdate();
        }

        UUID checklistId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_onboarding_checklists (id, tenant_id, plan_id, template_id, template_code, "
                        + "template_version) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, checklistId);
            ps.setObject(2, tenantId);
            ps.setObject(3, planId);
            ps.setObject(4, templateId);
            ps.setString(5, templateCode);
            ps.setInt(6, templateVersion);
            ps.executeUpdate();
        }

        // The checklist is an immutable snapshot of the template definition:
        // later template edits NEVER mutate this instance (T9 versioning).
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_onboarding_tasks (id, tenant_id, plan_id, checklist_id, seq, title, state) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'OPEN')")) {
            for (int i = 0; i < tasks.size(); i++) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, planId);
                ps.setObject(4, checklistId);
                ps.setInt(5, tasks.get(i).path("seq").asInt(i + 1));
                String title = tasks.get(i).path("title").asText(null);
                if (title == null || title.isBlank()) {
                    throw new IllegalStateException(
                            "HRM_CONVERSION_ONBOARDING_TEMPLATE_INVALID: template task " + i
                                    + " has no title");
                }
                ps.setString(6, title.substring(0, Math.min(200, title.length())));
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // PLAN_CREATED — exactly once, no PII (design §7.1 step 12).
        ObjectNode planPayload = JSON.createObjectNode();
        planPayload.put("plan_id", planId.toString());
        planPayload.put("employment_id", employmentId.toString());
        planPayload.put("template_code", templateCode);
        planPayload.put("event_type", EVENT_PLAN_CREATED);
        evidenceWriter.writeEvidence(connection,
                audit(tenantId, actorId, "HRM.ONBOARDING.PLAN_CREATED", planId, correlationId,
                        JSON.createObjectNode(),
                        JSON.createObjectNode().put("plan_number", planNumber)
                                .put("task_count", tasks.size())),
                new DomainEventEnvelope(UUID.randomUUID(), EVENT_PLAN_CREATED, 1, "HR_ONBOARDING_PLAN",
                        planId, tenantId, null, actorId, OffsetDateTime.now().toInstant(),
                        correlationId, null, null, "OPERATIONAL", planPayload));
        return planId;
    }

    private ArrayNode parseTaskDefinition(String definition) {
        if (definition == null || definition.isBlank()) {
            throw new IllegalStateException(
                    "HRM_CONVERSION_ONBOARDING_TEMPLATE_INVALID: the template definition is empty");
        }
        try {
            var node = JSON.readTree(definition);
            if (!node.isArray()) {
                throw new IllegalStateException(
                        "HRM_CONVERSION_ONBOARDING_TEMPLATE_INVALID: the template definition must be an array");
            }
            return (ArrayNode) node;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HRM_CONVERSION_ONBOARDING_TEMPLATE_INVALID: the template definition is not valid JSON", e);
        }
    }

    // ==================== conversion evidence (§7.1 step 12) ====================

    /** Final conversion audit + HIRE_COMPLETED outbox — same connection. */
    public void writeHireCompletedWithinTransaction(Connection connection, UUID tenantId, UUID offerId,
                                                    UUID applicationId, UUID personId, boolean personReused,
                                                    String employeeNumber, UUID employmentId,
                                                    UUID assignmentId, UUID contractId,
                                                    UUID compensationPackageId, UUID onboardingPlanId,
                                                    UUID actorId, UUID correlationId) throws SQLException {
        ObjectNode after = JSON.createObjectNode();
        after.put("offer_id", offerId.toString());
        after.put("person_id", personId.toString());
        after.put("person_reused", personReused);
        after.put("employee_number", employeeNumber);
        after.put("employment_id", employmentId.toString());
        after.put("assignment_id", assignmentId.toString());
        after.put("contract_id", contractId.toString());
        after.put("compensation_package_id", compensationPackageId.toString());
        after.put("onboarding_plan_id", onboardingPlanId.toString());
        // No candidate PII, no compensation values — identifiers only.

        evidenceWriter.writeEvidence(connection,
                audit(tenantId, actorId, ACTION_HIRE_CONVERTED, offerId, correlationId,
                        JSON.createObjectNode().put("state", "ACCEPTED"), after),
                envelopeWith(tenantId, EVENT_HIRE_COMPLETED, offerId, personId, employmentId,
                        actorId, correlationId));
    }

    private DomainEventEnvelope envelopeWith(UUID tenantId, String eventType, UUID offerId,
                                             UUID personId, UUID employmentId,
                                             UUID actorId, UUID correlationId) {
        // HIRE_COMPLETED carries employment_id + person_id (design §7.1 step 12);
        // PII-free by contract — identifiers only.
        ObjectNode payload = JSON.createObjectNode();
        payload.put("offer_id", offerId.toString());
        payload.put("person_id", personId.toString());
        payload.put("employment_id", employmentId.toString());
        payload.put("event_type", eventType);
        return new DomainEventEnvelope(JdbcHrEvidenceWriter.deterministicEventId(
                        tenantId, eventType, employmentId, offerId),
                eventType, 1, RESOURCE_TYPE, offerId, tenantId, null, actorId,
                OffsetDateTime.now().toInstant(), correlationId, null, null,
                "OPERATIONAL", payload);
    }

    private HrAuditRecord audit(UUID tenantId, UUID actorId, String action, UUID resourceId,
                                UUID correlationId, ObjectNode before, ObjectNode after) {
        return new HrAuditRecord(tenantId, actorId, action, RESOURCE_TYPE, resourceId,
                null, null, "OPERATIONAL", null, before, after, "SUCCESS",
                correlationId, null, null);
    }

    private DomainEventEnvelope envelope(UUID tenantId, String eventType, UUID entityId,
                                         UUID actorId, UUID correlationId) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("event_type", eventType);
        return new DomainEventEnvelope(UUID.randomUUID(), eventType, 1, RESOURCE_TYPE, entityId,
                tenantId, null, actorId, OffsetDateTime.now().toInstant(), correlationId, null,
                null, "OPERATIONAL", payload);
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
        }
    }

    // ==================== row records ====================

    /** Locked offer row (§7.1 step 1). */
    public record HireOfferRow(UUID id, UUID tenantId, UUID applicationId, HrOfferState state,
                               UUID currentVersionId, UUID positionId, UUID jobOpeningId,
                               UUID candidateId, OffsetDateTime expiresAt) {
    }

    /** Opening resolution row for the assignment inputs. */
    public record HireOpeningRow(UUID id, UUID positionId, UUID organizationId) {
    }

    /** Ledger row (replay + audit surface). */
    public record HireConversionLedgerRow(UUID id, String conversionState, UUID applicationId,
                                          UUID personId, boolean personReused, UUID employmentId,
                                          UUID assignmentId, UUID contractId,
                                          UUID compensationPackageId, UUID onboardingPlanId,
                                          String employeeNumber) {
    }

    /** Concurrent double-conversion loser signal (§7.3 case 8). */
    public static final class HireConversionLedgerConflict extends RuntimeException {
        public final UUID offerId;

        public HireConversionLedgerConflict(UUID offerId, SQLException cause) {
            super("HRM_HIRE_CONVERSION_CONFLICT", cause);
            this.offerId = offerId;
        }
    }
}
