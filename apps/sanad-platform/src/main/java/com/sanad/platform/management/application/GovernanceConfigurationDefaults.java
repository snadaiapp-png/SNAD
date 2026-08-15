package com.sanad.platform.management.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry of safe default values for governance configuration keys.
 *
 * <p>These defaults are used by {@link GovernanceConfigurationService}
 * when a tenant has not set an override. They mirror the hard-coded
 * constants that were used before V20260815_24 (e.g. SLA grace periods,
 * alert severity thresholds, score weights).
 *
 * <p>Registered once at bean construction time. The map is read-only
 * after construction.
 */
@Component
public class GovernanceConfigurationDefaults {

    private final Map<String, String> stringDefaults = new HashMap<>();
    private final Map<String, Integer> integerDefaults = new HashMap<>();
    private final Map<String, Boolean> booleanDefaults = new HashMap<>();
    private final Map<String, Duration> durationDefaults = new HashMap<>();

    public GovernanceConfigurationDefaults() {
        // SLA thresholds
        durationDefaults.put("sla.decision.due.default", Duration.ofHours(168));      // 7 days
        durationDefaults.put("sla.escalation.overdue.grace", Duration.ofHours(24));
        durationDefaults.put("sla.risk.critical.reassess.interval", Duration.ofHours(72));
        durationDefaults.put("sla.issue.critical.resolve.target", Duration.ofHours(48));

        // Alert thresholds
        integerDefaults.put("alert.dedup.window.seconds", 300);
        integerDefaults.put("alert.max.active.per.tenant", 1000);

        // Escalation thresholds
        integerDefaults.put("escalation.auto.critical.risk.threshold", 5);   // severity+impact >= 5
        integerDefaults.put("escalation.auto.critical.issue.threshold", 5);

        // Operational
        integerDefaults.put("command.center.max.dashboard.rows", 50);
        integerDefaults.put("report.snapshot.retention.days", 365);

        // Governance behavior
        booleanDefaults.put("governance.sod.enforce.self_approval_block", true);
        booleanDefaults.put("governance.audit.immutable", true);

        // Reporting configuration
        stringDefaults.put("report.executive.format.default", "JSON");
        stringDefaults.put("report.executive.period.default", "P30D");   // last 30 days
    }

    public String getStringDefault(String key) { return stringDefaults.get(key); }
    public Integer getIntegerDefault(String key) { return integerDefaults.get(key); }
    public Boolean getBooleanDefault(String key) { return booleanDefaults.get(key); }
    public Duration getDurationDefault(String key) { return durationDefaults.get(key); }

    /** All registered keys (for diagnostics). */
    public Map<String, String> allDefaults() {
        Map<String, String> all = new HashMap<>();
        stringDefaults.forEach(all::put);
        integerDefaults.forEach((k, v) -> all.put(k, String.valueOf(v)));
        booleanDefaults.forEach((k, v) -> all.put(k, String.valueOf(v)));
        durationDefaults.forEach((k, v) -> all.put(k, v.toString()));
        return all;
    }
}
