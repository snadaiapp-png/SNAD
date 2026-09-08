package com.sanad.platform.hr.recruitment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.domain.HrInterviewState;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrInterviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * HRM-G1 T6 — HrInterview application service (design §5.1, §13.1 rows
 * 13-16).
 *
 * <p>Scheduling requires INTERVIEW.SCHEDULE; every panel member must resolve
 * to a tenant user (IAM). Outcomes follow the §5.1 machine
 * SCHEDULED → DONE(PASSED|FAILED) | CANCELLED | NO_SHOW (terminal) under
 * INTERVIEW.RECORD_OUTCOME. Feedback is per-participant, versioned in place;
 * participants may always submit their own scorecard, otherwise
 * INTERVIEW.MANAGE is required.</p>
 */
@Service
public class HrInterviewService {

    private static final Set<String> MODES = Set.of("ONSITE", "REMOTE", "PHONE");
    private static final Set<String> OUTCOMES = Set.of("PASSED", "FAILED");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcHrInterviewRepository repository;
    private final RecruitmentAuthorizationPort authorization;

    @Autowired
    public HrInterviewService(JdbcHrInterviewRepository repository,
                              RecruitmentAuthorizationPort authorization) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public UUID schedule(HrCommandContext ctx, UUID applicationId, OffsetDateTime plannedAt,
                         String mode, Integer durationMinutes, List<UUID> panelUserIds) {
        authorization.requireInterviewSchedule(ctx, null);
        if (plannedAt == null) {
            throw new IllegalArgumentException("HRM_INTERVIEW_SCHEDULE_REQUIRED: planned_at is mandatory");
        }
        if (mode == null || !MODES.contains(mode)) {
            throw new IllegalArgumentException("HRM_INTERVIEW_MODE_INVALID: " + mode);
        }
        if (durationMinutes != null && durationMinutes <= 0) {
            throw new IllegalArgumentException("HRM_INTERVIEW_DURATION_INVALID: must be positive");
        }
        List<UUID> panel = panelUserIds == null ? List.of() : List.copyOf(panelUserIds);
        if (panel.isEmpty()) {
            throw new IllegalArgumentException("HRM_INTERVIEW_PANEL_REQUIRED: at least one participant");
        }
        if (!repository.allUsersInTenant(ctx.tenantId(), panel)) {
            throw new IllegalStateException(
                    "HRM_INTERVIEW_PANEL_NOT_IN_TENANT: panel members must resolve within the tenant");
        }
        return repository.insert(ctx.tenantId(), applicationId, plannedAt, mode, durationMinutes,
                panel, ctx.actorUserId(), ctx.correlationId());
    }

    public void recordOutcome(HrCommandContext ctx, UUID interviewId, String state, String outcome) {
        authorization.requireInterviewRecordOutcome(ctx, interviewId);
        JdbcHrInterviewRepository.InterviewRow interview = load(ctx, interviewId);
        if (interview.state() != HrInterviewState.SCHEDULED) {
            throw new IllegalStateException("HRM_INTERVIEW_STATE_CONFLICT: " + interview.state()
                    + " is terminal (§5.1 outcome machine)");
        }
        HrInterviewState target;
        try {
            target = HrInterviewState.valueOf(state);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("HRM_INTERVIEW_STATE_INVALID: " + state);
        }
        if (target == HrInterviewState.DONE) {
            if (outcome == null || !OUTCOMES.contains(outcome)) {
                throw new IllegalArgumentException(
                        "HRM_INTERVIEW_OUTCOME_REQUIRED: DONE requires outcome PASSED|FAILED");
            }
        } else if (target == HrInterviewState.CANCELLED || target == HrInterviewState.NO_SHOW) {
            if (outcome != null) {
                throw new IllegalArgumentException(
                        "HRM_INTERVIEW_OUTCOME_FORBIDDEN: " + target + " carries no PASSED|FAILED outcome");
            }
        } else {
            throw new IllegalArgumentException("HRM_INTERVIEW_STATE_INVALID: " + state);
        }
        repository.recordOutcome(ctx.tenantId(), interviewId, target.name(), outcome,
                ctx.actorUserId(), ctx.correlationId());
    }

    public void submitFeedback(HrCommandContext ctx, UUID interviewId, String scorecardJson) {
        JsonNode scorecard;
        try {
            scorecard = scorecardJson == null ? null : JSON.readTree(scorecardJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("HRM_INTERVIEW_FEEDBACK_INVALID: scorecard must be valid JSON");
        }
        if (scorecard == null || !scorecard.isObject() || scorecard.isEmpty()) {
            throw new IllegalArgumentException(
                    "HRM_INTERVIEW_FEEDBACK_INVALID: scorecard must be a non-empty JSON object");
        }
        UUID participantRowId = repository.participantRowId(ctx.tenantId(), interviewId, ctx.actorUserId());
        if (participantRowId == null) {
            // §13.1 row 16: participant OR INTERVIEW.MANAGE.
            authorization.requireInterviewManage(ctx, interviewId);
        }
        load(ctx, interviewId);
        UUID effectiveParticipant = participantRowId != null ? participantRowId
                : firstParticipantRow(ctx.tenantId(), interviewId);
        repository.upsertFeedback(ctx.tenantId(), interviewId, effectiveParticipant, scorecard,
                ctx.actorUserId(), ctx.correlationId());
    }

    public JdbcHrInterviewRepository.InterviewRow get(HrCommandContext ctx, UUID interviewId) {
        authorization.requireInterviewManage(ctx, interviewId);
        return load(ctx, interviewId);
    }

    // --- internals ---

    private JdbcHrInterviewRepository.InterviewRow load(HrCommandContext ctx, UUID interviewId) {
        JdbcHrInterviewRepository.InterviewRow row = repository.find(ctx.tenantId(), interviewId);
        if (row == null) {
            throw new IllegalStateException(
                    "HRM_INTERVIEW_NOT_FOUND: " + interviewId + " is not visible to this tenant context");
        }
        return row;
    }

    private UUID firstParticipantRow(UUID tenantId, UUID interviewId) {
        List<UUID> users = repository.participantUserIds(tenantId, interviewId);
        if (users.isEmpty()) {
            throw new IllegalStateException("HRM_INTERVIEW_PANEL_EMPTY: no participant row to attach feedback");
        }
        UUID rowId = repository.participantRowId(tenantId, interviewId, users.get(0));
        if (rowId == null) {
            throw new IllegalStateException("HRM_INTERVIEW_PARTICIPANT_MISSING");
        }
        return rowId;
    }
}
