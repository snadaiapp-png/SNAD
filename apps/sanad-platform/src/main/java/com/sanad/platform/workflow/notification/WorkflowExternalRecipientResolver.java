package com.sanad.platform.workflow.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves outbound recipients for EXTERNAL participants from the
 * authoritative CRM source of truth (GATE R2.9 / AD-7). Workflow never
 * stores a separate customer phone/email truth: EMAIL/WHATSAPP addresses
 * are derived from {@code crm_communication_methods} of the participant's
 * source entity (ACCOUNT or PERSON owner), requiring ACTIVE status and
 * verified=true, preferring preferred methods.
 */
@Service
public class WorkflowExternalRecipientResolver {

    private final JdbcTemplate jdbc;

    public WorkflowExternalRecipientResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ResolvedRecipient(String methodType, String normalizedValue,
                                    String displayValue, boolean verified,
                                    boolean preferred) {
    }

    /**
     * Resolves the best outbound address of the requested method type for a
     * participant. Ordering: verified DESC, preferred DESC, updated_at ASC
     * (deterministic tie-break). Empty when no verified method exists —
     * callers fail the intent closed (INVALID_RECIPIENT); an unverified
     * channel is never used for outbound customer contact.
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedRecipient> resolve(UUID tenantId, UUID participantId,
                                               String methodType) {
        List<ResolvedRecipient> rows = jdbc.query("""
                SELECT cm.method_type, cm.normalized_value, cm.display_value,
                       cm.verified, cm.preferred
                  FROM workflow_external_participants p
                  JOIN crm_communication_methods cm
                    ON cm.tenant_id = p.tenant_id
                   AND ((p.participant_type = 'CUSTOMER' AND cm.owner_type = 'ACCOUNT'
                         AND cm.account_id = p.source_entity_id)
                     OR (p.participant_type <> 'CUSTOMER' AND cm.owner_type = 'PERSON'
                         AND cm.contact_id = p.source_entity_id))
                 WHERE p.tenant_id = ? AND p.id = ?
                   AND UPPER(cm.method_type) = UPPER(?)
                   AND cm.status = 'ACTIVE'
                   AND cm.verified = TRUE
                 ORDER BY cm.verified DESC, cm.preferred DESC, cm.updated_at ASC, cm.id ASC
                 LIMIT 1
                """, (rs, i) -> new ResolvedRecipient(
                        rs.getString("method_type"),
                        rs.getString("normalized_value"),
                        rs.getString("display_value"),
                        rs.getBoolean("verified"),
                        rs.getBoolean("preferred")),
                tenantId, participantId, methodType);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
