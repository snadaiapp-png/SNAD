package com.sanad.platform.subscription.plan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlanVersionService}.
 *
 * <p>Covers the mission-critical invariants:
 * <ul>
 *   <li>Activating a new version must NOT mutate subscriber rows (version pinning)</li>
 *   <li>Only one ACTIVE version per plan</li>
 *   <li>Draft → Active → Retired lifecycle legality</li>
 *   <li>Date-based version resolution</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanVersionService — versioning invariants")
class PlanVersionServiceTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private PlanVersionRepository repository;

    private PlanVersionService service;

    private static final UUID PLAN_ID = UUID.fromString("c3000000-0000-0000-0000-000000000001");
    private static final UUID V1_ID = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID V2_ID = UUID.fromString("d1000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        service = new PlanVersionService(jdbc, repository);
    }

    private PlanVersionEntity activeVersion(UUID id, int number) {
        PlanVersionEntity v = new PlanVersionEntity();
        v.setId(id);
        v.setPlanId(PLAN_ID);
        v.setVersionNumber(number);
        v.setStatus("ACTIVE");
        v.setCurrencyCode("SAR");
        v.setMonthlyPriceMinor(29900L);
        v.setAnnualPriceMinor(299000L);
        v.setTrialDays(14);
        v.setMaxUsers(25);
        v.setMaxOrganizations(5);
        v.setStorageMb(51200L);
        v.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
        v.setCreatedAt(Instant.now());
        v.setUpdatedAt(Instant.now());
        return v;
    }

    @Test
    @DisplayName("activate: retires the current ACTIVE version and activates the draft")
    void activate_retiresCurrentAndActivatesDraft() {
        PlanVersionEntity v1 = activeVersion(V1_ID, 1);
        PlanVersionEntity v2 = activeVersion(V2_ID, 2);
        v2.setStatus("DRAFT");
        when(repository.findById(V2_ID)).thenReturn(Optional.of(v2));
        when(repository.findActiveByPlanId(PLAN_ID)).thenReturn(Optional.of(v1));

        PlanVersionEntity result = service.activate(V2_ID);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(repository).updateStatusAndWindow(eq(V1_ID), eq("RETIRED"), any(), any());
        verify(repository).updateStatusAndWindow(eq(V2_ID), eq("ACTIVE"), any(), any());
    }

    @Test
    @DisplayName("activate: subscribers stay pinned — tenant_subscriptions rows are never updated")
    void activate_doesNotTouchSubscriberRows() {
        PlanVersionEntity v2 = activeVersion(V2_ID, 2);
        v2.setStatus("DRAFT");
        when(repository.findById(V2_ID)).thenReturn(Optional.of(v2));
        when(repository.findActiveByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        service.activate(V2_ID);

        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test
    @DisplayName("activate: rejects activating an already ACTIVE version")
    void activate_rejectsAlreadyActive() {
        PlanVersionEntity v1 = activeVersion(V1_ID, 1);
        when(repository.findById(V1_ID)).thenReturn(Optional.of(v1));

        assertThatThrownBy(() -> service.activate(V1_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("activate: rejects activating a RETIRED version")
    void activate_rejectsRetired() {
        PlanVersionEntity v1 = activeVersion(V1_ID, 1);
        v1.setStatus("RETIRED");
        when(repository.findById(V1_ID)).thenReturn(Optional.of(v1));

        assertThatThrownBy(() -> service.activate(V1_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETIRED");
    }

    @Test
    @DisplayName("createDraft: version number is max+1 for the plan")
    void createDraft_assignsIncrementingVersionNumber() {
        when(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM saas_plans WHERE id = ?"), eq(Long.class), eq(PLAN_ID)))
                .thenReturn(1L);
        when(jdbc.queryForObject(
                eq("SELECT COALESCE(MAX(version_number), 0) FROM plan_versions WHERE plan_id = ?"),
                eq(Integer.class), eq(PLAN_ID))).thenReturn(1);

        PlanVersionEntity draft = service.createDraft(
                PLAN_ID, "SAR", 39900L, 399000L, 14, 50, 10, 102400L);

        assertThat(draft.getVersionNumber()).isEqualTo(2);
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        verify(repository).insert(draft);
    }

    @Test
    @DisplayName("createDraft: rejects unknown plan")
    void createDraft_rejectsUnknownPlan() {
        when(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM saas_plans WHERE id = ?"), eq(Long.class), eq(PLAN_ID)))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.createDraft(
                PLAN_ID, "SAR", 100L, 1000L, 0, 5, 1, 1024L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan");
    }

    @Test
    @DisplayName("resolveVersionForDate: returns the ACTIVE version when it covers the date")
    void resolveVersion_returnsActiveCoveringDate() {
        PlanVersionEntity v1 = activeVersion(V1_ID, 1);
        when(repository.findActiveByPlanId(PLAN_ID)).thenReturn(Optional.of(v1));

        Optional<PlanVersionEntity> resolved = service.resolveVersionForDate(
                PLAN_ID, Instant.parse("2026-06-15T00:00:00Z"));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(V1_ID);
    }

    @Test
    @DisplayName("resolveVersionForDate: empty when the ACTIVE version starts after the date")
    void resolveVersion_emptyWhenNotYetEffective() {
        PlanVersionEntity v1 = activeVersion(V1_ID, 1);
        v1.setEffectiveFrom(Instant.parse("2027-01-01T00:00:00Z"));
        when(repository.findActiveByPlanId(PLAN_ID)).thenReturn(Optional.of(v1));

        Optional<PlanVersionEntity> resolved = service.resolveVersionForDate(
                PLAN_ID, Instant.parse("2026-06-15T00:00:00Z"));

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("listForPlan: returns versions newest-first")
    void listForPlan_ordersByVersionDesc() {
        when(repository.findByPlanIdOrderByVersionNumberDesc(PLAN_ID))
                .thenReturn(List.of(activeVersion(V2_ID, 2), activeVersion(V1_ID, 1)));

        List<PlanVersionEntity> versions = service.listForPlan(PLAN_ID);

        assertThat(versions).extracting(PlanVersionEntity::getVersionNumber)
                .containsExactly(2, 1);
    }
}
