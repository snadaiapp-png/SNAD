package com.sanad.platform.crm.platform;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.workflow", name = "enabled", havingValue = "true")
public class CrmPrivacyDeadlineDispatcher {

    private final JdbcTemplate jdbc;

    public CrmPrivacyDeadlineDispatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "15m")
    @Transactional
    public void dispatchApproachingDeadlines() {
        jdbc.update("""
            INSERT INTO crm_platform.event_outbox
                (tenant_id, aggregate_type, aggregate_id, event_type,
                 routing_key, payload, headers)
            SELECT request.tenant_id,
                   'PRIVACY_REQUEST',
                   request.id,
                   'PrivacyRequestDeadlineApproaching',
                   'crm.workflow.privacy.deadline',
                   jsonb_build_object(
                       'privacyRequestId', request.id,
                       'requestType', request.request_type,
                       'status', request.status,
                       'dueAt', request.due_at,
                       'assignedTo', request.assigned_to),
                   jsonb_build_object('priority', 'HIGH')
              FROM crm_platform.privacy_request request
             WHERE request.status NOT IN ('COMPLETED','REJECTED','FAILED')
               AND request.due_at <= now() + interval '48 hours'
               AND NOT EXISTS (
                   SELECT 1
                     FROM crm_platform.event_outbox existing
                    WHERE existing.tenant_id=request.tenant_id
                      AND existing.aggregate_type='PRIVACY_REQUEST'
                      AND existing.aggregate_id=request.id
                      AND existing.event_type='PrivacyRequestDeadlineApproaching'
                      AND existing.created_at >= now() - interval '12 hours'
               )
            """);
    }
}
