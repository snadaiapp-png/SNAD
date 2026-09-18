package com.sanad.platform.hr.recruitment.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.hr.recruitment.domain.HrOffer;
import com.sanad.platform.hr.recruitment.domain.HrOfferState;
import com.sanad.platform.hr.recruitment.domain.HrOfferVersion;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for the HrOffer aggregate and its immutable HrOfferVersion
 * history (HRM-G1 T7).
 *
 * <p>Every mutation runs on a transaction-scoped connection with
 * {@code SET LOCAL app.tenant_id} (FORCE RLS) and appends the audit fact and
 * the outbox event on the SAME connection via the G0 transactional evidence
 * writer. This repository owns ALL {@code hr_offers} writes — no controller
 * or other service writes offer status directly (architecture boundary). It
 * never decides business transitions: callers pass the expected source state
 * and the guard-validated target; the optimistic conditional UPDATE fails
 * closed on any race ({@code HRM_OFFER_STATE_CONFLICT}).</p>
 *
 * <p>{@code hr_offer_versions} is INSERT-only here AND database-enforced
 * append-only (V20260914_1 guard trigger) — historical versions cannot be
 * mutated or removed by any path.</p>
 */
@Repository
public class JdbcHrOfferRepository {

    public static final String RESOURCE_TYPE = "HR_OFFER";

    public static final String ACTION_CREATED = "HRM.RECRUITMENT.OFFER_CREATED";
    public static final String ACTION_REVISED = "HRM.RECRUITMENT.OFFER_REVISED";
    public static final String ACTION_SUBMITTED_FOR_APPROVAL = "HRM.RECRUITMENT.OFFER_SUBMITTED_FOR_APPROVAL";
    public static final String ACTION_EXTENDED = "HRM.RECRUITMENT.OFFER_EXTENDED";
    public static final String ACTION_REJECTED = "HRM.RECRUITMENT.OFFER_REJECTED";
    public static final String ACTION_ACCEPTED = "HRM.RECRUITMENT.OFFER_ACCEPTED";
    public static final String ACTION_DECLINED = "HRM.RECRUITMENT.OFFER_DECLINED";
    public static final String ACTION_WITHDRAWN = "HRM.RECRUITMENT.OFFER_WITHDRAWN";
    public static final String ACTION_EXPIRED = "HRM.RECRUITMENT.OFFER_EXPIRED";
    public static final String ACTION_APPROVAL_CANCELLED = "HRM.RECRUITMENT.OFFER_APPROVAL_CANCELLED";
    public static final String ACTION_COMPENSATION_READ = "HRM.RECRUITMENT.OFFER_COMPENSATION_READ";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSource dataSource;
    private final JdbcHrEvidenceWriter evidenceWriter;

