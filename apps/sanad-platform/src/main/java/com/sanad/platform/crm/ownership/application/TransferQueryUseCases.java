package com.sanad.platform.crm.ownership.application;

import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.TransferRequest;
import com.sanad.platform.crm.ownership.domain.TransferRequestRepository;
import com.sanad.platform.crm.ownership.domain.TransferState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped incoming/outgoing transfer inbox queries. */
public class TransferQueryUseCases {

    private final TransferRequestRepository transfers;

    public TransferQueryUseCases(TransferRequestRepository transfers) {
        this.transfers = transfers;
    }

    public List<TransferRequest> list(
            UUID tenantId,
            UUID userId,
            Direction direction,
            TransferState state) {
        if (tenantId == null || userId == null) {
            throw new OwnershipDomainException("tenantId and userId required");
        }
        Direction effectiveDirection = direction == null ? Direction.ALL : direction;
        List<TransferRequest> source = switch (effectiveDirection) {
            case OUTGOING -> transfers.findByRequester(tenantId, userId);
            case INCOMING -> transfers.findByProposedOwner(tenantId, userId);
            case ALL -> merge(
                    transfers.findByRequester(tenantId, userId),
                    transfers.findByProposedOwner(tenantId, userId));
        };
        return source.stream()
                .filter(value -> state == null || value.state() == state)
                .sorted(java.util.Comparator.comparing(TransferRequest::updatedAt).reversed()
                        .thenComparing(TransferRequest::id))
                .toList();
    }

    private List<TransferRequest> merge(
            List<TransferRequest> outgoing,
            List<TransferRequest> incoming) {
        LinkedHashMap<UUID, TransferRequest> unique = new LinkedHashMap<>();
        outgoing.forEach(value -> unique.put(value.id(), value));
        incoming.forEach(value -> unique.put(value.id(), value));
        return List.copyOf(unique.values());
    }

    public enum Direction {
        INCOMING,
        OUTGOING,
        ALL
    }
}
