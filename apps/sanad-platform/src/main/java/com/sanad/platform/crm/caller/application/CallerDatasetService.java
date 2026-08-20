package com.sanad.platform.crm.caller.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Server-side caller dataset projection (G8-03 §32–§44, G8-ADR-008 Option B).
 *
 * <p>Minimum-PII delta source for the offline caller index: entries carry the
 * HMAC lookup token (never the plaintext number), masked/stripped identity for
 * RESTRICTED records, and tombstones for archived/inactive methods and owners.
 * Privacy filtering happens SERVER-side — the device never filters PII it
 * should not have received (§40).
 */
@Service
public class CallerDatasetService {

    private static final Logger log = LoggerFactory.getLogger(CallerDatasetService.class);

    /** Bumps when the dataset contract changes — client mismatch ⇒ full rebuild. */
    public static final int DATASET_VERSION = 1;
    private static final int PAGE_LIMIT = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final CallerDatasetTokenProvider tokens;
    private final MeterRegistry meterRegistry;

    public CallerDatasetService(NamedParameterJdbcTemplate jdbc,
                                CallerDatasetTokenProvider tokens,
                                MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.meterRegistry = meterRegistry;
    }

    /** One dataset entry — the ONLY thing sent to the device (§39). */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record CallerDatasetRecord(
            String lookupToken,
            String entityType,
            UUID entityId,
            String displayName,
            UUID accountId,
            String accountName,
            String phoneLabel,
            Boolean verified,
            Boolean preferred,
            String lifecycleStatus,
            String privacyLevel,
            long syncVersion,
            Instant updatedAt,
            boolean deleted) {
    }

    public record CallerDatasetDelta(
            int datasetVersion,
            boolean fullResyncRequired,
            String nextCursor,
            boolean hasMore,
            Instant serverTimestamp,
            String datasetKey,
            List<CallerDatasetRecord> entries) {
    }

