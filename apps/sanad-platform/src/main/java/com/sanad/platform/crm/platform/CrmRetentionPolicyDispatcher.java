package com.sanad.platform.crm.platform;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.workflow", name = "enabled", havingValue = "true")
public class CrmRetentionPolicyDispatcher {

    private final JdbcTemplate jdbc;

    public CrmRetentionPolicyDispatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 15 2 * * *", zone = "UTC")
    @Transactional
    public void dispatchDailyEvaluation() {
        jdbc.update("""
            INSERT INTO crm_platform.event_outbox
                (tenant_id, aggregate_type, aggregate_id, event_type,
                 routing_key, payload, headers)
            SELECT policy.tenant_id,
                   'RETENTION_POLICY',
                   policy.id,
                   'RetentionPolicyEvaluationRequested',
                   'crm.workflow.retention.evaluate',
                   jsonb_build_object(
                       'policyId', policy.id,
                       'dataClass', policy.data_class,
                       'jurisdiction', policy.jurisdiction,
                       'retentionDays', policy.retention_days,
                       'action', policy.action),
                   jsonb_build_object(
                       'humanApprovalRequired', true,
                       'automaticDestructionAllowed', false)
              FROM crm_platform.retention_policy policy
             WHERE policy.active=true
               AND NOT EXISTS (
                   SELECT 1
                     FROM crm_platform.event_outbox existing
                    WHERE existing.tenant_id=policy.tenant_id
                      AND existing.aggregate_type='RETENTION_POLICY'
                      AND existing.aggregate_id=policy.id
                      AND existing.event_type='RetentionPolicyEvaluationRequested'
                      AND existing.created_at >= now() - interval '23 hours'
               )
            """);
    }
}