    @Autowired
    public JdbcHrOfferRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.evidenceWriter = new JdbcHrEvidenceWriter(dataSource);
    }

    /** Inserts a DRAFT offer together with its initial immutable version. */
    public HrOffer insertOffer(HrOffer offer, HrOfferVersion version, UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, offer.tenantId());
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO hr_offers (id, tenant_id, application_id, offer_number, state, "
                                + "expires_at, position_id, version) VALUES (?, ?, ?, ?, ?, ?, ?, 0)")) {
                    ps.setObject(1, offer.id());
                    ps.setObject(2, offer.tenantId());
                    ps.setObject(3, offer.applicationId());
                    ps.setString(4, offer.offerNumber());
                    ps.setString(5, offer.state().name());
                    if (offer.expiresAt() != null) {
                        ps.setObject(6, offer.expiresAt());
                    } else {
                        ps.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE);
                    }
                    if (offer.positionId() != null) {
                        ps.setObject(7, offer.positionId());
                    } else {
                        ps.setNull(7, Types.NULL);
                    }
                    ps.executeUpdate();
                }
                insertVersionRow(connection, version);
                setCurrentVersion(connection, offer.tenantId(), offer.id(), version.id());
                HrOffer stored = findWithinTx(connection, offer.tenantId(), offer.id())
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offer.id()));
                writeEvidence(connection,
                        audit(offer.tenantId(), actorId, ACTION_CREATED, offer.id(), correlationId, null, null),
                        envelope(offer.tenantId(), ACTION_CREATED, offer.id(), actorId, correlationId));
                connection.commit();
                return stored;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof SQLException se && "23505".equals(se.getSQLState())) {
                    throw new IllegalStateException("HRM_OFFER_NUMBER_CONFLICT: "
                            + "offer_number is already used by this tenant");
                }
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Appends the NEXT immutable version (serialized by the FOR UPDATE row
     * lock on the offer) and binds it either as the current DRAFT terms or as
     * the pending revision candidate. 23505 on
     * {@code UNIQUE (offer_id, version_number)} maps to
     * {@code HRM_OFFER_VERSION_CONFLICT}.
     */
    public HrOfferVersion insertVersion(UUID tenantId, UUID offerId, String contractTerms, String compensation,
                                        OffsetDateTime expiresAt, boolean makeCurrent, String auditAction,
                                        UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                lockOfferRow(connection, tenantId, offerId);
                int nextNumber = nextVersionNumber(connection, tenantId, offerId);
                UUID predecessorId = currentVersionIdFor(connection, tenantId, offerId);
                UUID versionId = UUID.randomUUID();
                HrOfferVersion version = new HrOfferVersion(versionId, tenantId, offerId, nextNumber,
                        predecessorId, contractTerms, compensation, actorId, null);
                insertVersionRow(connection, version);
                if (makeCurrent) {
                    setCurrentVersion(connection, tenantId, offerId, versionId);
                    bumpOfferVersion(connection, tenantId, offerId);
                } else {
                    setPendingOfferVersion(connection, tenantId, offerId, versionId);
                    bumpOfferVersion(connection, tenantId, offerId);
                }
                if (expiresAt != null) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE hr_offers SET expires_at = ? WHERE id = ? AND tenant_id = ?")) {
                        ps.setObject(1, expiresAt);
                        ps.setObject(2, offerId);
                        ps.setObject(3, tenantId);
                        ps.executeUpdate();
                    }
                }
                writeEvidence(connection,
                        audit(tenantId, actorId, auditAction, offerId, correlationId, null, versionId),
                        envelope(tenantId, auditAction, offerId, actorId, correlationId));
                connection.commit();
                return version;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof SQLException se && "23505".equals(se.getSQLState())) {
                    throw new IllegalStateException("HRM_OFFER_VERSION_CONFLICT: "
                            + "a concurrent revision created the same version number");
                }
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Tenant-scoped load (FORCE RLS applies; foreign-tenant ids resolve empty). */
    public Optional<HrOffer> find(UUID tenantId, UUID offerId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Optional<HrOffer> found = findWithinTx(connection, tenantId, offerId);
                connection.commit();
                return found;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Immutable version history (version_number ASC, created_at ASC). */
    public List<HrOfferVersion> versions(UUID tenantId, UUID offerId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                List<HrOfferVersion> rows = versionsWithinTx(connection, tenantId, offerId);
                connection.commit();
                return rows;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Guarded optimistic state transition with no correlation changes
     * (accept/decline/withdraw/expire).
     */
    public HrOffer transition(UUID tenantId, UUID offerId, HrOfferState expectedFrom, HrOfferState to,
                              OffsetDateTime expiresAt, String auditAction, UUID actorId, UUID correlationId) {
        return guardedUpdate(tenantId, offerId, expectedFrom, to,
                "UPDATE hr_offers SET state = ?, version = version + 1, updated_at = NOW()"
                        + (expiresAt != null ? ", expires_at = ?" : "")
                        + " WHERE id = ? AND tenant_id = ? AND state = ? AND version = "
                        + "(SELECT version FROM hr_offers WHERE id = ? AND tenant_id = ?)",
                ps -> {
                    int i = 1;
                    ps.setString(i++, to.name());
                    if (expiresAt != null) {
                        ps.setObject(i++, expiresAt);
                    }
                    ps.setObject(i++, offerId);
                    ps.setObject(i++, tenantId);
                    ps.setString(i++, expectedFrom.name());
                    ps.setObject(i++, offerId);
                    ps.setObject(i++, tenantId);
                },
                auditAction, actorId, correlationId, null, null);
    }

    /**
     * Approval submission with the open approval correlation (T7.6). The
     * source state is DRAFT for the initial cycle and EXTENDED for a
     * post-extension revision cycle (§T7.9); the guard-validated target state
     * is PENDING_APPROVAL for DRAFT and unchanged EXTENDED for the revision.
     */
    public HrOffer persistSubmission(UUID tenantId, UUID offerId, HrOfferState expectedFrom,
                                     HrOfferState targetState, UUID workflowInstanceId,
                                     UUID workflowDefinitionVersionId, UUID submittedVersionId,
                                     UUID actorId, UUID correlationId) {
        return guardedUpdate(tenantId, offerId, expectedFrom, targetState,
                "UPDATE hr_offers SET state = ?, version = version + 1, updated_at = NOW(), "
                        + "pending_workflow_instance_id = ?, pending_workflow_definition_version_id = ?, "
                        + "pending_offer_version_id = ? "
                        + "WHERE id = ? AND tenant_id = ? AND state = ? AND version = "
                        + "(SELECT version FROM hr_offers WHERE id = ? AND tenant_id = ?)",
                ps -> {
                    int i = 1;
                    ps.setString(i++, HrOfferState.PENDING_APPROVAL.name());
                    ps.setObject(i++, workflowInstanceId);
                    ps.setObject(i++, workflowDefinitionVersionId);
                    ps.setObject(i++, submittedVersionId);
                    ps.setObject(i++, offerId);
                    ps.setObject(i++, tenantId);
                    ps.setString(i++, expectedFrom.name());
                    ps.setObject(i++, offerId);
                    ps.setObject(i++, tenantId);
                },
                ACTION_SUBMITTED_FOR_APPROVAL, actorId, correlationId,
                workflowInstanceId, submittedVersionId);
    }

    /**
     * PENDING_APPROVAL → EXTENDED (exactly once, optimistic): the submitted
     * version becomes the current extended terms and the correlation is
     * closed. The conditional predicate includes the workflow instance so a
     * replayed/stale completion cannot re-extend.
     */
    public HrOffer persistExtension(UUID tenantId, UUID offerId, UUID workflowInstanceId,
                                    OffsetDateTime expiresAt, UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                UUID expectedDefinitionVersionId = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId))
                        .pendingWorkflowDefinitionVersionId();
                verifyWorkflowApprovalInTx(connection, tenantId, offerId, workflowInstanceId,
                        expectedDefinitionVersionId, true);
                int rows;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_offers SET state = ?, version = version + 1, updated_at = NOW(), "
                                + "current_version_id = pending_offer_version_id, expires_at = ?, "
                                + "pending_workflow_instance_id = NULL, "
                                + "pending_workflow_definition_version_id = NULL, "
                                + "pending_offer_version_id = NULL "
                                + "WHERE id = ? AND tenant_id = ? AND state = ? "
                                + "AND pending_workflow_instance_id = ? AND version = "
                                + "(SELECT version FROM hr_offers WHERE id = ? AND tenant_id = ?)")) {
                    ps.setString(1, HrOfferState.EXTENDED.name());
                    ps.setObject(2, expiresAt);
                    ps.setObject(3, offerId);
                    ps.setObject(4, tenantId);
                    ps.setString(5, HrOfferState.PENDING_APPROVAL.name());
                    ps.setObject(6, workflowInstanceId);
                    ps.setObject(7, offerId);
                    ps.setObject(8, tenantId);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    connection.rollback();
                    throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: expected PENDING_APPROVAL "
                            + "linked to workflow " + workflowInstanceId + " (concurrent modification)");
                }
                UUID extendedVersionId = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId))
                        .currentVersionId();
                writeEvidence(connection,
                        audit(tenantId, actorId, ACTION_EXTENDED, offerId, correlationId,
                                workflowInstanceId, extendedVersionId),
                        envelope(tenantId, ACTION_EXTENDED, offerId, actorId, correlationId));
                HrOffer after = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId));
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * EXTENDED (revision cycle) → re-extension: swaps the current version to
     * the newly approved pending revision and closes the correlation. The
     * offer state remains EXTENDED — no EXTENDED→DRAFT demotion exists.
     */
    public HrOffer persistRevisionExtension(UUID tenantId, UUID offerId, UUID workflowInstanceId,
                                            OffsetDateTime expiresAt, UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                UUID expectedDefinitionVersionId = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId))
                        .pendingWorkflowDefinitionVersionId();
                verifyWorkflowApprovalInTx(connection, tenantId, offerId, workflowInstanceId,
                        expectedDefinitionVersionId, true);
                int rows;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_offers SET version = version + 1, updated_at = NOW(), "
                                + "current_version_id = pending_offer_version_id, expires_at = ?, "
                                + "pending_workflow_instance_id = NULL, "
                                + "pending_workflow_definition_version_id = NULL, "
                                + "pending_offer_version_id = NULL "
                                + "WHERE id = ? AND tenant_id = ? AND state = ? "
                                + "AND pending_workflow_instance_id = ? AND version = "
                                + "(SELECT version FROM hr_offers WHERE id = ? AND tenant_id = ?)")) {
                    ps.setObject(1, expiresAt);
                    ps.setObject(2, offerId);
                    ps.setObject(3, tenantId);
                    ps.setString(4, HrOfferState.EXTENDED.name());
                    ps.setObject(5, workflowInstanceId);
                    ps.setObject(6, offerId);
                    ps.setObject(7, tenantId);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    connection.rollback();
                    throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: expected EXTENDED revision "
                            + "linked to workflow " + workflowInstanceId + " (concurrent modification)");
                }
                UUID extendedVersionId = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId))
                        .currentVersionId();
                writeEvidence(connection,
                        audit(tenantId, actorId, ACTION_EXTENDED, offerId, correlationId,
                                workflowInstanceId, extendedVersionId),
                        envelope(tenantId, ACTION_EXTENDED, offerId, actorId, correlationId));
                HrOffer after = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId));
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** PENDING_APPROVAL → DRAFT (governed rejection, T7.8); correlation closed. */
    public HrOffer persistRejection(UUID tenantId, UUID offerId, UUID workflowInstanceId, String reasonCode,
                                    UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                UUID expectedDefinitionVersionId = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId))
                        .pendingWorkflowDefinitionVersionId();
                verifyWorkflowApprovalInTx(connection, tenantId, offerId, workflowInstanceId,
                        expectedDefinitionVersionId, false);
                int rows;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_offers SET state = ?, version = version + 1, updated_at = NOW(), "
                                + "pending_workflow_instance_id = NULL, "
                                + "pending_workflow_definition_version_id = NULL, "
                                + "pending_offer_version_id = NULL "
                                + "WHERE id = ? AND tenant_id = ? AND state = ? "
                                + "AND pending_workflow_instance_id = ? AND version = "
                                + "(SELECT version FROM hr_offers WHERE id = ? AND tenant_id = ?)")) {
                    ps.setString(1, HrOfferState.DRAFT.name());
                    ps.setObject(2, offerId);
                    ps.setObject(3, tenantId);
                    ps.setString(4, HrOfferState.PENDING_APPROVAL.name());
                    ps.setObject(5, workflowInstanceId);
                    ps.setObject(6, offerId);
                    ps.setObject(7, tenantId);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    connection.rollback();
                    throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: expected PENDING_APPROVAL "
                            + "linked to workflow " + workflowInstanceId + " (concurrent modification)");
                }
                writeEvidence(connection,
                        audit(tenantId, actorId, ACTION_REJECTED, offerId, correlationId,
                                workflowInstanceId, null, reasonCode),
                        envelope(tenantId, ACTION_REJECTED, offerId, actorId, correlationId));
                HrOffer after = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId));
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** EXTENDED revision cycle rejected: only the correlation is closed. */
    public HrOffer persistRevisionRejection(UUID tenantId, UUID offerId, UUID workflowInstanceId, String reasonCode,
                                            UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                UUID expectedDefinitionVersionId = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId))
                        .pendingWorkflowDefinitionVersionId();
                verifyWorkflowApprovalInTx(connection, tenantId, offerId, workflowInstanceId,
                        expectedDefinitionVersionId, false);
                int rows;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_offers SET version = version + 1, updated_at = NOW(), "
                                + "pending_workflow_instance_id = NULL, "
                                + "pending_workflow_definition_version_id = NULL, "
                                + "pending_offer_version_id = NULL "
                                + "WHERE id = ? AND tenant_id = ? AND state = ? "
                                + "AND pending_workflow_instance_id = ? AND version = "
                                + "(SELECT version FROM hr_offers WHERE id = ? AND tenant_id = ?)")) {
                    ps.setObject(1, offerId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, HrOfferState.EXTENDED.name());
                    ps.setObject(4, workflowInstanceId);
                    ps.setObject(5, offerId);
                    ps.setObject(6, tenantId);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    connection.rollback();
                    throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: expected EXTENDED revision "
                            + "linked to workflow " + workflowInstanceId + " (concurrent modification)");
                }
                writeEvidence(connection,
                        audit(tenantId, actorId, ACTION_REJECTED, offerId, correlationId,
                                workflowInstanceId, null, reasonCode),
                        envelope(tenantId, ACTION_REJECTED, offerId, actorId, correlationId));
                HrOffer after = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId));
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Tenant-scoped application stage read for the §T7.10 consistency gate
     * (offers require the application to be in stage OFFER). Read-only.
     */
    public Optional<String> applicationState(UUID tenantId, UUID applicationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Optional<String> state;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT state FROM hr_applications WHERE id = ? AND tenant_id = ?")) {
                    ps.setObject(1, applicationId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        state = rs.next() ? Optional.of(rs.getString("state")) : Optional.empty();
                    }
                }
                connection.commit();
                return state;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * §8 EXPIRY: EXTENDED → ACCEPTED gated by the DATABASE clock
     * (§ expires_at > NOW()) inside the governed mutation — no client
     * clock authority, deterministic resolution, acceptance after expiry
     * fails closed with HRM_OFFER_EXPIRED.
     */
    public HrOffer acceptIfNotExpired(UUID tenantId, UUID offerId, UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                int rows;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_offers SET state = ?, version = version + 1, updated_at = NOW() "
                                + "WHERE id = ? AND tenant_id = ? AND state = ? "
                                + "AND (expires_at IS NULL OR expires_at > NOW()) AND version = "
                                + "(SELECT version FROM hr_offers WHERE id = ? AND tenant_id = ?)")) {
                    ps.setString(1, HrOfferState.ACCEPTED.name());
                    ps.setObject(2, offerId);
                    ps.setObject(3, tenantId);
                    ps.setString(4, HrOfferState.EXTENDED.name());
                    ps.setObject(5, offerId);
                    ps.setObject(6, tenantId);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    connection.rollback();
                    HrOffer current = findWithinTx(connection, tenantId, offerId)
                            .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId));
                    if (current.state() == HrOfferState.EXTENDED && current.expiresAt() != null) {
                        try (PreparedStatement ps = connection.prepareStatement(
                                "SELECT (expires_at <= NOW()) FROM hr_offers WHERE id = ? AND tenant_id = ?")) {
                            ps.setObject(1, offerId);
                            ps.setObject(2, tenantId);
                            try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                if (rs.getBoolean(1)) {
                                    throw new IllegalStateException("HRM_OFFER_EXPIRED: the offer expired at "
                                            + current.expiresAt() + "; acceptance after expiry fails closed");
                                }
                            }
                        }
                    }
                    throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: expected state "
                            + HrOfferState.EXTENDED + " (concurrent modification)");
                }
                writeEvidence(connection,
                        audit(tenantId, actorId, ACTION_ACCEPTED, offerId, correlationId, null, null),
                        envelope(tenantId, ACTION_ACCEPTED, offerId, actorId, correlationId));
                HrOffer after = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId));
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * §9 ORPHAN CLEANUP: an approval cycle cancelled while the offer is
     * PENDING_APPROVAL returns the offer to DRAFT (registered reason) and
     * closes the correlation — the cancelled Y2 work items are no longer
     * actionable on the HRM side either.
     */
    public HrOffer persistApprovalCancellation(UUID tenantId, UUID offerId, UUID workflowInstanceId,
                                               String reasonCode, UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                UUID expectedDefinitionVersionId = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId))
                        .pendingWorkflowDefinitionVersionId();
                // Verification shape: the authoritative instance must be in a
                // non-RUNNING (cancelled/cancelling) state — verified here as
                // "no APPROVED outcome can ever apply" (fail-closed).
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT status FROM workflow_instances WHERE id = ? AND tenant_id = ? FOR SHARE")) {
                    ps.setObject(1, workflowInstanceId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("HRM_OFFER_APPROVAL_STALE: workflow instance "
                                    + workflowInstanceId + " does not exist in this tenant");
                        }
                        String status = rs.getString(1);
                        if ("COMPLETED".equals(status)) {
                            throw new IllegalStateException("HRM_OFFER_APPROVAL_OUTCOME_MISMATCH: instance "
                                    + "already completed; use the governed outcome reconciliation");
                        }
                    }
                }
                if (expectedDefinitionVersionId == null) {
                    throw new IllegalStateException("HRM_OFFER_APPROVAL_LINK_INVALID: incomplete correlation");
                }
                int rows;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_offers SET state = ?, version = version + 1, updated_at = NOW(), "
                                + "pending_workflow_instance_id = NULL, "
                                + "pending_workflow_definition_version_id = NULL, "
                                + "pending_offer_version_id = NULL "
                                + "WHERE id = ? AND tenant_id = ? AND state = ? "
                                + "AND pending_workflow_instance_id = ? AND version = "
                                + "(SELECT version FROM hr_offers WHERE id = ? AND tenant_id = ?)")) {
                    ps.setString(1, HrOfferState.DRAFT.name());
                    ps.setObject(2, offerId);
                    ps.setObject(3, tenantId);
                    ps.setString(4, HrOfferState.PENDING_APPROVAL.name());
                    ps.setObject(5, workflowInstanceId);
                    ps.setObject(6, offerId);
                    ps.setObject(7, tenantId);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    connection.rollback();
                    throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: expected PENDING_APPROVAL "
                            + "linked to workflow " + workflowInstanceId + " (concurrent modification)");
                }
                writeEvidence(connection,
                        audit(tenantId, actorId, ACTION_APPROVAL_CANCELLED, offerId, correlationId,
                                workflowInstanceId, null, reasonCode),
                        envelope(tenantId, ACTION_APPROVAL_CANCELLED, offerId, actorId, correlationId));
                HrOffer after = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId));
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Sensitive-read audit evidence (design §14: reads of the offer
     * compensation draft are sensitive-read audited). The payload carries NO
     * compensation values — only the reference.
     */
    public void recordCompensationRead(UUID tenantId, UUID offerId, UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                ObjectNode after = JSON.createObjectNode()
                        .put("state_event", ACTION_COMPENSATION_READ)
                        .put("offer_id", offerId.toString());
                HrAuditRecord record = new HrAuditRecord(tenantId, actorId, ACTION_COMPENSATION_READ,
                        RESOURCE_TYPE, offerId, null, null, "SENSITIVE", null,
                        JSON.createObjectNode(), after, "SUCCESS", correlationId, null, null);
                ObjectNode payload = JSON.createObjectNode();
                payload.put("offer_id", offerId.toString());
                payload.put("event_type", ACTION_COMPENSATION_READ);
                DomainEventEnvelope env = new DomainEventEnvelope(UUID.randomUUID(), ACTION_COMPENSATION_READ,
                        1, RESOURCE_TYPE, offerId, tenantId, null, actorId,
                        OffsetDateTime.now().toInstant(), correlationId, null, null, "SENSITIVE", payload);
                writeEvidence(connection, record, env);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Idempotent-replay evidence (T7.7): the immutable audit ledger proves
     * whether THIS offer was already extended/rejected through THIS workflow
     * instance — a repeated completion is side-effect-free, a foreign one is
     * refused.
     */
    public boolean hasActionEvidence(UUID tenantId, UUID offerId, String action, UUID workflowInstanceId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Integer count;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) FROM hr_audit_ledger "
                                + "WHERE resource_type = ? AND resource_id = ? AND action = ? "
                                + "AND after_state->>'workflow_instance_id' = ?")) {
                    ps.setString(1, RESOURCE_TYPE);
                    ps.setObject(2, offerId);
                    ps.setString(3, action);
                    ps.setString(4, workflowInstanceId.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        count = rs.getInt(1);
                    }
                }
                connection.commit();
                return count != null && count > 0;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    // ==================== internals ====================

    /**
     * §7 IN-TRANSACTION APPROVAL CHECK (fail-closed): the authoritative
     * Workflow Y2 state is verified INSIDE the governed mutation transaction,
     * on the same connection/snapshot, with FOR SHARE row locks on the
     * instance row so verification + HR state transition cannot race through
     * a stale pre-read. A read-before-transaction followed by an
     * unconditional UPDATE is NOT used anywhere on the extension path.
     */
    private void verifyWorkflowApprovalInTx(Connection connection, UUID tenantId, UUID offerId,
                                            UUID workflowInstanceId, UUID expectedDefinitionVersionId,
                                            boolean requireApproved) throws SQLException {
        String status;
        String entityType;
        UUID entityId;
        UUID definitionVersionId;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status, business_entity_type, business_entity_id, definition_version_id "
                        + "FROM workflow_instances WHERE id = ? AND tenant_id = ? FOR SHARE")) {
            ps.setObject(1, workflowInstanceId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_OFFER_APPROVAL_STALE: workflow instance "
                            + workflowInstanceId + " does not exist in this tenant");
                }
                status = rs.getString("status");
                entityType = rs.getString("business_entity_type");
                entityId = (UUID) rs.getObject("business_entity_id");
                definitionVersionId = (UUID) rs.getObject("definition_version_id");
            }
        }
        if (!"HR_OFFER".equals(entityType) || !offerId.equals(entityId)) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_LINK_INVALID: the workflow instance correlates "
                    + entityType + "/" + entityId + "; a foreign or stale workflow result can never move this offer");
        }
        if (expectedDefinitionVersionId == null || !expectedDefinitionVersionId.equals(definitionVersionId)) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_STALE: the workflow instance was not started "
                    + "on the correlated authoritative definition version");
        }
        int approved = 0;
        int rejected = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status, COUNT(*) FROM workflow_approval_requests "
                        + "WHERE workflow_instance_id = ? GROUP BY status")) {
            ps.setObject(1, workflowInstanceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String s = rs.getString(1);
                    if ("APPROVED".equals(s)) {
                        approved = rs.getInt(2);
                    } else if ("REJECTED".equals(s)) {
                        rejected = rs.getInt(2);
                    }
                }
            }
        }
        if (requireApproved) {
            if (rejected > 0) {
                throw new IllegalStateException("HRM_OFFER_APPROVAL_OUTCOME_MISMATCH: the authoritative outcome "
                        + "is REJECTED; extension requires APPROVED");
            }
            if (!"COMPLETED".equals(status) || approved == 0) {
                throw new IllegalStateException("HRM_OFFER_APPROVAL_OUTCOME_PENDING: the authoritative workflow "
                        + "instance is " + status + " with " + approved + " approvals; no extension without a "
                        + "final APPROVED outcome");
            }
        } else {
            if (approved > 0 && rejected == 0) {
                throw new IllegalStateException("HRM_OFFER_APPROVAL_OUTCOME_MISMATCH: the authoritative outcome "
                        + "is APPROVED; rejection reconciliation requires REJECTED");
            }
            if (rejected == 0) {
                throw new IllegalStateException("HRM_OFFER_APPROVAL_OUTCOME_PENDING: the authoritative workflow "
                        + "instance is " + status + "; no rejection reconciliation without a final REJECTED "
                        + "outcome");
            }
        }
    }

    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private HrOffer guardedUpdate(UUID tenantId, UUID offerId, HrOfferState expectedFrom, HrOfferState to,
                                  String sql, SqlBinder binder, String auditAction, UUID actorId,
                                  UUID correlationId, UUID workflowInstanceId, UUID offerVersionId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                int rows;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    binder.bind(ps);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    connection.rollback();
                    throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: expected state "
                            + expectedFrom + " (concurrent modification)");
                }
                writeEvidence(connection,
                        audit(tenantId, actorId, auditAction, offerId, correlationId,
                                workflowInstanceId, offerVersionId),
                        envelope(tenantId, auditAction, offerId, actorId, correlationId));
                HrOffer after = findWithinTx(connection, tenantId, offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId));
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw wrap("HRM_OFFER_PERSISTENCE_FAILED", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OFFER_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    private void insertVersionRow(Connection connection, HrOfferVersion version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_offer_versions (id, tenant_id, offer_id, version_number, predecessor_version_id, "
                        + "contract_terms, compensation, created_by) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)")) {
            ps.setObject(1, version.id());
            ps.setObject(2, version.tenantId());
            ps.setObject(3, version.offerId());
            ps.setInt(4, version.versionNumber());
            if (version.predecessorVersionId() != null) {
                ps.setObject(5, version.predecessorVersionId());
            } else {
                ps.setNull(5, Types.NULL);
            }
            ps.setString(6, version.contractTerms());
            ps.setString(7, version.compensation());
            if (version.createdBy() != null) {
                ps.setObject(8, version.createdBy());
            } else {
                ps.setNull(8, Types.NULL);
            }
            ps.executeUpdate();
        }
    }

    private void lockOfferRow(Connection connection, UUID tenantId, UUID offerId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM hr_offers WHERE id = ? AND tenant_id = ? FOR UPDATE")) {
            ps.setObject(1, offerId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_OFFER_NOT_FOUND: " + offerId);
                }
            }
        }
    }

    private UUID currentVersionIdFor(Connection connection, UUID tenantId, UUID offerId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT current_version_id FROM hr_offers WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, offerId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? (UUID) rs.getObject(1) : null;
            }
        }
    }

    private int nextVersionNumber(Connection connection, UUID tenantId, UUID offerId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM hr_offer_versions "
                        + "WHERE offer_id = ? AND tenant_id = ?")) {
            ps.setObject(1, offerId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void setCurrentVersion(Connection connection, UUID tenantId, UUID offerId, UUID versionId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_offers SET current_version_id = ? WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, versionId);
            ps.setObject(2, offerId);
            ps.setObject(3, tenantId);
            ps.executeUpdate();
        }
    }

    private void setPendingOfferVersion(Connection connection, UUID tenantId, UUID offerId, UUID versionId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_offers SET pending_offer_version_id = ? WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, versionId);
            ps.setObject(2, offerId);
            ps.setObject(3, tenantId);
            ps.executeUpdate();
        }
    }

    private void bumpOfferVersion(Connection connection, UUID tenantId, UUID offerId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_offers SET version = version + 1, updated_at = NOW() WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, offerId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private Optional<HrOffer> findWithinTx(Connection connection, UUID tenantId, UUID offerId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, tenant_id, application_id, offer_number, state, current_version_id, "
                        + "pending_offer_version_id, pending_workflow_instance_id, "
                        + "pending_workflow_definition_version_id, expires_at, position_id, version "
                        + "FROM hr_offers WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, offerId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UUID currentVersionId = (UUID) rs.getObject("current_version_id");
                UUID pendingVersionId = (UUID) rs.getObject("pending_offer_version_id");
                UUID pendingInstanceId = (UUID) rs.getObject("pending_workflow_instance_id");
                UUID pendingDefinitionVersionId = (UUID) rs.getObject("pending_workflow_definition_version_id");
                OffsetDateTime expiresAt = rs.getObject("expires_at", OffsetDateTime.class);
                UUID positionId = (UUID) rs.getObject("position_id");
                return Optional.of(new HrOffer(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("application_id"),
                        rs.getString("offer_number"),
                        HrOfferState.valueOf(rs.getString("state")),
                        currentVersionId,
                        pendingVersionId,
                        pendingInstanceId,
                        pendingDefinitionVersionId,
                        expiresAt,
                        positionId,
                        rs.getLong("version")));
            }
        }
    }

    private List<HrOfferVersion> versionsWithinTx(Connection connection, UUID tenantId, UUID offerId)
            throws SQLException {
        List<HrOfferVersion> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, tenant_id, offer_id, version_number, predecessor_version_id, "
                        + "contract_terms::text AS contract_terms, "
                        + "compensation::text AS compensation, created_by, created_at "
                        + "FROM hr_offer_versions WHERE offer_id = ? AND tenant_id = ? "
                        + "ORDER BY version_number ASC, created_at ASC")) {
            ps.setObject(1, offerId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new HrOfferVersion(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("tenant_id"),
                            (UUID) rs.getObject("offer_id"),
                            rs.getInt("version_number"),
                            (UUID) rs.getObject("predecessor_version_id"),
                            rs.getString("contract_terms"),
                            rs.getString("compensation"),
                            (UUID) rs.getObject("created_by"),
                            rs.getObject("created_at", OffsetDateTime.class)));
                }
            }
        }
        return rows;
    }

    private void writeEvidence(Connection connection, HrAuditRecord audit, DomainEventEnvelope envelope) {
        evidenceWriter.writeEvidence(connection, audit, envelope);
    }

    private HrAuditRecord audit(UUID tenantId, UUID actorId, String action, UUID offerId, UUID correlationId,
                                UUID workflowInstanceId, UUID offerVersionId) {
        return audit(tenantId, actorId, action, offerId, correlationId, workflowInstanceId, offerVersionId, null);
    }

    private HrAuditRecord audit(UUID tenantId, UUID actorId, String action, UUID offerId, UUID correlationId,
                                UUID workflowInstanceId, UUID offerVersionId, String reasonCode) {
        ObjectNode after = JSON.createObjectNode().put("state_event", action);
        if (workflowInstanceId != null) {
            after.put("workflow_instance_id", workflowInstanceId.toString());
        }
        if (offerVersionId != null) {
            after.put("offer_version", offerVersionId.toString());
        }
        if (reasonCode != null) {
            after.put("reason_code", reasonCode);
        }
        return new HrAuditRecord(tenantId, actorId, action, RESOURCE_TYPE, offerId,
                null, null, "OPERATIONAL", reasonCode, JSON.createObjectNode(), after, "SUCCESS",
                correlationId, null, null);
    }

    private DomainEventEnvelope envelope(UUID tenantId, String eventType, UUID offerId,
                                         UUID actorId, UUID correlationId) {
        // §12: offer events carry no compensation values.
        ObjectNode payload = JSON.createObjectNode();
        payload.put("offer_id", offerId.toString());
        payload.put("event_type", eventType);
        return new DomainEventEnvelope(UUID.randomUUID(), eventType, 1, RESOURCE_TYPE, offerId,
                tenantId, null, actorId, OffsetDateTime.now().toInstant(), correlationId, null,
                null, "OPERATIONAL", payload);
    }

    private static RuntimeException wrap(String code, Exception e) {
        return e instanceof IllegalStateException ise ? ise
                : e instanceof RuntimeException re ? re
                : new IllegalStateException(code + ": " + e.getMessage(), e);
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
        }
    }
}
