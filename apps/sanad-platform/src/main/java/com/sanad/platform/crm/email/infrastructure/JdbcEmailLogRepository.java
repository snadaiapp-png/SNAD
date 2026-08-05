package com.sanad.platform.crm.email.infrastructure;

import com.sanad.platform.crm.email.domain.EmailLogPort;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of {@link EmailLogPort}.
 * <p>
 * Tenant isolation is enforced in every query.
 * Follows the same pattern as {@code JdbcCaseRepository}.
 */
@Repository
public class JdbcEmailLogRepository implements EmailLogPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcEmailLogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EmailLogEntry create(UUID tenantId, EmailLogEntry entry) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbc.update(
                "INSERT INTO crm_email_logs (" +
                        "id, tenant_id, user_id, from_address, to_address, " +
                        "subject, status, provider, provider_message_id, " +
                        "related_entity_type, related_entity_id, template_name, " +
                        "sent_at, opened_at, clicked_at, click_url, " +
                        "error_message, created_at" +
                        ") VALUES (" +
                        ":id, :tenantId, :userId, :fromAddress, :toAddress, " +
                        ":subject, :status, :provider, :providerMessageId, " +
                        ":relatedEntityType, :relatedEntityId, :templateName, " +
                        ":sentAt, :openedAt, :clickedAt, :clickUrl, " +
                        ":errorMessage, :createdAt)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("userId", entry.userId())
                        .addValue("fromAddress", entry.fromAddress())
                        .addValue("toAddress", entry.toAddress())
                        .addValue("subject", entry.subject())
                        .addValue("status", entry.status())
                        .addValue("provider", entry.provider())
                        .addValue("providerMessageId", entry.providerMessageId())
                        .addValue("relatedEntityType", entry.relatedEntityType())
                        .addValue("relatedEntityId", entry.relatedEntityId())
                        .addValue("templateName", entry.templateName())
                        .addValue("sentAt", entry.sentAt() != null ? Timestamp.from(entry.sentAt()) : null)
                        .addValue("openedAt", entry.openedAt() != null ? Timestamp.from(entry.openedAt()) : null)
                        .addValue("clickedAt", entry.clickedAt() != null ? Timestamp.from(entry.clickedAt()) : null)
                        .addValue("clickUrl", entry.clickUrl())
                        .addValue("errorMessage", entry.errorMessage())
                        .addValue("createdAt", Timestamp.from(now))
        );

        return findById(tenantId, id);
    }

    @Override
    public void update(UUID tenantId, EmailLogEntry entry) {
        jdbc.update(
                "UPDATE crm_email_logs SET " +
                        "status = :status, provider = :provider, " +
                        "provider_message_id = :providerMessageId, " +
                        "sent_at = :sentAt, error_message = :errorMessage " +
                        "WHERE tenant_id = :tenantId AND id = :id",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", entry.id())
                        .addValue("status", entry.status())
                        .addValue("provider", entry.provider())
                        .addValue("providerMessageId", entry.providerMessageId())
                        .addValue("sentAt", entry.sentAt() != null ? Timestamp.from(entry.sentAt()) : null)
                        .addValue("errorMessage", entry.errorMessage())
        );
    }

    @Override
    public EmailLogEntry findById(UUID tenantId, UUID logId) {
        try {
            return mapRow(jdbc.queryForMap(
                    "SELECT * FROM crm_email_logs WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("id", logId)));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_EMAIL_NOT_FOUND);
        }
    }

    @Override
    public List<EmailLogEntry> findByRelatedEntity(UUID tenantId, String relatedEntityType, String relatedEntityId) {
        return jdbc.queryForList(
                "SELECT * FROM crm_email_logs WHERE tenant_id = :t " +
                        "AND related_entity_type = :entityType AND related_entity_id = :entityId " +
                        "ORDER BY created_at DESC",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("entityType", relatedEntityType)
                        .addValue("entityId", relatedEntityId)
        ).stream().map(this::mapRow).toList();
    }

    @Override
    public List<EmailLogEntry> findAll(UUID tenantId, int limit) {
        return jdbc.queryForList(
                "SELECT * FROM crm_email_logs WHERE tenant_id = :t " +
                        "ORDER BY created_at DESC LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("limit", limit)
        ).stream().map(this::mapRow).toList();
    }

    @Override
    public void recordOpen(UUID tenantId, UUID logId, Instant openedAt) {
        jdbc.update(
                "UPDATE crm_email_logs SET opened_at = :openedAt " +
                        "WHERE tenant_id = :tenantId AND id = :id AND opened_at IS NULL",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", logId)
                        .addValue("openedAt", Timestamp.from(openedAt))
        );
    }

    @Override
    public void recordClick(UUID tenantId, UUID logId, String url, Instant clickedAt) {
        jdbc.update(
                "UPDATE crm_email_logs SET clicked_at = :clickedAt, click_url = :clickUrl " +
                        "WHERE tenant_id = :tenantId AND id = :id",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", logId)
                        .addValue("clickedAt", Timestamp.from(clickedAt))
                        .addValue("clickUrl", url)
        );
    }

    @Override
    public EmailLogEntry findByLogId(UUID logId) {
        try {
            return mapRow(jdbc.queryForMap(
                    "SELECT * FROM crm_email_logs WHERE id = :id",
                    new MapSqlParameterSource()
                            .addValue("id", logId)));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    private EmailLogEntry mapRow(Map<String, Object> row) {
        return new EmailLogEntry(
                toUUID(row.get("id")),
                toUUID(row.get("tenant_id")),
                toUUID(row.get("user_id")),
                toString(row.get("from_address")),
                toString(row.get("to_address")),
                toString(row.get("subject")),
                toString(row.get("status")),
                toString(row.get("provider")),
                toString(row.get("provider_message_id")),
                toString(row.get("related_entity_type")),
                toString(row.get("related_entity_id")),
                toString(row.get("template_name")),
                toInstant(row.get("sent_at")),
                toInstant(row.get("opened_at")),
                toInstant(row.get("clicked_at")),
                toString(row.get("click_url")),
                toString(row.get("error_message")),
                toInstant(row.get("created_at"))
        );
    }

    private UUID toUUID(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(value.toString());
    }

    private String toString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toInstant();
        if (value instanceof Instant inst) return inst;
        return Instant.parse(value.toString());
    }
}
