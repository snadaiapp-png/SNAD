package com.sanad.platform.crm.ownership.application;

import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.OwnershipHistoryPage;
import com.sanad.platform.crm.ownership.domain.OwnershipReadPort;
import com.sanad.platform.crm.ownership.domain.WorkloadSummary;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped read service for current ownership, ledger history and My Work. */
public class OwnershipQueryUseCases {

    private final OwnershipReadPort reads;

    public OwnershipQueryUseCases(OwnershipReadPort reads) {
        this.reads = reads;
    }

    public Optional<Assignment> current(UUID tenantId,
                                        AssignmentRecordType recordType,
                                        UUID recordId) {
        require(tenantId, recordType, recordId);
        return reads.findActiveAssignment(tenantId, recordType, recordId);
    }

    public OwnershipHistoryPage history(UUID tenantId,
                                        AssignmentRecordType recordType,
                                        UUID recordId,
                                        UUID cursor,
                                        int pageSize) {
        require(tenantId, recordType, recordId);
        return reads.findOwnershipHistory(
                tenantId, recordType, recordId, cursor, Math.max(1, Math.min(pageSize, 100)));
    }

    public WorkloadSummary workload(UUID tenantId, UUID userId) {
        requireUser(tenantId, userId);
        return reads.findUserWorkload(tenantId, userId);
    }

    public int queueClaimCount(UUID tenantId, UUID userId) {
        requireUser(tenantId, userId);
        return reads.findUserQueueClaimCount(tenantId, userId);
    }

    private void require(UUID tenantId, AssignmentRecordType type, UUID recordId) {
        if (tenantId == null || type == null || recordId == null) {
            throw new IllegalArgumentException("tenantId, recordType and recordId required");
        }
    }

    private void requireUser(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("tenantId and userId required");
        }
    }
}
