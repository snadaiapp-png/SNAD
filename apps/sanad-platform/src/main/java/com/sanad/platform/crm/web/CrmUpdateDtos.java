package com.sanad.platform.crm.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CRM-G2 — Update request DTOs for PATCH operations.
 * These DTOs represent only the editable fields for partial updates.
 * Null fields are ignored (COALESCE in SQL).
 * Branch: crm/003-stable-api-contracts
 */
public final class CrmUpdateDtos {
    private CrmUpdateDtos() {}

    public record UpdateOpportunityRequest(
            @Size(max = 240) String name,
            @DecimalMin("0.0") BigDecimal amount,
            java.util.UUID ownerUserId,
            LocalDate expectedCloseDate) {}

    public record UpdateActivityRequest(
            @Size(max = 240) String subject,
            @Size(max = 4000) String body,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100) Integer priority,
            java.time.OffsetDateTime startAt,
            java.time.OffsetDateTime dueAt) {}

    public record UpdatePipelineRequest(
            @Size(max = 160) String name,
            @Pattern(regexp = "[A-Za-z]{3}") String currencyCode) {}

    public record UpdateCustomFieldRequest(
            @Size(max = 240) String labelAr,
            @Size(max = 240) String labelEn,
            Boolean sensitive,
            Boolean searchable,
            Boolean required) {}

    public record UpdateLeadRequest(
            @Size(max = 240) String displayName,
            @Size(max = 240) String companyName,
            @Email @Size(max = 255) String email,
            @Size(max = 64) String phone,
            java.util.UUID ownerUserId) {}
}
