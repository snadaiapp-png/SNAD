package com.sanad.platform.module.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of a module reset execution.
 *
 * @param tenantId     the tenant whose data was reset
 * @param moduleCode   the module code
 * @param status       RESET_COMPLETED | RESET_FAILED | RESET_PARTIAL
 * @param tableResults per-table results (table name + rows deleted + success/failure)
 * @param totalRowsDeleted total rows deleted across all tables
 * @param startedAt    when the reset started
 * @param completedAt  when the reset completed
 * @param errorMessage error message if status != RESET_COMPLETED
 */
public record ModuleResetResult(
        UUID tenantId,
        String moduleCode,
        String status,
        List<TableResetResult> tableResults,
        long totalRowsDeleted,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
    public static final String STATUS_COMPLETED = "RESET_COMPLETED";
    public static final String STATUS_FAILED = "RESET_FAILED";
    public static final String STATUS_PARTIAL = "RESET_PARTIAL";

    /**
     * Per-table reset result.
     *
     * @param tableName    the table name
     * @param rowsDeleted   number of rows deleted
     * @param success       whether the delete succeeded
     * @param errorMessage  error message if failed
     */
    public record TableResetResult(
            String tableName,
            int rowsDeleted,
            boolean success,
            String errorMessage
    ) {}
}
