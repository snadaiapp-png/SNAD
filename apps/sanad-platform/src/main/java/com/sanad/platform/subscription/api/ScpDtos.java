package com.sanad.platform.subscription.api;

import com.sanad.platform.subscription.catalog.ApplicationEntity;
import com.sanad.platform.subscription.item.SubscriptionItemEntity;
import com.sanad.platform.subscription.plan.PlanVersionEntity;
import com.sanad.platform.subscription.pricing.PriceEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO contracts for the Subscription Control Plane API (additive, under
 * {@code /api/v1/executive}). Record style, consistent with
 * {@code SaasAdminDtos} and {@code module.dto}.
 */
public final class ScpDtos {

    private ScpDtos() {
    }

    // ============================================================
    // Applications catalog
    // ============================================================

    public record ApplicationRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9_]+$") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String localizedName,
            @Size(max = 1000) String description,
            @Size(max = 50) String category,
            @Size(max = 50) String iconKey,
            @Size(max = 20) String provisioningMode,
            List<String> supportedCountries,
            List<String> dependencies,
            Integer displayOrder) {
    }

    public record ApplicationResponse(
            UUID id,
            String code,
            String name,
            String localizedName,
            String description,
            String category,
            String status,
            String version,
            int displayOrder,
            String iconKey,
            String provisioningMode,
            List<String> supportedCountries,
            List<String> dependencies,
            Instant createdAt,
            Instant updatedAt) {

        public static ApplicationResponse from(ApplicationEntity e) {
            return new ApplicationResponse(e.getId(), e.getCode(), e.getName(),
                    e.getLocalizedName(), e.getDescription(), e.getCategory(), e.getStatus(),
                    e.getVersion(), e.getDisplayOrder(), e.getIconKey(),
                    e.getProvisioningMode(), e.getSupportedCountries(), e.getDependencies(),
                    e.getCreatedAt(), e.getUpdatedAt());
        }
    }

    // ============================================================
    // Plan versions
    // ============================================================

    public record CreatePlanVersionRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
            Long monthlyPriceMinor,
            Long annualPriceMinor,
            Integer trialDays,
            Integer maxUsers,
            Integer maxOrganizations,
            Long storageMb) {
    }

    public record PlanVersionResponse(
            UUID id,
            UUID planId,
            int versionNumber,
            String status,
            Instant effectiveFrom,
            Instant effectiveTo,
            String currencyCode,
            long monthlyPriceMinor,
            long annualPriceMinor,
            int trialDays,
            int maxUsers,
            int maxOrganizations,
            long storageMb,
            Instant createdAt,
            Instant updatedAt) {

        public static PlanVersionResponse from(PlanVersionEntity v) {
            return new PlanVersionResponse(v.getId(), v.getPlanId(), v.getVersionNumber(),
                    v.getStatus(), v.getEffectiveFrom(), v.getEffectiveTo(), v.getCurrencyCode(),
                    v.getMonthlyPriceMinor(), v.getAnnualPriceMinor(), v.getTrialDays(),
                    v.getMaxUsers(), v.getMaxOrganizations(), v.getStorageMb(),
                    v.getCreatedAt(), v.getUpdatedAt());
        }
    }

    // ============================================================
    // Prices
    // ============================================================

    public record PriceRequest(
            @NotBlank @Pattern(regexp = "^(FLAT|PER_USER|PER_EMPLOYEE|PER_BRANCH|PER_TRANSACTION|PER_API_REQUEST|PER_AI_TOKEN|TIERED|VOLUME|USAGE_BASED|HYBRID|CUSTOM_CONTRACT)$")
            String priceModel,
            String countryCode,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
            String billingInterval,
            Long baseAmountMinor,
            Long unitAmountMinor,
            String tiersJson,
            Long minAmountMinor,
            Long maxAmountMinor) {
    }

    public record PriceResponse(
            UUID id,
            UUID planVersionId,
            UUID productId,
            String priceModel,
            String countryCode,
            String currencyCode,
            String billingInterval,
            long baseAmountMinor,
            Long unitAmountMinor,
            String tiersJson,
            Long minAmountMinor,
            Long maxAmountMinor,
            Instant effectiveFrom,
            Instant effectiveTo) {

        public static PriceResponse from(PriceEntity p) {
            return new PriceResponse(p.getId(), p.getPlanVersionId(), p.getProductId(),
                    p.getPriceModel(), p.getCountryCode(), p.getCurrencyCode(),
                    p.getBillingInterval(), p.getBaseAmountMinor(), p.getUnitAmountMinor(),
                    p.getTiersJson(), p.getMinAmountMinor(), p.getMaxAmountMinor(),
                    p.getEffectiveFrom(), p.getEffectiveTo());
        }
    }

    public record CountryCurrencyResponse(String countryCode, String currencyCode, boolean isDefault) {
    }

    // ============================================================
    // Subscription items
    // ============================================================

    public record AddSubscriptionItemRequest(
            @NotBlank @Pattern(regexp = "^(PLAN|ADD_ON|METERED|OTHER)$") String itemType,
            UUID applicationId,
            UUID productId,
            UUID planId,
            UUID planVersionId,
            Integer quantity,
            Long unitAmountMinor,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currencyCode) {
    }

    public record UpdateSubscriptionItemRequest(
            String action,
            Integer quantity) {
    }

    public record SubscriptionItemResponse(
            UUID id,
            UUID tenantId,
            UUID subscriptionId,
            String itemType,
            UUID applicationId,
            UUID productId,
            UUID planId,
            UUID planVersionId,
            String nameSnapshot,
            int quantity,
            Long unitAmountMinor,
            String currencyCode,
            String status,
            Instant createdAt,
            Instant updatedAt) {

        public static SubscriptionItemResponse from(SubscriptionItemEntity e) {
            return new SubscriptionItemResponse(e.getId(), e.getTenantId(), e.getSubscriptionId(),
                    e.getItemType(), e.getApplicationId(), e.getProductId(), e.getPlanId(),
                    e.getPlanVersionId(), e.getNameSnapshot(), e.getQuantity(),
                    e.getUnitAmountMinor(), e.getCurrencyCode(), e.getStatus(),
                    e.getCreatedAt(), e.getUpdatedAt());
        }
    }
}
