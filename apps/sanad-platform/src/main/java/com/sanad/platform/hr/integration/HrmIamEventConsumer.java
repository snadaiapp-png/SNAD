package com.sanad.platform.hr.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Employment-derived IAM policy consumer (WS4 Task 7).
 *
 * <p>Consumes the canonical employment lifecycle events
 * {@code HRM.EMPLOYEE.ACTIVATED.v1 / SUSPENDED.v1 / TERMINATED.v1 /
 * USER_LINKED.v1} from the HR outbox (registered as an
 * {@link HrOutboxEventConsumer}, so the delivery worker's AT_LEAST_ONCE
 * dispatch drives this consumer) and applies the {@link HrmIamAccessPolicy}
 * decision through the {@link IamEmploymentAccessPort} — never through
 * direct database writes into IAM/user-account tables.</p>
 *
 * <p>Consumer semantics are IDEMPOTENT: consumption is claimed in
 * {@code hr_idempotency_records} (unique on tenant + principal + operation +
 * key) BEFORE any side effect, and completed after it. A duplicate
 * at-least-once delivery hits the unique claim and performs no duplicate
 * side effect. A failed application deletes its claim so the retry can
 * proceed.</p>
 */
@Component
public class HrmIamEventConsumer implements HrOutboxEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(HrmIamEventConsumer.class);

    public static final Set<String> ACCEPTED_EVENT_TYPES = Set.of(
            HrmIamAccessPolicy.EMPLOYEE_ACTIVATED,
            HrmIamAccessPolicy.EMPLOYEE_SUSPENDED,
            HrmIamAccessPolicy.EMPLOYEE_TERMINATED,
            HrmIamAccessPolicy.EMPLOYEE_USER_LINKED);

    private static final String OPERATION_CODE = "HRM.IAM_POLICY_CONSUMER";
    /** NIL-UUID principal marks SYSTEM consumption (no human principal). */
    private static final UUID SYSTEM_PRINCIPAL = new UUID(0L, 0L);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final IamEmploymentAccessPort iamPort;
    private final HrmIamAccessPolicy policy;

    @Autowired
    public HrmIamEventConsumer(
            DataSource dataSource,
            ObjectMapper objectMapper,
            IamEmploymentAccessPort iamPort,
            HrmIamAccessPolicy policy) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.iamPort = Objects.requireNonNull(iamPort, "iamPort");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public void onEvent(HrOutboxEvent event) {
        if (!ACCEPTED_EVENT_TYPES.contains(event.eventType())) {
            return; // not an IAM-policy event — owned by other consumers
        }
        UUID tenantId = event.tenantId();
        if (!tryClaimConsumption(tenantId, event)) {
            log.info("HRM IAM policy event {} already consumed — duplicate at-least-once delivery ignored",
                    event.eventId());
            return;
        }
        try {
            apply(event);
            completeConsumption(tenantId, event.eventId(), "APPLIED");
        } catch (RuntimeException e) {
            releaseClaim(tenantId, event.eventId());
            throw e;
        }
    }

    private void apply(HrOutboxEvent event) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(event.payload() == null ? "{}" : event.payload());
        } catch (Exception e) {
            throw new IllegalStateException("HRM_IAM_EVENT_PAYLOAD_UNPARSEABLE: " + event.eventId(), e);
        }
        UUID personId = uuidFrom(payload, "personId");
        UUID userId = uuidFrom(payload, "userId");
        UUID employmentId = uuidFrom(payload, "employmentId");
        if (personId == null || userId == null) {
            throw new IllegalStateException("HRM_IAM_EVENT_PAYLOAD_INCOMPLETE: personId and userId are required in "
                    + event.eventType() + " payload (eventId=" + event.eventId() + ")");
        }

        HrmIamAccessPolicy.Decision decision = policy.decide(event.tenantId(), personId, userId, event.eventType());
        switch (decision.outcome()) {
            case DISABLE -> {
                iamPort.disableUserAccount(event.tenantId(), userId,
                        decision.reason() + " (employmentId=" + employmentId + ")");
                log.info("HRM IAM policy disabled account {} for tenant {} ({})", userId, event.tenantId(),
                        decision.reason());
            }
            case ENABLE -> {
                iamPort.enableUserAccount(event.tenantId(), userId,
                        decision.reason() + " (employmentId=" + employmentId + ")");
                log.info("HRM IAM policy enabled account {} for tenant {} ({})", userId, event.tenantId(),
                        decision.reason());
            }
            case NO_OP -> log.info("HRM IAM policy no-op for account {} ({})", userId, decision.reason());
            case FAIL_CLOSED -> log.warn("HRM IAM policy fail-closed for account {} ({})", userId,
                    decision.reason());
        }
    }

    // ==================== idempotent consumption claims ====================

    private boolean tryClaimConsumption(UUID tenantId, HrOutboxEvent event) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                boolean claimed;
                try (PreparedStatement ps = connection.prepareStatement(CLAIM_SQL)) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, SYSTEM_PRINCIPAL);
                    ps.setString(3, OPERATION_CODE);
                    ps.setString(4, event.eventId().toString());
                    ps.setString(5, fingerprint(event));
                    claimed = ps.executeUpdate() == 1;
                }
                connection.commit();
                return claimed;
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_IAM_CONSUMER_CLAIM_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_IAM_CONSUMER_CLAIM_FAILED: " + e.getMessage(), e);
        }
    }

    private void completeConsumption(UUID tenantId, UUID eventId, String result) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(COMPLETE_SQL)) {
                    ps.setInt(1, 200);
                    ps.setString(2, "{\"consumerResult\":\"" + result + "\"}");
                    ps.setObject(3, tenantId);
                    ps.setObject(4, SYSTEM_PRINCIPAL);
                    ps.setString(5, OPERATION_CODE);
                    ps.setString(6, eventId.toString());
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_IAM_CONSUMER_COMPLETE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_IAM_CONSUMER_COMPLETE_FAILED: " + e.getMessage(), e);
        }
    }

    private void releaseClaim(UUID tenantId, UUID eventId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(RELEASE_SQL)) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, SYSTEM_PRINCIPAL);
                    ps.setString(3, OPERATION_CODE);
                    ps.setString(4, eventId.toString());
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_IAM_CONSUMER_RELEASE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_IAM_CONSUMER_RELEASE_FAILED: " + e.getMessage(), e);
        }
    }

    private static String fingerprint(HrOutboxEvent event) {
        String material = event.eventType() + "|" + event.eventId() + "|" + event.tenantId();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HRM_IAM_CONSUMER_FINGERPRINT_FAILED", e);
        }
    }

    private static UUID uuidFrom(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        return node == null || node.isNull() ? null : UUID.fromString(node.asText());
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private static final String CLAIM_SQL = """
            INSERT INTO hr_idempotency_records
                (tenant_id, principal_id, operation_code, idempotency_key, request_fingerprint)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String COMPLETE_SQL = """
            UPDATE hr_idempotency_records SET response_status = ?, response_body = ?::jsonb
            WHERE tenant_id = ? AND principal_id = ? AND operation_code = ? AND idempotency_key = ?
            """;

    private static final String RELEASE_SQL = """
            DELETE FROM hr_idempotency_records
            WHERE tenant_id = ? AND principal_id = ? AND operation_code = ? AND idempotency_key = ?
            """;
}
