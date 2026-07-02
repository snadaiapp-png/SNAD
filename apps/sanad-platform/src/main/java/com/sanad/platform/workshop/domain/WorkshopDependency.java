package com.sanad.platform.workshop.domain;

import com.sanad.platform.tenant.domain.Tenant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workshop_dependencies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workshop_dependency",
                columnNames = {"tenant_id", "predecessor_item_id", "successor_item_id"})
})
public class WorkshopDependency {

    public enum Type { FINISH_TO_START, START_TO_START, FINISH_TO_FINISH }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_workshop_dependency_tenant"))
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
            @JoinColumn(name = "predecessor_item_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", nullable = false,
                    insertable = false, updatable = false)
    })
    private WorkshopWorkItem predecessor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "successor_item_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", nullable = false,
                    insertable = false, updatable = false)
    })
    private WorkshopWorkItem successor;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false, length = 30)
    private Type type;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkshopDependency() {}

    public WorkshopDependency(Tenant tenant, Workshop workshop, WorkshopWorkItem predecessor,
                              WorkshopWorkItem successor, Type type, UUID createdBy) {
        this.tenant = tenant;
        this.workshop = workshop;
        this.predecessor = predecessor;
        this.successor = successor;
        this.type = type == null ? Type.FINISH_TO_START : type;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public Workshop getWorkshop() { return workshop; }
    public WorkshopWorkItem getPredecessor() { return predecessor; }
    public WorkshopWorkItem getSuccessor() { return successor; }
    public Type getType() { return type; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
