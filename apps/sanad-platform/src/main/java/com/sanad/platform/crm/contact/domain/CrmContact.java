package com.sanad.platform.crm.contact.domain;

import com.sanad.platform.crm.account.domain.CrmAccount;
import com.sanad.platform.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "crm_contacts")
@EntityListeners(AuditingEntityListener.class)
public class CrmContact {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private CrmAccount account;

    @Version
    private long version;

    @Column(name = "given_name", nullable = false, length = 120)
    private String givenName;

    @Column(name = "family_name", length = 120)
    private String familyName;

    @Column(name = "display_name", nullable = false, length = 240)
    private String displayName;

    @Column(name = "normalized_name", nullable = false, length = 240)
    private String normalizedName;

    @Column(name = "primary_email", length = 255)
    private String primaryEmail;

    @Column(name = "primary_phone", length = 64)
    private String primaryPhone;

    @Column(name = "preferred_locale", length = 35)
    private String preferredLocale;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private String lifecycleStatus;

    @Column(name = "owner_user_id", columnDefinition = "uuid")
    private UUID ownerUserId;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false, columnDefinition = "uuid")
    private UUID updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected CrmContact() { }

    public CrmContact(Tenant tenant, CrmAccount account, String givenName, String familyName,
                      String email, String phone, String locale, String zone,
                      UUID ownerId, UUID actorId) {
        this.tenant = tenant;
        this.account = account;
        this.givenName = required(givenName);
        this.familyName = optional(familyName);
        this.displayName = this.familyName == null ? this.givenName : this.givenName + " " + this.familyName;
        this.normalizedName = this.displayName.toLowerCase(Locale.ROOT);
        this.primaryEmail = optional(email);
        this.primaryPhone = optional(phone);
        this.preferredLocale = optional(locale);
        this.timeZone = optional(zone);
        this.lifecycleStatus = "ACTIVE";
        this.ownerUserId = ownerId;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void archive(UUID actorId) {
        this.lifecycleStatus = "ARCHIVED";
        this.archivedAt = Instant.now();
        this.updatedBy = actorId;
    }

    private static String required(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("givenName is required");
        return normalized;
    }

    private static String optional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public String getGivenName() { return givenName; }
    public String getFamilyName() { return familyName; }
    public String getDisplayName() { return displayName; }
    public String getPrimaryEmail() { return primaryEmail; }
    public String getPrimaryPhone() { return primaryPhone; }
    public String getPreferredLocale() { return preferredLocale; }
    public String getTimeZone() { return timeZone; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public CrmAccount getAccount() { return account; }
}
