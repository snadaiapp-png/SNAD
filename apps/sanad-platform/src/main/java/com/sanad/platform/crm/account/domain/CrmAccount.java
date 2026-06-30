package com.sanad.platform.crm.account.domain;

import com.sanad.platform.tenant.domain.Tenant;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "crm_accounts")
@EntityListeners(AuditingEntityListener.class)
public class CrmAccount {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Version private long version;
    @Column(name = "display_name", nullable = false, length = 240) private String displayName;
    @Column(name = "normalized_name", nullable = false, length = 240) private String normalizedName;
    @Enumerated(EnumType.STRING) @Column(name = "account_type", nullable = false) private CrmAccountType accountType;
    @Enumerated(EnumType.STRING) @Column(name = "lifecycle_status", nullable = false) private CrmAccountStatus lifecycleStatus;
    @Column(name = "owner_user_id", columnDefinition = "uuid") private UUID ownerUserId;
    @Column(name = "primary_currency_code", length = 3) private String primaryCurrencyCode;
    @Column(name = "preferred_locale", length = 35) private String preferredLocale;
    @Column(name = "time_zone", length = 64) private String timeZone;
    @Column(name = "source", length = 64) private String source;
    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "uuid") private UUID createdBy;
    @Column(name = "updated_by", nullable = false, columnDefinition = "uuid") private UUID updatedBy;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "archived_at") private Instant archivedAt;

    protected CrmAccount() { }

    public CrmAccount(Tenant tenant, String name, CrmAccountType type, UUID ownerId,
                      String currency, String locale, String zone, String source, UUID actorId) {
        this.tenant = tenant;
        rename(name, actorId);
        this.accountType = type;
        this.lifecycleStatus = CrmAccountStatus.ACTIVE;
        this.ownerUserId = ownerId;
        this.primaryCurrencyCode = currency == null ? null : currency.toUpperCase(Locale.ROOT);
        this.preferredLocale = locale;
        this.timeZone = zone;
        this.source = source;
        this.createdBy = actorId;
    }

    public void rename(String name, UUID actorId) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("name is required");
        displayName = value;
        normalizedName = value.toLowerCase(Locale.ROOT);
        updatedBy = actorId;
    }

    public void archive(UUID actorId) {
        lifecycleStatus = CrmAccountStatus.ARCHIVED;
        archivedAt = Instant.now();
        updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public long getVersion() { return version; }
    public String getDisplayName() { return displayName; }
    public CrmAccountType getAccountType() { return accountType; }
    public CrmAccountStatus getLifecycleStatus() { return lifecycleStatus; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getPrimaryCurrencyCode() { return primaryCurrencyCode; }
    public String getPreferredLocale() { return preferredLocale; }
    public String getTimeZone() { return timeZone; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
