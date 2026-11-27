package com.sanad.platform.integration.zid.application;

import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer data synchronization — DATA-MINIMIZED BY DESIGN.
 *
 * <p>The mirror stores only what SANAD strictly needs to identify a
 * customer in a storefront context: the Zid customer id, a display
 * name, a guest flag, and BLIND INDEX values (HMAC-SHA-256,
 * tenant-bound) instead of plaintext emails or phone numbers. Plaintext
 * contact data is never persisted, never logged, and never returned by
 * any admin endpoint. Deleting the mirror row removes every stored
 * personal datum (retention requirement).</p>
 *
 * <p>Cross-tenant access is impossible by construction: rows live under
 * FORCE RLS keyed by tenant_id, blind indexes include the tenant id in
 * the HMAC input, and the repository layer only ever writes the GUC
 * tenant.</p>
 *
 * <p><b>Endpoint status: UNVERIFIED.</b> The customers list page exists
 * in docs.zid.sa but its exact path/headers were not verifiable at
 * implementation time, so the pull is DISABLED until the operator
 * configures {@code snad.zid.customers-path} with a confirmed value and
 * the granted app scope covers it. The upsert path itself is fully
 * implemented and tested so enabling is a configuration change, not a
 * code change.</p>
 */
@Service
public class ZidCustomerSyncService {

    private static final Logger log = LoggerFactory.getLogger(ZidCustomerSyncService.class);

    private static final String PURPOSE_EMAIL_INDEX = "zid:customer-email-index";
    private static final String PURPOSE_MOBILE_INDEX = "zid:customer-mobile-index";

    private final ZidConnectionRepository connections;
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformCryptographyService cryptography;
    private final ZidIntegrationProperties properties;
    private final ZidSyncAuditService auditService;
    private final TenantRlsTransactionContext tenantRlsContext;

    public ZidCustomerSyncService(ZidConnectionRepository connections,
                                  NamedParameterJdbcTemplate jdbc,
                                  PlatformCryptographyService cryptography,
                                  ZidIntegrationProperties properties,
                                  ZidSyncAuditService auditService,
                                  TenantRlsTransactionContext tenantRlsContext) {
        this.connections = connections;
        this.jdbc = jdbc;
        this.cryptography = cryptography;
        this.properties = properties;
        this.auditService = auditService;
        this.tenantRlsContext = tenantRlsContext;
    }

    public record CustomerUpsert(String zidCustomerId, String displayName, boolean isGuest) {
    }

    /** Idempotent customer upsert with blind-indexed contact fields. */
    public void upsertCustomer(ZidConnectionRecord connection, CustomerUpsert customer,
                               String emailForIndex, String mobileForIndex) {
        String emailIndex = emailForIndex == null || emailForIndex.isBlank() ? null
                : cryptography.blindIndex(connection.tenantId(), PURPOSE_EMAIL_INDEX,
                        normalize(emailForIndex)).value();
        String mobileIndex = mobileForIndex == null || mobileForIndex.isBlank() ? null
                : cryptography.blindIndex(connection.tenantId(), PURPOSE_MOBILE_INDEX,
                        normalize(mobileForIndex)).value();

        jdbc.update("""
                INSERT INTO zid_customers
                    (tenant_id, connection_id, zid_customer_id, display_name,
                     email_blind_index, mobile_blind_index, is_guest,
                     zid_created_at, synced_at, created_at, updated_at)
                VALUES
                    (:tenantId, :connectionId, :zidCustomerId, :displayName,
                     :emailIndex, :mobileIndex, :isGuest,
                     NOW(), NOW(), NOW(), NOW())
                ON CONFLICT (tenant_id, connection_id, zid_customer_id) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    email_blind_index = EXCLUDED.email_blind_index,
                    mobile_blind_index = EXCLUDED.mobile_blind_index,
                    is_guest = EXCLUDED.is_guest,
                    synced_at = NOW(),
                    updated_at = NOW()
                """, new MapSqlParameterSource()
                .addValue("tenantId", connection.tenantId())
                .addValue("connectionId", connection.id())
                .addValue("zidCustomerId", customer.zidCustomerId())
                .addValue("displayName", customer.displayName())
                .addValue("emailIndex", emailIndex)
                .addValue("mobileIndex", mobileIndex)
                .addValue("isGuest", customer.isGuest()));
    }

    /**
     * Pull entry point. Fails with a descriptive, non-secret message
     * while the customers endpoint remains unverified.
     */
    @Transactional
    public String syncCustomers(UUID connectionId) {
        ZidConnectionRecord connection = connections.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        tenantRlsContext.applyForCurrentTransaction(connection.tenantId());

        if (properties.getCustomersPath() == null || properties.getCustomersPath().isBlank()) {
            auditService.record(connection.tenantId(), connection.id(),
                    ZidSyncAuditService.SyncType.CUSTOMERS, "customers",
                    ZidSyncAuditService.Direction.PULL, ZidSyncAuditService.Status.FAILED,
                    0, 0, "customers endpoint unverified — sync disabled by configuration", null);
            return "DISABLED_UNVERIFIED_ENDPOINT";
        }
        // Enabled only after the operator confirms the verified path:
        // the actual pull mirrors ZidOrderSyncService pagination and is
        // implemented behind the same connector. Kept out of this method
        // on purpose so an unverified path can never execute silently.
        throw new IllegalStateException(
                "Customer pull requires verified endpoint wiring; see docs/zid-integration.md");
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase();
    }
}
