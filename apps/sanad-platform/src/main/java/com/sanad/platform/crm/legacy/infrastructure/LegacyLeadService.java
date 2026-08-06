package com.sanad.platform.crm.legacy.infrastructure;

import com.sanad.platform.crm.web.UpdateLeadStatusRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.sanad.platform.crm.legacy.infrastructure.LegacySupport.*;

@Service
public class LegacyLeadService {

    private final LegacySupport support;

    public LegacyLeadService(LegacySupport support) {
        this.support = support;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLead(Authentication authentication, UUID leadId) {
        UUID tenantId = support.tenantId(authentication);
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<>(support.one("crm_leads", tenantId, leadId, "CRM lead not found"));
        return result;
    }

    @Transactional
    public Map<String, Object> changeLeadStatus(
            Authentication authentication, UUID leadId, UpdateLeadStatusRequest request) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        Map<String, Object> lead = support.one("crm_leads", tenantId, leadId, "CRM lead not found");
        String current = String.valueOf(lead.get("status"));
        String next = request.status().trim().toUpperCase(Locale.ROOT);
        if (!leadTransitionAllowed(current, next)) {
            throw conflict("Invalid CRM lead status transition: " + current + " -> " + next);
        }
        Instant now = Instant.now();
        support.jdbc.update(
                "UPDATE crm_leads SET status=:status,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id",
                support.context(tenantId, actorId, leadId, now).addValue("status", next));
        support.timeline(tenantId, "LEAD", leadId, "crm.lead.status_changed",
                "Lead status changed to " + next, "CRM_LEAD", leadId, actorId, now);
        return getLead(authentication, leadId);
    }

    static boolean leadTransitionAllowed(String current, String next) {
        if (current.equals(next)) return true;
        return switch (current) {
            case "NEW" -> Set.of("ASSIGNED", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED").contains(next);
            case "ASSIGNED" -> Set.of("CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED").contains(next);
            case "CONTACTED" -> Set.of("QUALIFIED", "DISQUALIFIED", "ARCHIVED").contains(next);
            case "QUALIFIED" -> Set.of("DISQUALIFIED", "ARCHIVED").contains(next);
            case "DISQUALIFIED" -> "ARCHIVED".equals(next);
            default -> false;
        };
    }
}
