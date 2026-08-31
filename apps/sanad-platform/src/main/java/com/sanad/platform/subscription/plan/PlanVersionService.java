package com.sanad.platform.subscription.plan;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan versioning service — Draft → Active → Retired lifecycle with
 * subscriber pinning.
 *
 * <p>Activating a new version never touches {@code tenant_subscriptions}
 * rows: subscribers keep the version they contracted until an explicit plan
 * change, renewal, or migration. This is the mission-critical invariant
 * proven by {@code PlanVersionServiceTest}.
 */
@Service
public class PlanVersionService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETIRED = "RETIRED";

    private final JdbcTemplate jdbc;
    private final PlanVersionRepository repository;

    public PlanVersionService(JdbcTemplate jdbc, PlanVersionRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

    @Transactional
    public PlanVersionEntity createDraft(UUID planId, String currencyCode,
                                         long monthlyPriceMinor, long annualPriceMinor,
                                         int trialDays, int maxUsers, int maxOrganizations,
                                         long storageMb) {
        Long planCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_plans WHERE id = ?", Long.class, planId);
        if (planCount == null || planCount == 0) {
            throw new IllegalArgumentException("Unknown plan: " + planId);
        }
        if (currencyCode == null || !currencyCode.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currencyCode must be a 3-letter ISO code");
        }
        Integer maxVersion = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) FROM plan_versions WHERE plan_id = ?",
                Integer.class, planId);

        PlanVersionEntity draft = new PlanVersionEntity();
        draft.setId(UUID.randomUUID());
        draft.setPlanId(planId);
        draft.setVersionNumber((maxVersion == null ? 0 : maxVersion) + 1);
        draft.setStatus(STATUS_DRAFT);
        draft.setCurrencyCode(currencyCode);
        draft.setMonthlyPriceMinor(monthlyPriceMinor);
        draft.setAnnualPriceMinor(annualPriceMinor);
        draft.setTrialDays(trialDays);
        draft.setMaxUsers(maxUsers);
        draft.setMaxOrganizations(maxOrganizations);
        draft.setStorageMb(storageMb);
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        repository.insert(draft);
        return draft;
    }

    /**
     * Activates a DRAFT version. The currently ACTIVE version of the same plan
     * (if any) is retired with {@code effectiveTo = now}. Subscriber rows are
     * never modified — existing subscribers stay pinned to their contracted
     * version.
     */
    @Transactional
    public PlanVersionEntity activate(UUID planVersionId) {
        PlanVersionEntity version = repository.findById(planVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan version: " + planVersionId));
        if (!STATUS_DRAFT.equals(version.getStatus())) {
            throw new IllegalStateException(
                    "Only DRAFT versions can be activated (current: " + version.getStatus() + ")");
        }
        Instant now = Instant.now();
        repository.findActiveByPlanId(version.getPlanId())
                .filter(current -> !current.getId().equals(planVersionId))
                .ifPresent(current -> repository.updateStatusAndWindow(
                        current.getId(), STATUS_RETIRED, null, now));
        repository.updateStatusAndWindow(planVersionId, STATUS_ACTIVE, now, null);
        version.setStatus(STATUS_ACTIVE);
        version.setEffectiveFrom(now);
        version.setEffectiveTo(null);
        version.setUpdatedAt(now);
        return version;
    }

    @Transactional
    public PlanVersionEntity retire(UUID planVersionId) {
        PlanVersionEntity version = repository.findById(planVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan version: " + planVersionId));
        if (!STATUS_ACTIVE.equals(version.getStatus())) {
            throw new IllegalStateException(
                    "Only ACTIVE versions can be retired (current: " + version.getStatus() + ")");
        }
        Instant now = Instant.now();
        repository.updateStatusAndWindow(planVersionId, STATUS_RETIRED, null, now);
        version.setStatus(STATUS_RETIRED);
        version.setEffectiveTo(now);
        version.setUpdatedAt(now);
        return version;
    }

    @Transactional(readOnly = true)
    public List<PlanVersionEntity> listForPlan(UUID planId) {
        return repository.findByPlanIdOrderByVersionNumberDesc(planId);
    }

    /**
     * Resolves the version in effect at the given instant: the plan's ACTIVE
     * version when its effective window covers {@code at}.
     */
    @Transactional(readOnly = true)
    public Optional<PlanVersionEntity> resolveVersionForDate(UUID planId, Instant at) {
        return repository.findActiveByPlanId(planId)
                .filter(v -> v.getEffectiveFrom() == null || !v.getEffectiveFrom().isAfter(at))
                .filter(v -> v.getEffectiveTo() == null || v.getEffectiveTo().isAfter(at));
    }

    @Transactional(readOnly = true)
    public Optional<PlanVersionEntity> findVersion(UUID planVersionId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM plan_versions WHERE id = ?",
                    PlanVersionRepository.ROW_MAPPER, planVersionId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
