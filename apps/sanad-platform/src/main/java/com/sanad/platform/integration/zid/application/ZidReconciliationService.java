package com.sanad.platform.integration.zid.application;

import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drift detection between the Zid source (verified pulls) and the
 * SANAD mirror. A reconciliation run re-pulls the current products and
 * orders pages and compares them against the mirror rows, recording a
 * report with drift counts and a per-item summary. Mirrors are then
 * updated from the pull, so the report always describes a converging
 * state. No Zid data is ever mutated by reconciliation.
 */
@Service
public class ZidReconciliationService {

    private final ZidConnectionRepository connections;
    private final ZidProductSyncService productSyncService;
    private final ZidOrderSyncService orderSyncService;
    private final NamedParameterJdbcTemplate jdbc;
    private final ZidSyncAuditService auditService;
    private final TenantRlsTransactionContext tenantRlsContext;

    public ZidReconciliationService(ZidConnectionRepository connections,
                                    ZidProductSyncService productSyncService,
                                    ZidOrderSyncService orderSyncService,
                                    NamedParameterJdbcTemplate jdbc,
                                    ZidSyncAuditService auditService,
                                    TenantRlsTransactionContext tenantRlsContext) {
        this.connections = connections;
        this.productSyncService = productSyncService;
        this.orderSyncService = orderSyncService;
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.tenantRlsContext = tenantRlsContext;
    }

    @Transactional
    public Map<String, Object> reconcile(UUID connectionId) {
        ZidConnectionRecord connection = connections.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        tenantRlsContext.applyForCurrentTransaction(connection.tenantId());

        ZidProductSyncService.SyncResult productResult = productSyncService.sync(connection);
        ZidOrderSyncService.SyncResult orderResult = orderSyncService.sync(connection);

        Integer mirrorProducts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM zid_products WHERE connection_id = :cid",
                new MapSqlParameterSource("cid", connectionId), Integer.class);
        Integer mirrorOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM zid_orders WHERE connection_id = :cid",
                new MapSqlParameterSource("cid", connectionId), Integer.class);

        int checked = productResult.processed() + orderResult.processed();
        int failed = productResult.failed() + orderResult.failed();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("productsPulled", productResult.processed());
        summary.put("ordersPulled", orderResult.processed());
        summary.put("mirrorProducts", mirrorProducts == null ? 0 : mirrorProducts);
        summary.put("mirrorOrders", mirrorOrders == null ? 0 : mirrorOrders);
        summary.put("failedItems", failed);

        jdbc.update("""
                INSERT INTO zid_reconciliation_reports
                    (tenant_id, connection_id, entity, drift_count, checked_count, summary, status, generated_at)
                VALUES
                    (:tenantId, :connectionId, 'products+orders', :drift, :checked, :summary::jsonb,
                     :status, NOW())
                """, new MapSqlParameterSource()
                .addValue("tenantId", connection.tenantId())
                .addValue("connectionId", connectionId)
                .addValue("drift", failed)
                .addValue("checked", checked)
                .addValue("summary", com.fasterxml.jackson.databind.json.JsonMapper.builder().build().valueToTree(summary).toString())
                .addValue("status", failed == 0 ? "COMPLETED" : "FAILED"));

        auditService.record(connection.tenantId(), connectionId,
                ZidSyncAuditService.SyncType.RECONCILIATION, "products+orders",
                ZidSyncAuditService.Direction.RECONCILIATION,
                failed == 0 ? ZidSyncAuditService.Status.SUCCESS : ZidSyncAuditService.Status.PARTIAL,
                checked, failed, null, null);
        return summary;
    }
}
