package com.sanad.platform.workshop.domain;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.shared.api.exceptions.BusinessRuleException;
import com.sanad.platform.tenant.domain.Tenant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workshops", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workshops_tenant_code", columnNames = {"tenant_id", "code"})
})
public class Workshop {

    public enum Status { DRAFT, ACTIVE, PAUSED, COMPLETED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_workshops_tenant"))
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "organization_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", nullable = false,
                    insertable = false, updatable = false)
    })
    private Organization organization;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(name = "planned_start")
    private Instant plannedStart;

    @Column(name = "planned_end")
    private Instant plannedEnd;

    @Column(name = "actual_start")
    private Instant actualStart;

    @Column(name = "actual_end")
    private Instant actualEnd;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Workshop() {}

    public Workshop(Tenant tenant, Organization organization, String code, String name,
                    String description, Instant plannedStart, Instant plannedEnd, UUID createdBy) {
        if (plannedStart != null && plannedEnd != null && plannedEnd.isBefore(plannedStart)) {
            throw new BusinessRuleException("Workshop planned end must not precede planned start.");
        }
        this.tenant = tenant;
        this.organization = organization;
        this.code = normalizeCode(code);
        this.name = normalizeRequired(name, "Workshop name");
        this.description = normalizeOptional(description);
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.createdBy = createdBy;
        this.status = Status.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void transitionTo(Status target, Instant now) {
        if (target == null || target == status) return;
        boolean allowed = switch (status) {
            case DRAFT -> target == Status.ACTIVE || target == Status.ARCHIVED;
            case ACTIVE -> target == Status.PAUSED || target == Status.COMPLETED || target == Status.ARCHIVED;
            case PAUSED -> target == Status.ACTIVE || target == Status.COMPLETED || target == Status.ARCHIVED;
            case COMPLETED -> target == Status.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) {
            throw new BusinessRuleException("Invalid workshop transition: " + status + " -> " + target);
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        if (target == Status.ACTIVE && actualStart == null) actualStart = effectiveNow;
        if (target == Status.COMPLETED) actualEnd = effectiveNow;
        status = target;
        updatedAt = effectiveNow;
    }

    public void ensureExecutionOpen() {
        if (status == Status.COMPLETED || status == Status.ARCHIVED) {
            throw new BusinessRuleException("Workshop is closed for execution.");
        }
    }

    private static String normalizeCode(String value) {
        String normalized = normalizeRequired(value, "Workshop code").toUpperCase();
        if (!normalized.matches("[A-Z0-9][A-Z0-9._-]{1,79}")) {
            throw new BusinessRuleException("Workshop code must contain 2-80 uppercase letters, digits, dot, underscore or hyphen.");
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
    public Organization getOrganization() { return organization; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public Instant getPlannedStart() { return plannedStart; }
    public Instant getPlannedEnd() { return plannedEnd; }
    public Instant getActualStart() { return actualStart; }
    public Instant getActualEnd() { return actualEnd; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
