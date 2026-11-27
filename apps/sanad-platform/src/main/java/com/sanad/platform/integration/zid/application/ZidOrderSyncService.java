package com.sanad.platform.integration.zid.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidConnector;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Order mirror synchronization against the VERIFIED orders endpoint
 * ({@code GET /v1/managers/store/orders}, scope orders.read).
 *
 * <p>Guarantees:</p>
 * <ul>
 *   <li><b>Idempotency</b> — UNIQUE (tenant_id, connection_id,
 *       zid_order_id) + ON CONFLICT DO UPDATE: the same order (from a
 *       webhook replay or a repeated pull) never produces a second
 *       row.</li>
 *   <li><b>Tenant binding</b> — every row carries the connection's
 *       trusted tenant; the worker path applies the tenant GUC
 *       explicitly.</li>
 *   <li><b>No financial side effects</b> — this service performs NO
 *       refunds, captures, cancellations or status changes on Zid. The
 *       mirror observes order state; mutations require a separate,
 *       explicitly authorized business scope.</li>
 *   <li><b>Minimal PII</b> — only the customer display name and guest
 *       flag are mirrored; emails/phones stay in Zid and are never
 *       persisted here (customer contact sync, if ever enabled, goes
 *       through blind indexes only).</li>
 * </ul>
 */
@Service
public class ZidOrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(ZidOrderSyncService.class);

    private final ZidConnector connector;
    private final ZidConnectionRepository connections;
    private final ZidAuthService authService;
    private final NamedParameterJdbcTemplate jdbc;
    private final ZidIntegrationProperties properties;
    private final ZidSyncAuditService auditService;
    private final TenantRlsTransactionContext tenantRlsContext;

    public ZidOrderSyncService(ZidConnector connector,
                               ZidConnectionRepository connections,
                               ZidAuthService authService,
                               NamedParameterJdbcTemplate jdbc,
                               ZidIntegrationProperties properties,
                               ZidSyncAuditService auditService,
                               TenantRlsTransactionContext tenantRlsContext) {
        this.connector = connector;
        this.connections = connections;
        this.authService = authService;
        this.jdbc = jdbc;
        this.properties = properties;
        this.auditService = auditService;
        this.tenantRlsContext = tenantRlsContext;
    }

    public record SyncResult(int processed, int failed, Instant completedAt) {
    }

    @Transactional
    public SyncResult syncConnection(UUID connectionId) {
        ZidConnectionRecord connection = connections.findById(connectionId)
                .orElseThrow(() -> new ZidIntegrationException(
                        ZidIntegrationException.Reason.TRUSTED_BINDING_NOT_FOUND,
                        "Connection not found"));
        tenantRlsContext.applyForCurrentTransaction(connection.tenantId());
        return sync(connection);
    }

    public SyncResult sync(ZidConnectionRecord connection) {
        int processed = 0;
        int failed = 0;
        String failureSummary = null;

        ZidAuthService.ZidCredentials credentials = authService.decryptCredentials(connection);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("payload_type", "simple");
        query.put("page", "1");
        query.put("per_page", String.valueOf(properties.getPageSize()));

        int page = 1;
        while (page <= properties.getMaxPagesPerSync()) {
            query.put("page", String.valueOf(page));
            ZidConnector.ZidResponse response = connector.get(
                    credentials.toConnectionCredentials(), "managers/store/orders", query);
            if (!response.isSuccess() || response.body() == null) {
                failed++;
                failureSummary = "orders pull failed on page " + page
                        + " (status " + response.statusCode() + ")";
                break;
            }
            JsonNode orders = response.body().path("orders");
            if (!orders.isArray() || orders.isEmpty()) {
                break;
            }
            for (JsonNode order : orders) {
                try {
                    upsertOrder(connection, order);
                    processed++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("Zid order upsert failed for store {}: skipping item",
                            connection.zidStoreId());
                }
            }
            // The verified orders payload has no explicit "next" link; pagination ends
            // when the page returns fewer items than requested.
            if (orders.size() < properties.getPageSize()) {
                break;
            }
            page++;
        }

        connections.markSynced(connection.id(), Instant.now());
        auditService.record(connection.tenantId(), connection.id(),
                ZidSyncAuditService.SyncType.ORDERS, "orders",
                ZidSyncAuditService.Direction.PULL,
                failed == 0 ? ZidSyncAuditService.Status.SUCCESS
                        : (processed > 0 ? ZidSyncAuditService.Status.PARTIAL : ZidSyncAuditService.Status.FAILED),
                processed, failed, failureSummary, null);
        return new SyncResult(processed, failed, Instant.now());
    }

    void upsertOrder(ZidConnectionRecord connection, JsonNode order) {
        String zidOrderId = order.path("id").isNumber()
                ? String.valueOf(order.path("id").asLong())
                : order.path("id").asText(null);
        if (zidOrderId == null || zidOrderId.isBlank()) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TRANSIENT_FAILURE,
                    "Order payload missing id");
        }
        jdbc.update("""
                INSERT INTO zid_orders
                    (tenant_id, connection_id, zid_order_id, code, status_code, status_name,
                     payment_status, order_total, currency, customer_name, is_guest_customer,
                     requires_shipping, zid_created_at, zid_updated_at, synced_at,
                     raw_payload, created_at, updated_at)
                VALUES
                    (:tenantId, :connectionId, :zidOrderId, :code, :statusCode, :statusName,
                     :paymentStatus, :orderTotal, :currency, :customerName, :isGuest,
                     :requiresShipping, :zidCreatedAt, :zidUpdatedAt, NOW(),
                     :rawPayload::jsonb, NOW(), NOW())
                ON CONFLICT (tenant_id, connection_id, zid_order_id) DO UPDATE SET
                    code = EXCLUDED.code,
                    status_code = EXCLUDED.status_code,
                    status_name = EXCLUDED.status_name,
                    payment_status = EXCLUDED.payment_status,
                    order_total = EXCLUDED.order_total,
                    currency = EXCLUDED.currency,
                    customer_name = EXCLUDED.customer_name,
                    is_guest_customer = EXCLUDED.is_guest_customer,
                    requires_shipping = EXCLUDED.requires_shipping,
                    zid_created_at = EXCLUDED.zid_created_at,
                    zid_updated_at = EXCLUDED.zid_updated_at,
                    synced_at = NOW(),
                    raw_payload = EXCLUDED.raw_payload,
                    updated_at = NOW()
                """, new MapSqlParameterSource()
                .addValue("tenantId", connection.tenantId())
                .addValue("connectionId", connection.id())
                .addValue("zidOrderId", zidOrderId)
                .addValue("code", ZidProductSyncService.textOrNull(order, "code"))
                .addValue("statusCode", order.path("order_status").path("code").asText(null))
                .addValue("statusName", order.path("order_status").path("name").asText(null))
                .addValue("paymentStatus", ZidProductSyncService.textOrNull(order, "payment_status"))
                .addValue("orderTotal", ZidProductSyncService.decimalOrNull(order, "order_total"))
                .addValue("currency", ZidProductSyncService.textOrNull(order, "currency_code"))
                .addValue("customerName", order.path("customer").path("name").asText(null))
                .addValue("isGuest", order.path("is_guest_customer").asBoolean(false))
                .addValue("requiresShipping", order.path("requires_shipping").asBoolean(false))
                .addValue("zidCreatedAt", ZidProductSyncService.timestampOrNull(order, "created_at"))
                .addValue("zidUpdatedAt", ZidProductSyncService.timestampOrNull(order, "updated_at"))
                .addValue("rawPayload", order.toString()));
    }
}
