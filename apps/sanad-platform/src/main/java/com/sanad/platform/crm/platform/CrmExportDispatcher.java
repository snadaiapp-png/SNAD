package com.sanad.platform.crm.platform;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.messaging", name = "enabled", havingValue = "true")
public class CrmExportDispatcher {

    private final JdbcTemplate jdbc;

    public CrmExportDispatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "10s")
    @Transactional
    public void dispatch() {
        jdbc.update("""
            INSERT INTO crm_platform.event_outbox
                (tenant_id, aggregate_type, aggregate_id, event_type, routing_key, payload, headers)
            SELECT job.tenant_id,
                   'EXPORT_JOB',
                   job.id,
                   'CrmExportRequested',
                   'crm.export.execute',
                   jsonb_build_object(
                       'jobId', job.id,
                       'entityType', job.entity_type,
                       'filter', job.filter,
                       'fields', job.fields,
                       'requestedBy', job.requested_by,
                       'approvedBy', job.approved_by),
                   jsonb_build_object('enhancedAudit', true)
              FROM crm_platform.export_job job
             WHERE job.status='APPROVED'
               AND job.approved_by IS NOT NULL
               AND NOT EXISTS (
                   SELECT 1 FROM crm_platform.event_outbox event
                    WHERE event.aggregate_type='EXPORT_JOB'
                      AND event.aggregate_id=job.id
                      AND event.event_type='CrmExportRequested'
                      AND event.status IN ('PENDING','PROCESSING','PUBLISHED')
               )
             ORDER BY job.created_at
             LIMIT 20
            """);
        jdbc.update("""
            UPDATE crm_platform.export_job job
               SET status='RUNNING'
             WHERE status='APPROVED'
               AND approved_by IS NOT NULL
               AND EXISTS (
                   SELECT 1 FROM crm_platform.event_outbox event
                    WHERE event.aggregate_type='EXPORT_JOB'
                      AND event.aggregate_id=job.id
                      AND event.event_type='CrmExportRequested'
               )
            """);
    }
}
