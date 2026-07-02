package com.sanad.platform.workshop.domain;

import com.sanad.platform.tenant.domain.Tenant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workshop_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workshop_assignment",
                columnNames = {"tenant_id", "work_item_id", "user_id", "assignment_role"})
})
public class WorkshopAssignment {

    public enum Role { OWNER, FACILITATOR, EXECUTOR, REVIEWER, OBSERVER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_workshop_assignment_tenant"))
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

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 30)
    private Role role;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkshopAssignment() {}

    public WorkshopAssignment(Tenant tenant, Workshop workshop, WorkshopWorkItem workItem,
                              UUID userId, Role role, UUID createdBy) {
        this.tenant = tenant;
        this.workshop = workshop;
        this.workItem = workItem;
        this.userId = userId;
        this.role = role;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public Workshop getWorkshop() { return workshop; }
    public WorkshopWorkItem getWorkItem() { return workItem; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
