package com.sanad.platform.access.capability;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "access_capabilities",
        uniqueConstraints = @UniqueConstraint(name = "uk_access_capabilities_code", columnNames = "code"))
@EntityListeners(AuditingEntityListener.class)
public class AccessCapability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String code;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CapabilityStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccessCapability() {
    }

    public AccessCapability(String code, String name, String description) {
        this.code = normalizeCode(code);
        this.name = normalizeText(name);
        this.description = normalizeNullable(description);
        this.status = CapabilityStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = normalizeCode(code); }
    public String getName() { return name; }
    public void setName(String name) { this.name = normalizeText(name); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = normalizeNullable(description); }
    public CapabilityStatus getStatus() { return status; }
    public void setStatus(CapabilityStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeNullable(String value) {
        String normalized = normalizeText(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AccessCapability capability)) return false;
        if (id != null && capability.id != null) return id.equals(capability.id);
        return Objects.equals(code, capability.code);
    }

    @Override
    public int hashCode() { return Objects.hash(code); }
}
