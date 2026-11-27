package com.sanad.platform.integration.zid.application;

import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.dto.ZidConnectionStatusResponse;
import com.sanad.platform.integration.zid.dto.ZidSyncSummaryResponse;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.integration.zid.infrastructure.ZidLinkStateRepository;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped orchestration facade for the admin API: resolves the
 * tenant's connection, dispatches syncs under the correct transaction
 * scope, and shapes the read models the SPA consumes. All read models
 * are token-free by construction — they never select the encrypted
 * token columns.
 */
@Service
public class ZidIntegrationFacade {

    private final ZidConnectionRepository connections;
    private final ZidLinkStateRepository linkStates;
    private final ZidTenantBindingService bindingService;
    private final ZidProductSyncService productSyncService;
    private final ZidOrderSyncService orderSyncService;
    private final ZidInventorySyncService inventorySyncService;
    private final ZidCustomerSyncService customerSyncService;
    private final ZidReconciliationService reconciliationService;
    private final NamedParameterJdbcTemplate jdbc;
    private final ZidIntegrationProperties properties;
    private final TenantRlsTransactionContext tenantRlsContext;

    public ZidIntegrationFacade(ZidConnectionRepository connections,
                                ZidLinkStateRepository linkStates,
                                ZidTenantBindingService bindingService,
                                ZidProductSyncService productSyncService,
                                ZidOrderSyncService orderSyncService,
                                ZidInventorySyncService inventorySyncService,
                                ZidCustomerSyncService customerSyncService,
                                ZidReconciliationService reconciliationService,
                                NamedParameterJdbcTemplate jdbc,
                                ZidIntegrationProperties properties,
                                TenantRlsTransactionContext tenantRlsContext) {
        this.connections = connections;
        this.linkStates = linkStates;
        this.bindingService = bindingService;
        this.productSyncService = productSyncService;
        this.orderSyncService = orderSyncService;
        this.inventorySyncService = inventorySyncService;
        this.customerSyncService = customerSyncService;
        this.reconciliationService = reconciliationService;
        this.jdbc = jdbc;
        this.properties = properties;
        this.tenantRlsContext = tenantRlsContext;
    }

    public String linkRedirectUrl() {
        return properties.getLinkRedirectUrl();
    }

    /**
     * Resolves the tenant for an OAuth callback from the stored state
     * hash. Returns null for unknown states; completeLink re-validates
     * status/expiry/tenancy before claiming it.
     */
    public UUID tenantForState(String rawState) {
        String stateHash = sha256(rawState);
        return linkStates.findByStateHash(stateHash)
                .map(ZidLinkStateRepository.ZidLinkStateRecord::tenantId)
                .orElse(null);
    }

    public Optional<ZidConnectionStatusResponse> connectionStatus(UUID tenantId) {
        return connections.findAll().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .findFirst()
                .map(ZidConnectionStatusResponse::from);
    }

    @Transactional
    public boolean unlink(UUID tenantId) {
        List<ZidConnectionRecord> own = connections.findAll().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .toList();
        if (own.isEmpty()) {
            return false;
        }
        bindingService.unlink(tenantId, own.get(0).id());
        return true;
    }

    @Transactional
    public ZidProductSyncService.SyncResult syncProductsForTenant(UUID tenantId) {
        ZidConnectionRecord connection = requireConnection(tenantId);
        tenantRlsContext.applyForCurrentTransaction(tenantId);
        return productSyncService.sync(connection);
    }

    @Transactional
    public ZidOrderSyncService.SyncResult syncOrdersForTenant(UUID tenantId) {
        ZidConnectionRecord connection = requireConnection(tenantId);
        tenantRlsContext.applyForCurrentTransaction(tenantId);
        return orderSyncService.sync(connection);
    }

    @Transactional
    public ZidInventorySyncService.InventoryResult syncInventoryForTenant(UUID tenantId) {
        ZidConnectionRecord connection = requireConnection(tenantId);
        tenantRlsContext.applyForCurrentTransaction(tenantId);
        return inventorySyncService.syncInventory(connection.id());
    }

    @Transactional
    public String syncCustomersForTenant(UUID tenantId) {
        ZidConnectionRecord connection = requireConnection(tenantId);
        tenantRlsContext.applyForCurrentTransaction(tenantId);
        return customerSyncService.syncCustomers(connection.id());
    }

    @Transactional
    public Map<String, Object> reconcileForTenant(UUID tenantId) {
        ZidConnectionRecord connection = requireConnection(tenantId);
        tenantRlsContext.applyForCurrentTransaction(tenantId);
        return reconciliationService.reconcile(connection.id());
    }

    public ZidSyncSummaryResponse summary(UUID tenantId) {
        List<ZidConnectionRecord> own = connections.findAll().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .toList();
        if (own.isEmpty()) {
            return ZidSyncSummaryResponse.notLinked();
        }
        ZidConnectionRecord connection = own.get(0);

        Integer products = count("SELECT COUNT(*) FROM zid_products WHERE connection_id = :id", connection.id());
        Integer publishedProducts = count(
                "SELECT COUNT(*) FROM zid_products WHERE connection_id = :id AND is_published = TRUE",
                connection.id());
        Integer stocks = count("SELECT COUNT(*) FROM zid_product_stocks WHERE connection_id = :id", connection.id());
        Integer orders = count("SELECT COUNT(*) FROM zid_orders WHERE connection_id = :id", connection.id());
        Integer failedEvents = count(
                "SELECT COUNT(*) FROM zid_webhook_events WHERE connection_id = :id "
                        + "AND status IN ('FAILED', 'FAILED_MANUAL')", connection.id());
        Integer conflicts = count(
                "SELECT COUNT(*) FROM zid_sync_audit WHERE tenant_id = :tenantId "
                        + "AND status IN ('CONFLICT', 'FAILED') AND started_at > NOW() - INTERVAL '30 days'",
                connection.id(), tenantId);

        return new ZidSyncSummaryResponse(
                connection.status(),
                connection.zidStoreId(),
                connection.zidStoreName(),
                connection.lastSyncAt(),
                products, publishedProducts, stocks, orders,
                failedEvents, conflicts);
    }

    public List<Map<String, Object>> recentAudit(UUID tenantId) {
        return jdbc.queryForList("""
                SELECT sync_type, entity, direction, status, items_processed, items_failed,
                       error_summary, correlation_id, started_at, finished_at
                FROM zid_sync_audit
                WHERE tenant_id = :tenantId
                ORDER BY started_at DESC
                LIMIT 50
                """, new MapSqlParameterSource("tenantId", tenantId));
    }

    public List<Map<String, Object>> recentWebhookEvents(UUID tenantId) {
        return jdbc.queryForList("""
                SELECT zid_store_id, event_type, status, attempts, last_error, received_at, processed_at
                FROM zid_webhook_events
                WHERE tenant_id = :tenantId
                ORDER BY received_at DESC
                LIMIT 50
                """, new MapSqlParameterSource("tenantId", tenantId));
    }

    private ZidConnectionRecord requireConnection(UUID tenantId) {
        return connections.findAll().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("NO_LINKED_CONNECTION"));
    }

    private Integer count(String sql, Object... params) {
        MapSqlParameterSource source = new MapSqlParameterSource();
        if (params.length >= 1) source.addValue("id", params[0]);
        if (params.length >= 2) source.addValue("tenantId", params[1]);
        Integer value = jdbc.queryForObject(sql, source, Integer.class);
        return value == null ? 0 : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
