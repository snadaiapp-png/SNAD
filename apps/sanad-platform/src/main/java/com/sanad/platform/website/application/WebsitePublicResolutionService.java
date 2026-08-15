package com.sanad.platform.website.application;

import com.sanad.platform.website.api.WebsiteDtos.*;
import com.sanad.platform.website.domain.WebsiteDomain;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public Website Resolution Service (v20260816.3).
 *
 * Resolves published websites by hostname — this is the PUBLIC-facing
 * resolution chain used by the public website renderer. It only returns
 * ACTIVE websites with ACTIVE domains and PUBLISHED pages.
 *
 * Tenant identity comes from hostname mapping — NEVER from a public tenantId.
 */
@Service
public class WebsitePublicResolutionService {

    private final JdbcTemplate jdbc;
    private final WebsiteDomainService domainService;

    public WebsitePublicResolutionService(JdbcTemplate jdbc, WebsiteDomainService domainService) {
        this.jdbc = jdbc;
        this.domainService = domainService;
    }

    /**
     * Resolve a website by hostname. Returns the public website data
     * (no management fields, no unpublished pages).
     */
    @Transactional(readOnly = true)
    public PublicWebsiteResponse resolveWebsite(String hostname) {
        var domain = domainService.findByHostname(hostname);
        if (domain == null) return null;
        // Fetch website — must be ACTIVE
        try {
            Map<String, Object> website = jdbc.queryForMap(
                    "SELECT * FROM websites WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
                    domain.websiteId(), domain.tenantId());
            return new PublicWebsiteResponse(
                    domain.websiteId(),
                    (String) website.get("name"),
                    (String) website.get("slug"),
                    (String) website.get("default_locale"),
                    null, // theme config parsed separately if needed
                    listPublishedPages(domain.tenantId(), domain.websiteId()),
                    List.of() // navigation fetched separately
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null; // website not active or not found
        }
    }

    /**
     * Resolve a published page by hostname + slug.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public PublicPageResponse resolvePage(String hostname, String pageSlug) {
        var domain = domainService.findByHostname(hostname);
        if (domain == null) return null;
        try {
            Map<String, Object> page = jdbc.queryForMap(
                    "SELECT * FROM website_pages WHERE tenant_id = ? AND website_id = ? AND slug = ? AND status = 'PUBLISHED'",
                    domain.tenantId(), domain.websiteId(), pageSlug);
            Map<String, Object> theme = null;
            try {
                var themeRow = jdbc.queryForMap(
                        "SELECT * FROM website_theme_settings WHERE tenant_id = ? AND website_id = ?",
                        domain.tenantId(), domain.websiteId());
                // return minimal theme info
            } catch (Exception ignored) {}
            return new PublicPageResponse(
                    (UUID) page.get("id"), domain.websiteId(),
                    (String) page.get("title"), (String) page.get("slug"),
                    WebsiteDomain.PageType.valueOf((String) page.get("page_type")),
                    null, // content layout
                    (String) page.get("seo_title"), (String) page.get("seo_description"),
                    (String) page.get("canonical_url"),
                    (String) page.get("og_title"), (String) page.get("og_description"),
                    (Boolean) page.get("robots_index"),
                    null, // theme config
                    page.get("published_at") instanceof java.sql.Timestamp ts ? ts.toInstant() : null);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null; // page not published or not found
        }
    }

    private List<PublicPageSummary> listPublishedPages(UUID tenantId, UUID websiteId) {
        return jdbc.query(
                "SELECT id, title, slug, page_type FROM website_pages WHERE tenant_id = ? AND website_id = ? AND status = 'PUBLISHED' ORDER BY slug",
                (rs, rowNum) -> new PublicPageSummary(
                        rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getString("slug"), WebsiteDomain.PageType.valueOf(rs.getString("page_type"))),
                tenantId, websiteId);
    }
}
