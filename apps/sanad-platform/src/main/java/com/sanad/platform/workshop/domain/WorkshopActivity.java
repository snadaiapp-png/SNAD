package com.sanad.platform.workshop.domain;

import com.sanad.platform.shared.api.exceptions.BusinessRuleException;
import com.sanad.platform.tenant.domain.Tenant;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workshop_activities")
public class WorkshopActivity {

    public enum Type { COMMENT, CHECKLIST, TIME, ATTACHMENT, STATUS_CHANGE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_workshop_activity_tenant"))
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "workshop_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", nullable = false,
                    insertable = false, updatable = false)
    })
    private Workshop workshop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "work_item_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", nullable = false,
                    insertable = false, updatable = false)
    })
    private WorkshopWorkItem workItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private Type type;

    @Column(name = "body", length = 4000)
    private String body;

    @Column(name = "minutes")
    private Integer minutes;

    @Column(name = "external_uri", length = 1000)
    private String externalUri;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_by", columnDefinition = "uuid")
    private UUID completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkshopActivity() {}

    public WorkshopActivity(Tenant tenant, Workshop workshop, WorkshopWorkItem workItem,
                            Type type, String body, Integer minutes, String externalUri,
                            Instant startedAt, Instant endedAt, UUID createdBy) {
        this.tenant = tenant;
        this.workshop = workshop;
        this.workItem = workItem;
        this.type = type;
        this.body = normalize(body);
        this.externalUri = normalize(externalUri);
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.completed = false;
        validateAndResolveMinutes(minutes);
    }

    private void validateAndResolveMinutes(Integer suppliedMinutes) {
        if (type == null) throw new BusinessRuleException("Activity type is required.");
        switch (type) {
            case COMMENT, CHECKLIST, STATUS_CHANGE -> {
                if (body == null) throw new BusinessRuleException("Activity body is required for " + type + ".");
                minutes = null;
            }
            case ATTACHMENT -> {
                if (externalUri == null) throw new BusinessRuleException("Attachment URI is required.");
                minutes = null;
            }
            case TIME -> {
                int resolved = suppliedMinutes == null ? calculateMinutes(startedAt, endedAt) : suppliedMinutes;
                if (resolved <= 0) throw new BusinessRuleException("Time activity minutes must be positive.");
                minutes = resolved;
            }
        }
    }

    private static int calculateMinutes(Instant start, Instant end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessRuleException("Valid start and end timestamps are required for time activities.");
        }
        long value = Duration.between(start, end).toMinutes();
        if (value > Integer.MAX_VALUE) throw new BusinessRuleException("Time activity duration is too large.");
        return (int) value;
    }

    public void complete(UUID userId, Instant now) {
        if (type != Type.CHECKLIST) throw new BusinessRuleException("Only checklist activities can be completed.");
        if (!completed) {
            completed = true;
            completedBy = userId;
            completedAt = now == null ? Instant.now() : now;
        }
    }

    private static String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public Workshop getWorkshop() { return workshop; }
    public WorkshopWorkItem getWorkItem() { return workItem; }
    public Type getType() { return type; }
    public String getBody() { return body; }
    public Integer getMinutes() { return minutes; }
    public String getExternalUri() { return externalUri; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public boolean isCompleted() { return completed; }
    public UUID getCompletedBy() { return completedBy; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
