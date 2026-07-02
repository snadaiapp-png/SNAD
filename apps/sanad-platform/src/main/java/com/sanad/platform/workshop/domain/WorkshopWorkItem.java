package com.sanad.platform.workshop.domain;

import com.sanad.platform.shared.api.exceptions.BusinessRuleException;
import com.sanad.platform.tenant.domain.Tenant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workshop_work_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workshop_items_code", columnNames = {"tenant_id", "workshop_id", "code"})
})
public class WorkshopWorkItem {

    public enum Status { BACKLOG, READY, IN_PROGRESS, BLOCKED, IN_REVIEW, DONE, CANCELLED }
    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_workshop_items_tenant"))
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "workshop_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", nullable = false,
                    insertable = false, updatable = false)
    })
    private Workshop workshop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "parent_item_id", referencedColumnName = "id"),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id",
                    insertable = false, updatable = false)
    })
    private WorkshopWorkItem parentItem;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "title", nullable = false, length = 240)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private Priority priority;

    @Column(name = "primary_assignee_user_id", columnDefinition = "uuid")
    private UUID primaryAssigneeUserId;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "actual_minutes", nullable = false)
    private int actualMinutes;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "blocked_reason", length = 1000)
    private String blockedReason;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WorkshopWorkItem() {}

    public WorkshopWorkItem(Tenant tenant, Workshop workshop, WorkshopWorkItem parentItem,
                            String code, String title, String description, Priority priority,
                            Instant dueAt, Integer estimatedMinutes, int sequenceNo, UUID createdBy) {
        if (estimatedMinutes != null && estimatedMinutes < 0) {
            throw new BusinessRuleException("Estimated minutes must be zero or positive.");
        }
        if (sequenceNo < 0) throw new BusinessRuleException("Sequence number must be zero or positive.");
        this.tenant = tenant;
        this.workshop = workshop;
        this.parentItem = parentItem;
        this.code = normalizeCode(code);
        this.title = normalizeRequired(title, "Work item title");
        this.description = normalizeOptional(description);
        this.priority = priority == null ? Priority.MEDIUM : priority;
        this.dueAt = dueAt;
        this.estimatedMinutes = estimatedMinutes;
        this.sequenceNo = sequenceNo;
        this.status = Status.BACKLOG;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void transitionTo(Status target, String reason, boolean dependenciesDone,
                             boolean checklistComplete, Instant now) {
        if (target == null || target == status) return;
        boolean allowed = switch (status) {
            case BACKLOG -> target == Status.READY || target == Status.CANCELLED;
            case READY -> target == Status.IN_PROGRESS || target == Status.BLOCKED || target == Status.CANCELLED;
            case IN_PROGRESS -> target == Status.BLOCKED || target == Status.IN_REVIEW || target == Status.DONE || target == Status.CANCELLED;
            case BLOCKED -> target == Status.READY || target == Status.IN_PROGRESS || target == Status.CANCELLED;
            case IN_REVIEW -> target == Status.IN_PROGRESS || target == Status.BLOCKED || target == Status.DONE;
            case DONE, CANCELLED -> false;
        };
        if (!allowed) throw new BusinessRuleException("Invalid work item transition: " + status + " -> " + target);
        if ((target == Status.READY || target == Status.IN_PROGRESS || target == Status.DONE) && !dependenciesDone) {
            throw new BusinessRuleException("Work item dependencies are not complete.");
        }
        if (target == Status.DONE && !checklistComplete) {
            throw new BusinessRuleException("All checklist activities must be complete before DONE.");
        }
        if (target == Status.BLOCKED && (reason == null || reason.isBlank())) {
            throw new BusinessRuleException("Blocked reason is required.");
        }
        status = target;
        blockedReason = target == Status.BLOCKED ? reason.trim() : null;
        updatedAt = now == null ? Instant.now() : now;
    }

    public void setPrimaryAssignee(UUID userId) {
        if (primaryAssigneeUserId == null) primaryAssigneeUserId = userId;
        updatedAt = Instant.now();
    }

    public void addActualMinutes(int minutes) {
        if (minutes <= 0) throw new BusinessRuleException("Time entry minutes must be positive.");
        actualMinutes = Math.addExact(actualMinutes, minutes);
        updatedAt = Instant.now();
    }

    private static String normalizeCode(String value) {
        String normalized = normalizeRequired(value, "Work item code").toUpperCase();
        if (!normalized.matches("[A-Z0-9][A-Z0-9._-]{1,79}")) {
            throw new BusinessRuleException("Work item code must contain 2-80 uppercase letters, digits, dot, underscore or hyphen.");
        }
        return normalized;
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new BusinessRuleException(field + " is required.");
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public Workshop getWorkshop() { return workshop; }
    public WorkshopWorkItem getParentItem() { return parentItem; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public Priority getPriority() { return priority; }
    public UUID getPrimaryAssigneeUserId() { return primaryAssigneeUserId; }
    public Instant getDueAt() { return dueAt; }
    public Integer getEstimatedMinutes() { return estimatedMinutes; }
    public int getActualMinutes() { return actualMinutes; }
    public int getSequenceNo() { return sequenceNo; }
    public String getBlockedReason() { return blockedReason; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
