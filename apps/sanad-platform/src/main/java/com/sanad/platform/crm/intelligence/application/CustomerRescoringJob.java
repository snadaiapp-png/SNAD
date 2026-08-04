package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.intelligence.config.CustomerIntelligenceProperties;
import com.sanad.platform.crm.intelligence.domain.CustomerHealthDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled batch job that recalculates health scores for accounts whose
 * scores are stale (older than the configured rescore interval).
 *
 * <p>Only runs when {@code scheduling.enabled=true}. Iterates over all
 * accounts in the {@code crm_customer_scores} table whose most recent
 * HEALTH score predates the cutoff, then recalculates health indicators
 * and scores in configurable batch sizes.</p>
 */
@Component
public class CustomerRescoringJob {

    private static final Logger log = LoggerFactory.getLogger(CustomerRescoringJob.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerHealthDataPort healthDataPort;
    private final CustomerHealthService healthService;
    private final CustomerIntelligenceProperties properties;

    public CustomerRescoringJob(NamedParameterJdbcTemplate jdbc,
                                CustomerHealthDataPort healthDataPort,
                                CustomerHealthService healthService,
                                CustomerIntelligenceProperties properties) {
        this.jdbc = jdbc;
        this.healthDataPort = healthDataPort;
        this.healthService = healthService;
        this.properties = properties;
    }

    /**
     * Runs on a fixed delay determined by {@code sanad.intelligence.scoring.rescore-interval-minutes}.
     * The delay is expressed in milliseconds (interval * 60_000).
     */
    @Scheduled(fixedDelayString = "${sanad.intelligence.scoring.rescore-interval-minutes:360}000")
    public void runRescoreBatch() {
        int rescoreMinutes = properties.getScoring().getRescoreIntervalMinutes();
        int batchSize = properties.getScoring().getBatchSize();
        Instant cutoff = Instant.now().minus(rescoreMinutes, ChronoUnit.MINUTES);

        log.info("Starting batch rescore: cutoff={} ({} minutes ago), batchSize={}",
                cutoff, rescoreMinutes, batchSize);

        List<StaleAccount> staleAccounts = findStaleAccounts(cutoff);

        if (staleAccounts.isEmpty()) {
            log.info("No stale accounts found. Rescore batch complete.");
            return;
        }

        log.info("Found {} stale accounts to rescore", staleAccounts.size());

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < staleAccounts.size(); i += batchSize) {
            List<StaleAccount> batch = staleAccounts.subList(i, Math.min(i + batchSize, staleAccounts.size()));
            int batchNumber = (i / batchSize) + 1;
            int totalBatches = (int) Math.ceil((double) staleAccounts.size() / batchSize);

            log.info("Processing batch {}/{} ({} accounts)", batchNumber, totalBatches, batch.size());

            for (StaleAccount account : batch) {
                try {
                    rescoreAccount(account.tenantId(), account.accountId());
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.warn("Failed to rescore account {} in tenant {}: {}",
                            account.accountId(), account.tenantId(), e.getMessage());
                }
            }

            log.info("Batch {}/{} complete: {} succeeded, {} failed so far",
                    batchNumber, totalBatches, successCount, failCount);
        }

        log.info("Rescore batch finished: {} total accounts, {} succeeded, {} failed",
                staleAccounts.size(), successCount, failCount);
    }

    /**
     * Recalculates health score for a single account using live indicators.
     */
    private void rescoreAccount(UUID tenantId, UUID accountId) {
        CustomerHealthDataPort.HealthIndicators indicators =
                healthDataPort.getHealthIndicators(tenantId, accountId);

        // Use a nil UUID as actor since this is a system-triggered recalculation
        healthService.calculateHealth(
                tenantId, accountId, UUID.fromString("00000000-0000-0000-0000-000000000000"),
                indicators.daysSinceLastActivity(),
                indicators.openOpportunities(),
                indicators.totalPipeline(),
                indicators.meetingFreq30d(),
                indicators.responseTimeAvgHours(),
                "ACTIVE");

        log.debug("Rescored health for account {}: daysSinceLastActivity={}, openOpps={}, "
                        + "pipeline={}, meetings30d={}, responseTime={}h",
                accountId, indicators.daysSinceLastActivity(), indicators.openOpportunities(),
                indicators.totalPipeline(), indicators.meetingFreq30d(),
                indicators.responseTimeAvgHours());
    }

    /**
     * Finds all accounts whose latest HEALTH score predates the given cutoff.
     * Uses a DISTINCT ON query to get the most recent score per account,
     * then filters by the cutoff.
     */
    private List<StaleAccount> findStaleAccounts(Instant cutoff) {
        String sql = """
                SELECT DISTINCT ON (cs.tenant_id, cs.account_id)
                       cs.tenant_id, cs.account_id
                FROM crm_customer_scores cs
                WHERE cs.score_type = 'HEALTH'
                  AND cs.calculated_at < :cutoff
                ORDER BY cs.tenant_id, cs.account_id, cs.calculated_at DESC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(cutoff));

        try {
            return jdbc.query(sql, params, (rs, rowNum) ->
                    new StaleAccount(
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("account_id", UUID.class)));
        } catch (Exception e) {
            log.error("Failed to query stale accounts: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Read model for an account that needs rescoring.
     */
    private record StaleAccount(UUID tenantId, UUID accountId) {}
}
