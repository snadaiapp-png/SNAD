package com.sanad.platform.hr.api.v2.performance;

import com.sanad.platform.hr.performance.PerformanceCapabilities;
import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G3 performance-goal HTTP boundary.
 *
 * <p>Every endpoint is capability-scoped and resolves SELF/TEAM against the
 * canonical Person -> ACTIVE Employment -> PRIMARY Assignment graph. TEAM
 * access uses {@link HrEmploymentScopeResolver}; there is no legacy-manager
 * fallback. PostgreSQL FORCE RLS remains authoritative and is bound with a
 * transaction-local tenant GUC before data access.</p>
 */
@RestController
@RequestMapping("/api/v2/hr/performance/goals")
@Tag(name = "HRM-G3 Performance Goals")
public class HrPerformanceGoalV2Controller {

    private final JdbcTemplate jdbc;
    private final HrEmploymentScopeResolver scopeResolver;
    private final TenantRlsTransactionContext rlsContext;

    public HrPerformanceGoalV2Controller(
            JdbcTemplate jdbc,
            HrEmploymentScopeResolver scopeResolver,
            TenantRlsTransactionContext rlsContext) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
        this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver is required");
        this.rlsContext = Objects.requireNonNull(rlsContext, "rlsContext is required");
    }

    @GetMapping
    @Operation(operationId = "hrPerformanceGoalsList", summary = "List own performance goals")
    @RequireCapability(PerformanceCapabilities.GOAL_SELF_VIEW)
    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(Authentication authentication) {
        Actor actor = bind(authentication);
        UUID employmentId = scopeResolver.requireSelfEmployment(actor.tenantId(), actor.userId());
        return listForEmployment(actor.tenantId(), employmentId);
    }

    @GetMapping("/{goalId}")
    @Operation(operationId = "hrPerformanceGoalGet", summary = "Get an own performance goal")
    @RequireCapability(PerformanceCapabilities.GOAL_SELF_VIEW)
    @Transactional(readOnly = true)
    public GoalResponse getGoal(Authentication authentication, @PathVariable UUID goalId) {
        Actor actor = bind(authentication);
        UUID employmentId = scopeResolver.requireSelfEmployment(actor.tenantId(), actor.userId());
        return requireGoal(actor.tenantId(), employmentId, goalId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "hrPerformanceGoalCreate", summary = "Create an own performance goal")
    @RequireCapability(PerformanceCapabilities.GOAL_SELF_UPDATE)
    @Transactional
    public GoalResponse createGoal(
            Authentication authentication,
            @Valid @RequestBody GoalWriteRequest request) {
        Actor actor = bind(authentication);
        UUID employmentId = scopeResolver.requireSelfEmployment(actor.tenantId(), actor.userId());
        return createForEmployment(actor.tenantId(), employmentId, request);
    }

    @PutMapping("/{goalId}")
    @Operation(operationId = "hrPerformanceGoalUpdate", summary = "Update an own performance goal")
    @RequireCapability(PerformanceCapabilities.GOAL_SELF_UPDATE)
    @Transactional
    public GoalResponse updateGoal(
            Authentication authentication,
            @PathVariable UUID goalId,
            @Valid @RequestBody GoalWriteRequest request) {
        Actor actor = bind(authentication);
        UUID employmentId = scopeResolver.requireSelfEmployment(actor.tenantId(), actor.userId());
        updateForEmployment(actor.tenantId(), employmentId, goalId, request);
        return requireGoal(actor.tenantId(), employmentId, goalId);
    }

    @PatchMapping("/{goalId}/progress")
    @Operation(operationId = "hrPerformanceGoalProgressUpdate", summary = "Update own goal progress")
    @RequireCapability(PerformanceCapabilities.GOAL_SELF_UPDATE)
    @Transactional
    public GoalResponse updateGoalProgress(
            Authentication authentication,
            @PathVariable UUID goalId,
            @Valid @RequestBody GoalProgressRequest request) {
        Actor actor = bind(authentication);
        UUID employmentId = scopeResolver.requireSelfEmployment(actor.tenantId(), actor.userId());
        updateProgress(actor.tenantId(), employmentId, goalId, request.progress());
        return requireGoal(actor.tenantId(), employmentId, goalId);
    }

    @GetMapping("/team/{employmentId}")
    @Operation(operationId = "hrPerformanceTeamGoalsList", summary = "List a direct report's performance goals")
    @RequireCapability(PerformanceCapabilities.GOAL_TEAM_MANAGE)
    @Transactional(readOnly = true)
    public List<GoalResponse> listTeamGoals(
            Authentication authentication,
            @PathVariable UUID employmentId) {
        Actor actor = bind(authentication);
        scopeResolver.requireManagedEmployment(actor.tenantId(), actor.userId(), employmentId);
        return listForEmployment(actor.tenantId(), employmentId);
    }

    @PostMapping("/team/{employmentId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "hrPerformanceTeamGoalCreate", summary = "Create a direct report's performance goal")
    @RequireCapability(PerformanceCapabilities.GOAL_TEAM_MANAGE)
    @Transactional
    public GoalResponse createTeamGoal(
            Authentication authentication,
            @PathVariable UUID employmentId,
            @Valid @RequestBody GoalWriteRequest request) {
        Actor actor = bind(authentication);
        scopeResolver.requireManagedEmployment(actor.tenantId(), actor.userId(), employmentId);
        return createForEmployment(actor.tenantId(), employmentId, request);
    }

    @PutMapping("/team/{employmentId}/{goalId}")
    @Operation(operationId = "hrPerformanceTeamGoalUpdate", summary = "Update a direct report's performance goal")
    @RequireCapability(PerformanceCapabilities.GOAL_TEAM_MANAGE)
    @Transactional
    public GoalResponse updateTeamGoal(
            Authentication authentication,
            @PathVariable UUID employmentId,
            @PathVariable UUID goalId,
            @Valid @RequestBody GoalWriteRequest request) {
        Actor actor = bind(authentication);
        scopeResolver.requireManagedEmployment(actor.tenantId(), actor.userId(), employmentId);
        updateForEmployment(actor.tenantId(), employmentId, goalId, request);
        return requireGoal(actor.tenantId(), employmentId, goalId);
    }

    @PatchMapping("/team/{employmentId}/{goalId}/progress")
    @Operation(operationId = "hrPerformanceTeamGoalProgressUpdate", summary = "Update a direct report's goal progress")
    @RequireCapability(PerformanceCapabilities.GOAL_TEAM_MANAGE)
    @Transactional
    public GoalResponse updateTeamGoalProgress(
            Authentication authentication,
            @PathVariable UUID employmentId,
            @PathVariable UUID goalId,
            @Valid @RequestBody GoalProgressRequest request) {
        Actor actor = bind(authentication);
        scopeResolver.requireManagedEmployment(actor.tenantId(), actor.userId(), employmentId);
        updateProgress(actor.tenantId(), employmentId, goalId, request.progress());
        return requireGoal(actor.tenantId(), employmentId, goalId);
    }

    private Actor bind(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        rlsContext.applyForCurrentTransaction(tenantId);
        return new Actor(tenantId, userId);
    }

    private List<GoalResponse> listForEmployment(UUID tenantId, UUID employmentId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, person_id, employment_id, title, metric, target_value,
                       progress, status, starts_on, ends_on, created_at, updated_at
                  FROM hr_performance_goals
                 WHERE tenant_id = ? AND employment_id = ?
                 ORDER BY starts_on DESC, created_at DESC, id
                """,
                this::mapGoal,
                tenantId, employmentId);
    }

    private GoalResponse requireGoal(UUID tenantId, UUID employmentId, UUID goalId) {
        List<GoalResponse> rows = jdbc.query(
                """
                SELECT id, tenant_id, person_id, employment_id, title, metric, target_value,
                       progress, status, starts_on, ends_on, created_at, updated_at
                  FROM hr_performance_goals
                 WHERE tenant_id = ? AND employment_id = ? AND id = ?
                """,
                this::mapGoal,
                tenantId, employmentId, goalId);
        if (rows.size() != 1) {
            throw new AccessDeniedException("Performance goal is not visible to the authenticated principal");
        }
        return rows.get(0);
    }

    private GoalResponse createForEmployment(UUID tenantId, UUID employmentId, GoalWriteRequest request) {
        validatePeriod(request.startsOn(), request.endsOn());
        UUID personId = canonicalPersonId(tenantId, employmentId);
        UUID goalId = UUID.randomUUID();
        int progress = request.progress() == null ? 0 : request.progress();

        jdbc.update(
                """
                INSERT INTO hr_performance_goals
                    (id, tenant_id, person_id, employment_id, title, metric, target_value,
                     progress, status, starts_on, ends_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                """,
                goalId, tenantId, personId, employmentId,
                request.title().trim(), request.metric().trim(), request.targetValue().trim(),
                progress, request.startsOn(), request.endsOn());

        return requireGoal(tenantId, employmentId, goalId);
    }

    private void updateForEmployment(
            UUID tenantId,
            UUID employmentId,
            UUID goalId,
            GoalWriteRequest request) {
        validatePeriod(request.startsOn(), request.endsOn());
        int progress = request.progress() == null ? 0 : request.progress();
        int updated = jdbc.update(
                """
                UPDATE hr_performance_goals
                   SET title = ?, metric = ?, target_value = ?, progress = ?,
                       starts_on = ?, ends_on = ?, updated_at = NOW()
                 WHERE tenant_id = ? AND employment_id = ? AND id = ?
                """,
                request.title().trim(), request.metric().trim(), request.targetValue().trim(),
                progress, request.startsOn(), request.endsOn(),
                tenantId, employmentId, goalId);
        requireSingleMutation(updated);
    }

    private void updateProgress(UUID tenantId, UUID employmentId, UUID goalId, int progress) {
        int updated = jdbc.update(
                """
                UPDATE hr_performance_goals
                   SET progress = ?, updated_at = NOW()
                 WHERE tenant_id = ? AND employment_id = ? AND id = ?
                """,
                progress, tenantId, employmentId, goalId);
        requireSingleMutation(updated);
    }

    private UUID canonicalPersonId(UUID tenantId, UUID employmentId) {
        List<UUID> personIds = jdbc.query(
                """
                SELECT person_id
                  FROM hr_employees
                 WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'
                """,
                (rs, rowNum) -> rs.getObject("person_id", UUID.class),
                tenantId, employmentId);
        if (personIds.size() != 1) {
            throw new AccessDeniedException("Target employment is not an active canonical employment");
        }
        return personIds.get(0);
    }

    private void requireSingleMutation(int updated) {
        if (updated != 1) {
            throw new AccessDeniedException("Performance goal is not mutable by the authenticated principal");
        }
    }

    private void validatePeriod(LocalDate startsOn, LocalDate endsOn) {
        if (endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("HRM_GOAL_PERIOD_INVALID: endsOn must not precede startsOn");
        }
    }

    private GoalResponse mapGoal(ResultSet rs, int rowNum) throws SQLException {
        return new GoalResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("person_id", UUID.class),
                rs.getObject("employment_id", UUID.class),
                rs.getString("title"),
                rs.getString("metric"),
                rs.getString("target_value"),
                rs.getInt("progress"),
                rs.getString("status"),
                rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class),
                rs.getObject("created_at", Instant.class),
                rs.getObject("updated_at", Instant.class));
    }

    private record Actor(UUID tenantId, UUID userId) {}

    public record GoalWriteRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 128) String metric,
            @NotBlank @Size(max = 255) String targetValue,
            @Min(0) @Max(100) Integer progress,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn) {}

    public record GoalProgressRequest(@Min(0) @Max(100) int progress) {}

    public record GoalResponse(
            UUID id,
            UUID tenantId,
            UUID personId,
            UUID employmentId,
            String title,
            String metric,
            String targetValue,
            int progress,
            String status,
            LocalDate startsOn,
            LocalDate endsOn,
            Instant createdAt,
            Instant updatedAt) {}
}
