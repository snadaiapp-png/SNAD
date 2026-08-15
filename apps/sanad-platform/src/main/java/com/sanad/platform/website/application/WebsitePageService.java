package com.sanad.platform.website.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.website.api.WebsiteDtos.*;
import com.sanad.platform.website.domain.WebsiteDomain;
import com.sanad.platform.website.domain.WebsiteDomain.PageType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Website Page application service (v20260816.3).
 * Tenant-scoped CRUD + publish/unpublish/archive with version-based optimistic locking.
 */
@Service
public class WebsitePageService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public WebsitePageService(JdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public PageResponse create(UUID tenantId, UUID websiteId, CreatePageRequest request, Authentication auth) {
        // Verify website belongs to tenant
        ensureWebsite(tenantId, websiteId);
        if (request == null || request.title() == null || request.title().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        String slug = normalizeSlug(request.slug() != null ? request.slug() : request.title());
        PageType type = request.pageType() != null ? request.pageType() : PageType.STANDARD;
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO website_pages (id, tenant_id, website_id, title, slug, page_type, "
                            + "content_layout, seo_title, seo_description, canonical_url, og_title, og_description, "
                            + "robots_index, status, version, created_by, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, 'DRAFT', 0, ?, ?, ?)",
                    id, tenantId, websiteId, request.title().trim(), slug, type.name(),
                    request.contentLayout() != null ? toJson(request.contentLayout()) : null,
                    request.seoTitle(), request.seoDescription(), request.canonicalUrl(),
                    request.ogTitle(), request.ogDescription(),
                    request.robotsIndex(),
                    actorUserId(auth), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "page slug already exists: " + slug);
        }
        audit(tenantId, auth, "PAGE.CREATED", id, "slug=" + slug);
        return getOrThrow(tenantId, websiteId, id);
    }

    @Transactional
    public PageResponse update(UUID tenantId, UUID websiteId, UUID pageId, UpdatePageRequest request, Authentication auth) {
        PageResponse existing = getOrThrow(tenantId, websiteId, pageId);
        // Optimistic concurrency check
        if (request.expectedVersion() != null && request.expectedVersion() != existing.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "version conflict: expected " + request.expectedVersion() + " but was " + existing.version());
        }
        Instant now = Instant.now();
        if (request.title() != null && !request.title().isBlank()) {
            jdbc.update("UPDATE website_pages SET title = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    request.title().trim(), Timestamp.from(now), tenantId, pageId);
        }
        if (request.contentLayout() != null) {
            jdbc.update("UPDATE website_pages SET content_layout = ?::jsonb, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    toJson(request.contentLayout()), Timestamp.from(now), tenantId, pageId);
        }
        if (request.seoTitle() != null) {
            jdbc.update("UPDATE website_pages SET seo_title = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    request.seoTitle(), Timestamp.from(now), tenantId, pageId);
        }
        if (request.seoDescription() != null) {
            jdbc.update("UPDATE website_pages SET seo_description = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    request.seoDescription(), Timestamp.from(now), tenantId, pageId);
        }
        if (request.canonicalUrl() != null) {
            jdbc.update("UPDATE website_pages SET canonical_url = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    request.canonicalUrl(), Timestamp.from(now), tenantId, pageId);
        }
        if (request.ogTitle() != null) {
            jdbc.update("UPDATE website_pages SET og_title = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    request.ogTitle(), Timestamp.from(now), tenantId, pageId);
        }
        if (request.ogDescription() != null) {
            jdbc.update("UPDATE website_pages SET og_description = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    request.ogDescription(), Timestamp.from(now), tenantId, pageId);
        }
        if (request.robotsIndex() != null) {
            jdbc.update("UPDATE website_pages SET robots_index = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    request.robotsIndex(), Timestamp.from(now), tenantId, pageId);
        }
        audit(tenantId, auth, "PAGE.UPDATED", pageId, "title=" + existing.title());
        return getOrThrow(tenantId, websiteId, pageId);
    }

    @Transactional(readOnly = true)
    public List<PageResponse> list(UUID tenantId, UUID websiteId) {
        ensureWebsite(tenantId, websiteId);
        return jdbc.query("SELECT * FROM website_pages WHERE tenant_id = ? AND website_id = ? ORDER BY created_at",
                this::mapRow, tenantId, websiteId);
    }

    @Transactional(readOnly = true)
    public PageResponse get(UUID tenantId, UUID websiteId, UUID pageId) { return getOrThrow(tenantId, websiteId, pageId); }

    @Transactional
    public PageResponse publish(UUID tenantId, UUID websiteId, UUID pageId, Authentication auth) {
        PageResponse existing = getOrThrow(tenantId, websiteId, pageId);
        Instant now = Instant.now();
        UUID actor = actorUserId(auth);
        jdbc.update("UPDATE website_pages SET status = 'PUBLISHED', published_at = ?, published_by = ?, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), actor, Timestamp.from(now), tenantId, pageId);
        // Record publication
        jdbc.update("INSERT INTO website_publications (id, tenant_id, website_id, page_id, publication_type, "
                        + "published_version, status, published_at, published_by, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'PAGE', ?, 'PUBLISHED', ?, ?, 0, ?, ?)",
                UUID.randomUUID(), tenantId, websiteId, pageId, existing.version() + 1,
                Timestamp.from(now), actor, Timestamp.from(now), Timestamp.from(now));
        audit(tenantId, auth, "PAGE.PUBLISHED", pageId, "title=" + existing.title());
        return getOrThrow(tenantId, websiteId, pageId);
    }

    @Transactional
    public PageResponse unpublish(UUID tenantId, UUID websiteId, UUID pageId, Authentication auth) {
        PageResponse existing = getOrThrow(tenantId, websiteId, pageId);
        Instant now = Instant.now();
        UUID actor = actorUserId(auth);
        jdbc.update("UPDATE website_pages SET status = 'UNPUBLISHED', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), tenantId, pageId);
        jdbc.update("UPDATE website_publications SET status = 'UNPUBLISHED', unpublished_at = ?, unpublished_by = ?, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND page_id = ? AND status = 'PUBLISHED'",
                Timestamp.from(now), actor, Timestamp.from(now), tenantId, pageId);
        audit(tenantId, auth, "PAGE.UNPUBLISHED", pageId, "title=" + existing.title());
        return getOrThrow(tenantId, websiteId, pageId);
    }

    @Transactional
    public PageResponse archive(UUID tenantId, UUID websiteId, UUID pageId, Authentication auth) {
        PageResponse existing = getOrThrow(tenantId, websiteId, pageId);
        Instant now = Instant.now();
        jdbc.update("UPDATE website_pages SET status = 'ARCHIVED', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, pageId);
        audit(tenantId, auth, "PAGE.ARCHIVED", pageId, "title=" + existing.title());
        return getOrThrow(tenantId, websiteId, pageId);
    }

    // ===== Helpers =====
    private void ensureWebsite(UUID tenantId, UUID websiteId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM websites WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, websiteId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "website not found");
    }

    private PageResponse getOrThrow(UUID tenantId, UUID websiteId, UUID pageId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM website_pages WHERE tenant_id = ? AND website_id = ? AND id = ?",
                    this::mapRow, tenantId, websiteId, pageId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "page not found: " + pageId);
        }
    }

    private String normalizeSlug(String raw) {
        if (raw == null) return "page";
        String s = raw.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (s.startsWith("-")) s = s.substring(1);
        if (s.endsWith("-")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) s = "page";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "WEBSITE_PAGE", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private String toJson(Map<String, Object> map) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class); }
        catch (Exception e) { return null; }
    }

    private PageResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PageResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("website_id", UUID.class), rs.getString("title"), rs.getString("slug"),
                WebsiteDomain.PageType.valueOf(rs.getString("page_type")),
                fromJson(rs.getString("content_layout")),
                rs.getString("seo_title"), rs.getString("seo_description"),
                rs.getString("canonical_url"), rs.getString("og_title"), rs.getString("og_description"),
                rs.getBoolean("robots_index"),
                WebsiteDomain.PageStatus.valueOf(rs.getString("status")),
                rs.getObject("published_at", Timestamp.class) == null ? null : rs.getObject("published_at", Timestamp.class).toInstant(),
                rs.getObject("published_by", UUID.class),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
