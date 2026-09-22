package com.sanad.platform.commerce.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.domain.CommerceDomain;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
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
 * Store application service (v20260816.5).
 *
 * <p>Tenant-scoped CRUD + lifecycle (activate / suspend / archive / setPrimary)
 * for {@code commerce_stores}. Mirrors the {@code WebsiteService} patterns:
 * JdbcTemplate-based, audited via {@link PlatformAuditService},
 * {@code @Transactional} on mutations, optimistic version via {@code version+1}.
 */
@Service
public class StoreService {

    private static final String MODULE_CODE = "ECOMMERCE_CX";
    private static final String STORE_LIMIT_CODE = "ECOMMERCE_CX.MAX_STORES";

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;
    private final EntitlementResolver entitlementResolver;
    private final StoreDomainService domainService;

    public StoreService(
            JdbcTemplate jdbc,
            PlatformAuditService auditService,
            ObjectMapper objectMapper,
            EntitlementResolver entitlementResolver,
            StoreDomainService domainService
    ) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.entitlementResolver = entitlementResolver;
        this.domainService = domainService;
    }

    @Transactional
    public StoreResponse create(UUID tenantId, CreateStoreRequest request, Authentication auth) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        enforceCreationLimit(tenantId);
        String slug = normalizeSlug(request.slug() != null ? request.slug() : request.name());
        String code = (request.code() != null && !request.code().isBlank())
                ? normalizeCode(request.code()) : slug.toUpperCase();
        String locale = request.defaultLocale() != null && !request.defaultLocale().isBlank()
                ? request.defaultLocale().trim()
                : tenantLocale(tenantId);
        String currency = request.defaultCurrency() != null && !request.defaultCurrency().isBlank()
                ? request.defaultCurrency().trim().toUpperCase()
                : tenantCurrency(tenantId);
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "defaultCurrency must be an ISO-4217 currency code");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO commerce_stores (id, tenant_id, name, code, slug, status, "
                            + "default_locale, default_currency, is_primary, settings, version, created_by, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, FALSE, ?::jsonb, 0, ?, ?, ?)",
                    id, tenantId, request.name().trim(), code, slug, locale, currency,
                    toJson(request.settings()),
                    actorUserId(auth), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug already exists for this tenant: " + slug);
        }
        domainService.generateAndRegisterDefaultDomain(tenantId, id, slug, auth);
        audit(tenantId, auth, "STORE.CREATED", id, "slug=" + slug);
        return getOrThrow(tenantId, id);
    }

    @Transactional
    public StoreResponse update(UUID tenantId, UUID storeId, UpdateStoreRequest request, Authentication auth) {
        StoreResponse existing = getOrThrow(tenantId, storeId);
        Instant now = Instant.now();
        if (request.name() != null && !request.name().isBlank()) {
            jdbc.update("UPDATE commerce_stores SET name = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?",
                    request.name().trim(), Timestamp.from(now), tenantId, storeId);
        }
        if (request.defaultLocale() != null) {
            String locale = request.defaultLocale().trim();
            if (locale.isEmpty() || locale.length() > 10) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "defaultLocale is invalid");
            }
            jdbc.update("UPDATE commerce_stores SET default_locale = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?",
                    locale, Timestamp.from(now), tenantId, storeId);
        }
        if (request.defaultCurrency() != null) {
            String currency = request.defaultCurrency().trim().toUpperCase();
            if (!currency.matches("^[A-Z]{3}$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "defaultCurrency must be an ISO-4217 currency code");
            }
            jdbc.update("UPDATE commerce_stores SET default_currency = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?",
                    currency, Timestamp.from(now), tenantId, storeId);
        }
        if (request.settings() != null) {
            jdbc.update("UPDATE commerce_stores SET settings = ?::jsonb, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?",
                    toJson(request.settings()), Timestamp.from(now), tenantId, storeId);
        }
        audit(tenantId, auth, "STORE.UPDATED", storeId, "name=" + existing.name());
        return getOrThrow(tenantId, storeId);
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> list(UUID tenantId) {
        return jdbc.query("SELECT * FROM commerce_stores WHERE tenant_id = ? ORDER BY created_at", this::mapRow, tenantId);
    }

    @Transactional(readOnly = true)
    public StoreResponse get(UUID tenantId, UUID storeId) {
        return getOrThrow(tenantId, storeId);
    }

    @Transactional
    public StoreResponse activate(UUID tenantId, UUID storeId, Authentication auth) {
        return transition(tenantId, storeId, "ACTIVE", "STORE.ACTIVATED", auth);
    }

    @Transactional
    public StoreResponse suspend(UUID tenantId, UUID storeId, Authentication auth) {
        return transition(tenantId, storeId, "SUSPENDED", "STORE.SUSPENDED", auth);
    }

    @Transactional
    public StoreResponse archive(UUID tenantId, UUID storeId, Authentication auth) {
        return transition(tenantId, storeId, "ARCHIVED", "STORE.ARCHIVED", auth);
    }

    @Transactional
    public StoreResponse setPrimary(UUID tenantId, UUID storeId, Authentication auth) {
        StoreResponse store = getOrThrow(tenantId, storeId);
        if (!"ACTIVE".equals(store.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "store must be ACTIVE before setting primary");
        }
        Instant now = Instant.now();
        jdbc.update("UPDATE commerce_stores SET is_primary = FALSE, updated_at = ? "
                        + "WHERE tenant_id = ? AND is_primary = TRUE", Timestamp.from(now), tenantId);
        jdbc.update("UPDATE commerce_stores SET is_primary = TRUE, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, storeId);
        audit(tenantId, auth, "STORE.SET_PRIMARY", storeId, "name=" + store.name());
        return getOrThrow(tenantId, storeId);
    }

    @Transactional(readOnly = true)
    public StoreSummary summarize(UUID tenantId) {
        Integer total = countFor("SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ?", tenantId);
        Integer active = countFor("SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND status = 'ACTIVE'", tenantId);
        Integer draft = countFor("SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND status = 'DRAFT'", tenantId);
        Integer suspended = countFor("SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND status = 'SUSPENDED'", tenantId);
        Integer archived = countFor("SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND status = 'ARCHIVED'", tenantId);
        Integer products = countFor("SELECT COUNT(*) FROM commerce_products WHERE tenant_id = ?", tenantId);
        Integer published = countFor("SELECT COUNT(*) FROM commerce_products WHERE tenant_id = ? AND status = 'PUBLISHED'", tenantId);
        Integer collections = countFor("SELECT COUNT(*) FROM commerce_collections WHERE tenant_id = ?", tenantId);
        Integer carts = countFor("SELECT COUNT(*) FROM commerce_carts WHERE tenant_id = ? AND status = 'ACTIVE'", tenantId);
        Integer orders = countFor("SELECT COUNT(*) FROM commerce_orders WHERE tenant_id = ?", tenantId);
        Integer paid = countFor("SELECT COUNT(*) FROM commerce_orders WHERE tenant_id = ? AND payment_status = 'PAID'", tenantId);
        return new StoreSummary(
                total, active, draft, suspended, archived, products, published, collections,
                carts, orders, paid);
    }

    // ===== Helpers =====
    private void enforceCreationLimit(UUID tenantId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> null, "STORE_LIMIT:" + tenantId);
        long limit = entitlementResolver.getLimit(tenantId, MODULE_CODE, STORE_LIMIT_CODE);
        if (limit <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "active ecommerce subscription entitlement is required"
            );
        }
        Long current = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND status <> 'ARCHIVED'",
                Long.class,
                tenantId
        );
        long used = current == null ? 0 : current;
        if (used >= limit) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "store limit for the subscription has been reached"
            );
        }
    }

    private String tenantLocale(UUID tenantId) {
        String locale = jdbc.queryForObject(
                "SELECT locale FROM tenants WHERE id = ?",
                String.class,
                tenantId
        );
        return locale == null || locale.isBlank() ? "ar-SA" : locale;
    }

    private String tenantCurrency(UUID tenantId) {
        String currency = jdbc.queryForObject(
                "SELECT currency_code FROM tenants WHERE id = ?",
                String.class,
                tenantId
        );
        if (currency == null || !currency.trim().toUpperCase().matches("^[A-Z]{3}$")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "tenant currency is not configured");
        }
        return currency.trim().toUpperCase();
    }

    private StoreResponse getOrThrow(UUID tenantId, UUID storeId) {
        try {
            return jdbc.queryForObject("SELECT * FROM commerce_stores WHERE tenant_id = ? AND id = ?",
                    this::mapRow, tenantId, storeId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "store not found: " + storeId);
        }
    }

    private StoreResponse transition(UUID tenantId, UUID storeId, String newStatus, String auditAction, Authentication auth) {
        StoreResponse existing = getOrThrow(tenantId, storeId);
        Instant now = Instant.now();
        jdbc.update("UPDATE commerce_stores SET status = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", newStatus, Timestamp.from(now), tenantId, storeId);
        audit(tenantId, auth, auditAction, storeId, "name=" + existing.name() + ",to=" + newStatus);
        return getOrThrow(tenantId, storeId);
    }

    private Integer countFor(String sql, UUID tenantId) {
        Integer v = jdbc.queryForObject(sql, Integer.class, tenantId);
        return v != null ? v : 0;
    }

    private String normalizeSlug(String raw) {
        if (raw == null) return "store";
        String s = raw.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (s.startsWith("-")) s = s.substring(1);
        if (s.endsWith("-")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) s = "store";
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    private String normalizeCode(String raw) {
        if (raw == null) return "STORE";
        String s = raw.trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_").replaceAll("_+", "_");
        if (s.startsWith("_")) s = s.substring(1);
        if (s.endsWith("_")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) s = "STORE";
        return s.length() > 50 ? s.substring(0, 50) : s;
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        auditService.success(auth, tenantId, action, "STORE",
                resourceId == null ? null : resourceId.toString(), reason, null, null);
    }

    private String toJson(Map<String, Object> map) {
        try { return objectMapper.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return null; }
    }

    private StoreResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StoreResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("name"), rs.getString("code"), rs.getString("slug"),
                CommerceDomain.StoreStatus.valueOf(rs.getString("status")),
                rs.getString("default_locale"), rs.getString("default_currency"),
                rs.getBoolean("is_primary"),
                fromJson(rs.getString("settings")),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
