package com.sanad.platform.website.application;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.website.api.WebsiteDtos.*;
import com.sanad.platform.website.domain.WebsiteDomain;
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
 * Website application service (v20260816.3).
 * Tenant-scoped CRUD + lifecycle (activate/suspend/archive/setPrimary).
 */
@Service
public class WebsiteService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public WebsiteService(JdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public WebsiteResponse create(UUID tenantId, CreateWebsiteRequest request, Authentication auth) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        String slug = normalizeSlug(request.slug() != null ? request.slug() : request.name());
        String locale = request.defaultLocale() != null && !request.defaultLocale().isBlank()
                ? request.defaultLocale() : "ar";
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO websites (id, tenant_id, name, slug, status, default_locale, is_primary, "
                            + "theme_config, version, created_by, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'DRAFT', ?, FALSE, NULL, 0, ?, ?, ?)",
                    id, tenantId, request.name().trim(), slug, locale, actorUserId(auth),
                    Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug already exists for this tenant: " + slug);
        }
        audit(tenantId, auth, "WEBSITE.CREATED", id, "slug=" + slug);
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public WebsiteResponse update(UUID tenantId, UUID websiteId, UpdateWebsiteRequest request, Authentication auth) {
        WebsiteResponse existing = getOrThrow(tenantId, websiteId);
        Instant now = Instant.now();
        if (request.name() != null && !request.name().isBlank()) {
            jdbc.update("UPDATE websites SET name = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    request.name().trim(), Timestamp.from(now), tenantId, websiteId);
        }
        if (request.defaultLocale() != null) {
            jdbc.update("UPDATE websites SET default_locale = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    request.defaultLocale(), Timestamp.from(now), tenantId, websiteId);
        }
        if (request.themeConfig() != null) {
            jdbc.update("UPDATE websites SET theme_config = ?::jsonb, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                    toJson(request.themeConfig()), Timestamp.from(now), tenantId, websiteId);
        }
        audit(tenantId, auth, "WEBSITE.UPDATED", websiteId, "name=" + existing.name());
        return getOrThrow(tenantId, websiteId);
    }

    @Transactional(readOnly = true)
    public List<WebsiteResponse> list(UUID tenantId) {
        return jdbc.query("SELECT * FROM websites WHERE tenant_id = ? ORDER BY created_at", this::mapRow, tenantId);
    }

    @Transactional(readOnly = true)
    public WebsiteResponse get(UUID tenantId, UUID websiteId) { return getOrThrow(tenantId, websiteId); }

    @Transactional
    public WebsiteResponse activate(UUID tenantId, UUID websiteId, Authentication auth) {
        return transition(tenantId, websiteId, "ACTIVE", "WEBSITE.ACTIVATED", auth);
    }

    @Transactional
    public WebsiteResponse suspend(UUID tenantId, UUID websiteId, Authentication auth) {
        return transition(tenantId, websiteId, "SUSPENDED", "WEBSITE.SUSPENDED", auth);
    }

    @Transactional
    public WebsiteResponse archive(UUID tenantId, UUID websiteId, Authentication auth) {
        return transition(tenantId, websiteId, "ARCHIVED", "WEBSITE.ARCHIVED", auth);
    }

    @Transactional
    public WebsiteResponse setPrimary(UUID tenantId, UUID websiteId, Authentication auth) {
        WebsiteResponse website = getOrThrow(tenantId, websiteId);
        jdbc.update("UPDATE websites SET is_primary = FALSE, updated_at = ? WHERE tenant_id = ? AND is_primary = TRUE",
                Timestamp.from(Instant.now()), tenantId);
        jdbc.update("UPDATE websites SET is_primary = TRUE, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                Timestamp.from(Instant.now()), tenantId, websiteId);
        audit(tenantId, auth, "WEBSITE.SET_PRIMARY", websiteId, "name=" + website.name());
        return getOrThrow(tenantId, websiteId);
    }

    @Transactional(readOnly = true)
    public WebsiteSummary summarize(UUID tenantId) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM websites WHERE tenant_id = ?", Integer.class, tenantId);
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM websites WHERE tenant_id = ? AND status = 'ACTIVE'", Integer.class, tenantId);
        Integer draft = jdbc.queryForObject("SELECT COUNT(*) FROM websites WHERE tenant_id = ? AND status = 'DRAFT'", Integer.class, tenantId);
        Integer suspended = jdbc.queryForObject("SELECT COUNT(*) FROM websites WHERE tenant_id = ? AND status = 'SUSPENDED'", Integer.class, tenantId);
        Integer archived = jdbc.queryForObject("SELECT COUNT(*) FROM websites WHERE tenant_id = ? AND status = 'ARCHIVED'", Integer.class, tenantId);
        Integer pages = jdbc.queryForObject("SELECT COUNT(*) FROM website_pages WHERE tenant_id = ?", Integer.class, tenantId);
        Integer published = jdbc.queryForObject("SELECT COUNT(*) FROM website_pages WHERE tenant_id = ? AND status = 'PUBLISHED'", Integer.class, tenantId);
        Integer activeDomains = jdbc.queryForObject("SELECT COUNT(*) FROM website_domains WHERE tenant_id = ? AND activation_status = 'ACTIVE'", Integer.class, tenantId);
        Integer verifiedDomains = jdbc.queryForObject("SELECT COUNT(*) FROM website_domains WHERE tenant_id = ? AND verification_status = 'VERIFIED'", Integer.class, tenantId);
        return new WebsiteSummary(
                total != null ? total : 0, active != null ? active : 0, draft != null ? draft : 0,
                suspended != null ? suspended : 0, archived != null ? archived : 0,
                pages != null ? pages : 0, published != null ? published : 0,
                activeDomains != null ? activeDomains : 0, verifiedDomains != null ? verifiedDomains : 0);
    }

    // ===== Helpers =====
    private WebsiteResponse getOrThrow(UUID tenantId, UUID websiteId) {
        try {
            return jdbc.queryForObject("SELECT * FROM websites WHERE tenant_id = ? AND id = ?", this::mapRow, tenantId, websiteId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "website not found: " + websiteId);
        }
    }

    private WebsiteResponse transition(UUID tenantId, UUID websiteId, String newStatus, String auditAction, Authentication auth) {
        WebsiteResponse existing = getOrThrow(tenantId, websiteId);
        Instant now = Instant.now();
        jdbc.update("UPDATE websites SET status = ?, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                newStatus, Timestamp.from(now), tenantId, websiteId);
        audit(tenantId, auth, auditAction, websiteId, "name=" + existing.name() + ",to=" + newStatus);
        return getOrThrow(tenantId, websiteId);
    }

    private String normalizeSlug(String raw) {
        if (raw == null) return "website";
        String s = raw.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (s.startsWith("-")) s = s.substring(1);
        if (s.endsWith("-")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) s = "website";
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "WEBSITE", resourceId == null ? null : resourceId.toString(), reason, null, null); }
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

    private WebsiteResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WebsiteResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("name"), rs.getString("slug"),
                WebsiteDomain.WebsiteStatus.valueOf(rs.getString("status")),
                rs.getString("default_locale"), rs.getBoolean("is_primary"),
                fromJson(rs.getString("theme_config")),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
