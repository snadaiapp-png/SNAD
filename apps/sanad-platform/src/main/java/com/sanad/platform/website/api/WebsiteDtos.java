package com.sanad.platform.website.api;

import com.sanad.platform.website.domain.WebsiteDomain.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WebsiteDtos {

    private WebsiteDtos() {}

    // ===== Website DTOs =====
    public record CreateWebsiteRequest(String name, String slug, String defaultLocale) {}
    public record UpdateWebsiteRequest(String name, String defaultLocale, Map<String, Object> themeConfig) {}
    public record WebsiteResponse(UUID id, UUID tenantId, String name, String slug,
            WebsiteStatus status, String defaultLocale, boolean isPrimary,
            Map<String, Object> themeConfig, long version, Instant createdAt, Instant updatedAt) {}

    // ===== Page DTOs =====
    public record CreatePageRequest(String title, String slug, PageType pageType,
            Map<String, Object> contentLayout, String seoTitle, String seoDescription,
            String canonicalUrl, String ogTitle, String ogDescription, boolean robotsIndex) {}
    public record UpdatePageRequest(String title, Map<String, Object> contentLayout,
            String seoTitle, String seoDescription, String canonicalUrl,
            String ogTitle, String ogDescription, Boolean robotsIndex, Long expectedVersion) {}
    public record PageResponse(UUID id, UUID tenantId, UUID websiteId, String title, String slug,
            PageType pageType, Map<String, Object> contentLayout, String seoTitle,
            String seoDescription, String canonicalUrl, String ogTitle, String ogDescription,
            boolean robotsIndex, PageStatus status, Instant publishedAt, UUID publishedBy,
            long version, Instant createdAt, Instant updatedAt) {}

    // ===== Domain DTOs =====
    public record CreateDomainRequest(String hostname, VerificationMethod verificationMethod) {}
    public record VerifyDomainRequest(String verificationToken) {}
    public record DomainResponse(UUID id, UUID tenantId, UUID websiteId, String hostname,
            DomainType domainType, VerificationStatus verificationStatus,
            ActivationStatus activationStatus, boolean isPrimary,
            String verificationToken, VerificationMethod verificationMethod,
            Instant verifiedAt, String failureReason, long version,
            Instant createdAt, Instant updatedAt) {}
    public record DomainVerificationInstructions(String hostname, String method,
            String txtRecordName, String txtRecordValue, String cnameRecord,
            String httpPath, String httpExpectedContent) {}

    // ===== Navigation DTOs =====
    public record CreateNavigationRequest(String name, NavType navType) {}
    public record NavigationResponse(UUID id, UUID tenantId, UUID websiteId, String name,
            NavType navType, List<NavItemResponse> items, long version,
            Instant createdAt, Instant updatedAt) {}
    public record NavItemResponse(UUID id, UUID navigationId, UUID parentId, String label,
            NavTargetType targetType, UUID targetPageId, String targetUrl, int sortOrder) {}
    public record CreateNavItemRequest(String label, NavTargetType targetType,
            UUID targetPageId, String targetUrl, int sortOrder, UUID parentId) {}

    // ===== Theme DTOs =====
    public record UpdateThemeRequest(String primaryColor, String secondaryColor,
            String fontFamily, String layout, String customCss) {}
    public record ThemeResponse(UUID id, UUID tenantId, UUID websiteId, String primaryColor,
            String secondaryColor, String fontFamily, String layout, String customCss,
            long version, Instant createdAt, Instant updatedAt) {}

    // ===== Public Resolution DTOs =====
    public record PublicWebsiteResponse(UUID websiteId, String name, String slug,
            String defaultLocale, Map<String, Object> themeConfig,
            List<PublicPageSummary> pages, List<PublicNavigationSummary> navigation) {}
    public record PublicPageSummary(UUID id, String title, String slug, PageType pageType) {}
    public record PublicNavigationSummary(String name, NavType navType,
            List<PublicNavItemSummary> items) {}
    public record PublicNavItemSummary(String label, String targetType, String targetUrl, UUID targetPageId, int sortOrder) {}
    public record PublicPageResponse(UUID id, UUID websiteId, String title, String slug,
            PageType pageType, Map<String, Object> contentLayout,
            String seoTitle, String seoDescription, String canonicalUrl,
            String ogTitle, String ogDescription, boolean robotsIndex,
            Map<String, Object> themeConfig, Instant publishedAt) {}

    // ===== Summary =====
    public record WebsiteSummary(int totalWebsites, int activeWebsites, int draftWebsites,
            int suspendedWebsites, int archivedWebsites, int totalPages,
            int publishedPages, int activeDomains, int verifiedDomains) {}
}
