package com.sanad.platform.crm.account.domain;

import com.sanad.platform.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.ZoneId;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "crm_accounts")
@EntityListeners(AuditingEntityListener.class)
public class CrmAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "display_name", nullable = false, length = 240)
    private String displayName;

    @Column(name = "normalized_name", nullable = false, length = 240)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 40)
    private CrmAccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private CrmAccountStatus lifecycleStatus;

    @Column(name = "owner_user_id", columnDefinition = "uuid")
    private UUID ownerUserId;

    @Column(name = "primary_currency_code", length = 3)
    private String primaryCurrencyCode;

    @Column(name = "preferred_locale", length = 35)
    private String preferredLocale;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "source", length = 64)
    private String source;

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

    protected CrmAccount() { }

    public CrmAccount(Tenant tenant, String name, CrmAccountType type, UUID ownerId,
                      String currency, String locale, String zone, String source, UUID actorId) {
        this.tenant = Objects.requireNonNull(tenant, "tenant is required");
        this.accountType = Objects.requireNonNull(type, "accountType is required");
        this.createdBy = Objects.requireNonNull(actorId, "actorId is required");
        this.lifecycleStatus = CrmAccountStatus.ACTIVE;
        rename(name, actorId);
        this.ownerUserId = ownerId;
        this.primaryCurrencyCode = normalizeCurrency(currency);
        this.preferredLocale = normalizeLocale(locale);
        this.timeZone = normalizeZone(zone);
        this.source = optional(source, 64, "source");
    }

    public void rename(String name, UUID actorId) {
        String value = required(name, 240, "displayName");
        this.displayName = value;
        this.normalizedName = value.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        this.updatedBy = Objects.requireNonNull(actorId, "actorId is required");
    }

    public void archive(UUID actorId) {
        if (lifecycleStatus != CrmAccountStatus.ARCHIVED) {
            this.lifecycleStatus = CrmAccountStatus.ARCHIVED;
            this.archivedAt = Instant.now();
        }
        this.updatedBy = Objects.requireNonNull(actorId, "actorId is required");
    }

    private static String normalizeCurrency(String value) {
        String normalized = optional(value, 3, "primaryCurrencyCode");
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("primaryCurrencyCode must be ISO 4217 alpha-3");
        }
        return normalized;
    }

    private static String normalizeLocale(String value) {
        String normalized = optional(value, 35, "preferredLocale");
        if (normalized == null) return null;
        try {
            Locale locale = new Locale.Builder().setLanguageTag(normalized).build();
            if (locale.getLanguage().isBlank()) throw new IllegalArgumentException("preferredLocale is invalid");
            return locale.toLanguageTag();
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("preferredLocale is invalid", exception);
        }
    }

    private static String normalizeZone(String value) {
        String normalized = optional(value, 64, "timeZone");
        if (normalized == null) return null;
        try {
            return ZoneId.of(normalized).getId();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("timeZone is invalid", exception);
        }
    }

    private static String required(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return normalized;
    }

    private static String optional(String value, int max, String field) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return normalized;
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
