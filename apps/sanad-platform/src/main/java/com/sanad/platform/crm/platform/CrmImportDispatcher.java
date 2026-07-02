package com.sanad.platform.crm.platform;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.messaging", name = "enabled", havingValue = "true")
public class CrmImportDispatcher {

    private final JdbcTemplate jdbc;

    public CrmImportDispatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "10s")
    @Transactional
    public void dispatch() {
        jdbc.update("""
            INSERT INTO crm_platform.event_outbox
                (tenant_id, aggregate_type, aggregate_id, event_type, routing_key, payload, headers)
            SELECT job.tenant_id,
                   'IMPORT_JOB',
                   job.id,
                   'CrmImportRequested',
                   'crm.import.execute',
                   jsonb_build_object(
                       'jobId', job.id,
                       'entityType', job.entity_type,
                       'objectKey', job.object_key,
                       'mapping', job.mapping,
                       'checkpoint', job.checkpoint),
                   '{}'::jsonb
              FROM crm_platform.import_job job
             WHERE job.status='READY'
               AND NOT EXISTS (
                   SELECT 1 FROM crm_platform.event_outbox event
                    WHERE event.aggregate_type='IMPORT_JOB'
                      AND event.aggregate_id=job.id
                      AND event.event_type='CrmImportRequested'
                      AND event.status IN ('PENDING','PROCESSING','PUBLISHED')
               )
             ORDER BY job.created_at
             LIMIT 20
            """);
        jdbc.update("""
            UPDATE crm_platform.import_job job
               SET status='RUNNING', started_at=COALESCE(started_at, now())
             WHERE status='READY'
               AND EXISTS (
                   SELECT 1 FROM crm_platform.event_outbox event
                    WHERE event.aggregate_type='IMPORT_JOB'
                      AND event.aggregate_id=job.id
                      AND event.event_type='CrmImportRequested'
               )
            """);
    }
}
