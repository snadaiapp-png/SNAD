package com.sanad.platform.ops.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op adapter that logs alerts instead of dispatching them.
 * <p>
 * Activated when alerting is disabled or no provider is configured.
 * Useful for development, testing, and environments without external alerting.
 * <p>
 * CRM-008 remediation: production-grade alerting for operational events.
 */
@Component
@ConditionalOnProperty(prefix = "snad.ops.alerting", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class NoOpOperationalAlertAdapter implements OperationalAlertPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpOperationalAlertAdapter.class);

    @Override
    public void dispatch(OperationalAlert alert) {
        log.info("[ALERT-NOOP] {} | {} | {} | {}",
                alert.severity(), alert.category(), alert.summary(), alert.details());
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
