package com.sanad.platform.hr.compatibility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 7 — per-tenant HRM migration phase gate.
 *
 * <p>States (hr_migration_tenant_state):
 * LEGACY (v1 authoritative, v1 create allowed under unambiguity rules),
 * MIGRATING (read freeze active: v1 reads OK, v1 writes 409),
 * CANONICAL (v2 authoritative; v1 reads project from canonical data),
 * BLOCKED (cutover halted; same write semantics as MIGRATING).
 */
@Service
public class HrMigrationStateService {

    public enum TenantMigrationState { LEGACY, MIGRATING, CANONICAL, BLOCKED }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public HrMigrationStateService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    /** Resolves the tenant's migration state; a missing row defaults to LEGACY. */
    public TenantMigrationState state(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return transactionTemplate.execute(status -> {
            bindTenant(tenantId);
            List<String> rows = jdbc.query(
                    "SELECT state FROM hr_migration_tenant_state WHERE tenant_id = ?",
                    (rs, rowNum) -> rs.getString(1), tenantId);
            if (rows.isEmpty()) {
                return TenantMigrationState.LEGACY;
            }
            return TenantMigrationState.valueOf(rows.get(0));
        });
    }

    public boolean allowsV1Writes(UUID tenantId) {
        TenantMigrationState state = state(tenantId);
        return state == TenantMigrationState.LEGACY || state == TenantMigrationState.CANONICAL;
    }

    private void bindTenant(UUID tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }
}