    /**
     * Snapshot/delta over the canonical source ordered by
     * {@code (updated_ms, id)} — stable, no dup, no skip, retry-safe.
     */
    @Transactional(readOnly = true)
    public CallerDatasetDelta delta(UUID tenantId, long cursorMs, UUID cursorId,
                                    int limit, boolean keyMissing) {
        int bounded = Math.min(limit <= 0 ? PAGE_LIMIT : limit, PAGE_LIMIT);
        List<Row> rows = new ArrayList<>();
        jdbc.query("""
                        SELECT cm.id AS method_id, cm.owner_type, cm.owner_id, cm.normalized_value,
                               cm.label, cm.preferred, cm.verified, cm.verification_status,
                               cm.privacy_classification, cm.status AS method_status, cm.updated_at,
                               c.id AS contact_id, c.display_name AS contact_name,
                               c.lifecycle_status AS contact_lifecycle, c.owner_user_id AS contact_owner,
                               a.id AS account_id, a.display_name AS account_name,
                               a.lifecycle_status AS account_lifecycle,
                               contact_account.id AS contact_account_id,
                               contact_account.display_name AS contact_account_name,
                               EXTRACT(EPOCH FROM cm.updated_at) * 1000 AS updated_ms
                        FROM crm_communication_methods cm
                        LEFT JOIN crm_contacts c
                               ON c.tenant_id = cm.tenant_id AND c.id = cm.contact_id
                        LEFT JOIN crm_accounts a
                               ON a.tenant_id = cm.tenant_id AND a.id = cm.account_id
                        LEFT JOIN crm_accounts contact_account
                               ON contact_account.tenant_id = cm.tenant_id
                              AND contact_account.id = c.account_id
                        WHERE cm.tenant_id = :tenantId
                          AND cm.method_type IN ('PHONE', 'MOBILE')
                          AND (
                                (cm.status = 'ACTIVE'
                                 AND ((cm.owner_type = 'PERSON' AND c.lifecycle_status = 'ACTIVE')
                                      OR (cm.owner_type = 'ACCOUNT' AND a.lifecycle_status = 'ACTIVE')))
                                OR cm.status IN ('INACTIVE', 'ARCHIVED')
                                OR (cm.owner_type = 'PERSON'
                                    AND c.lifecycle_status IN ('INACTIVE', 'ARCHIVED'))
                                OR (cm.owner_type = 'ACCOUNT'
                                    AND a.lifecycle_status IN ('INACTIVE', 'ARCHIVED'))
                              )
                          AND (:cursorMs = 0
                               OR ((EXTRACT(EPOCH FROM cm.updated_at) * 1000) > :cursorMs
                                   OR ((EXTRACT(EPOCH FROM cm.updated_at) * 1000) = :cursorMs
                                       AND cm.id > :cursorId)))
                        ORDER BY updated_ms ASC, cm.id ASC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("cursorMs", cursorMs)
                        .addValue("cursorId", cursorId == null ? UUID.randomUUID() : cursorId)
                        .addValue("limit", bounded + 1),
                rs -> {
                    String ownerType = rs.getString("owner_type");
                    boolean methodActive = "ACTIVE".equals(rs.getString("method_status"));
                    boolean ownerActive = "PERSON".equals(ownerType)
                            ? "ACTIVE".equals(rs.getString("contact_lifecycle"))
                            : "ACTIVE".equals(rs.getString("account_lifecycle"));
                    boolean eligible = methodActive && ownerActive;
                    String normalized = rs.getString("normalized_value");
                    String token = tokens.lookupToken(tenantId, normalized);
                    long updatedMs = rs.getLong("updated_ms");
                    UUID methodId = uuidOrNull(rs, "method_id");
                    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

                    if (!eligible) {
                        // Tombstone — the device removes/replaces its row (§42).
                        rows.add(new Row(updatedMs, methodId, new CallerDatasetRecord(
                                token, null, null, null, null, null, null,
                                null, null, null, null,
                                updatedMs, updatedAt, true)));
                        return;
                    }
                    String privacy = rs.getString("privacy_classification");
                    boolean restricted = "RESTRICTED".equals(privacy);
                    String displayName = restricted ? null : ("PERSON".equals(ownerType)
                            ? rs.getString("contact_name") : rs.getString("account_name"));
                    String accountName = restricted ? null : ("PERSON".equals(ownerType)
                            ? rs.getString("contact_account_name") : rs.getString("account_name"));
                    UUID accountId = restricted ? null : ("PERSON".equals(ownerType)
                            ? uuidOrNull(rs, "contact_account_id") : uuidOrNull(rs, "account_id"));
                    UUID entityId = "PERSON".equals(ownerType)
                            ? uuidOrNull(rs, "contact_id") : uuidOrNull(rs, "account_id");
                    rows.add(new Row(updatedMs, methodId, new CallerDatasetRecord(
                            token,
                            "PERSON".equals(ownerType) ? "CONTACT" : "ACCOUNT",
                            entityId,
                            displayName,
                            accountId,
                            accountName,
                            rs.getString("label"),
                            rs.getBoolean("verified"),
                            rs.getBoolean("preferred"),
                            "ACTIVE",
                            privacy,
                            updatedMs,
                            updatedAt,
                            false)));
                });

        boolean hasMore = rows.size() > bounded;
        List<Row> page = hasMore ? rows.subList(0, bounded) : rows;
        List<CallerDatasetRecord> records = page.stream().map(Row::record).toList();
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Row last = page.get(page.size() - 1);
            nextCursor = Base64.getUrlEncoder().encodeToString(
                    (last.updatedMs() + ":" + last.methodId()).getBytes());
        }
        meterRegistry.counter("caller_dataset_sync_total").increment();
        meterRegistry.counter("caller_dataset_entries").increment(records.size());
        Instant now = Instant.now();
        String datasetKey = keyMissing && tokens.isConfigured() ? tokens.tenantDatasetKey(tenantId) : null;
        if (datasetKey != null) {
            log.debug("CALLER_DATASET_KEY_ISSUED tenant={} (SecureStore on device)", tenantId);
        }
        return new CallerDatasetDelta(DATASET_VERSION, false, nextCursor, hasMore, now,
                datasetKey, records);
    }

    private static UUID uuidOrNull(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : UUID.fromString(value.toString());
    }

    private record Row(long updatedMs, UUID methodId, CallerDatasetRecord record) {
    }
}
