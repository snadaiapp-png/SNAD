package com.sanad.platform.crm.web;

import com.sanad.platform.crm.ownership.application.TransferQueryUseCases;
import com.sanad.platform.crm.ownership.application.TransferUseCases;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.TransferPolicy;
import com.sanad.platform.crm.ownership.domain.TransferRequest;
import com.sanad.platform.crm.ownership.domain.TransferState;
import com.sanad.platform.crm.ownership.domain.TransferStepDecision;
import com.sanad.platform.crm.ownership.domain.TransferType;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CRM-008 governed transfer surface: exactly five public operations. */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmOwnershipTransferController {

    private final TransferUseCases transfers;
    private final TransferQueryUseCases queries;
    private final CrmOwnershipHttpSupport http;

    public CrmOwnershipTransferController(
            TransferUseCases transfers,
            TransferQueryUseCases queries,
            CrmOwnershipHttpSupport http) {
        this.transfers = transfers;
        this.queries = queries;
        this.http = http;
    }

    @RequireCapability("CRM.TRANSFER.READ")
    @GetMapping("/transfers")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipListResponse<TransferRequest>> listTransfers(
            Authentication authentication,
            @RequestParam(required = false) TransferQueryUseCases.Direction direction,
            @RequestParam(required = false) TransferState state,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<TransferRequest> data = queries.list(
                context.tenantId(), context.userId(), direction, state);
        List<TransferRequest> bounded = data.stream().limit(pageSize).toList();
        return http.list(bounded, null, data.size() > bounded.size(), pageSize, http.trace(request));
    }

    @RequireCapability("CRM.TRANSFER.REQUEST")
    @PostMapping("/transfers")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TransferRequest>> createTransfer(
            Authentication authentication,
            @Valid @RequestBody CreateTransferRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = http.begin(authentication, "POST:/api/v2/crm/transfers", key, body, request);
        if (guard.isReplay()) return http.replay(guard, TransferRequest.class);
        try {
            var context = http.context(authentication);
            TransferRequest created = transfers.createDraft(
                    context.tenantId(), context.userId(),
                    new TransferUseCases.CreateTransferCommand(
                            body.recordType(), body.recordIds(), body.proposedOwnerType(),
                            body.proposedOwnerId(), body.transferType(), body.temporaryEndDate(),
                            body.reason(), body.policy()));
            return http.complete(
                    guard, created, TransferRequest.class, HttpStatus.CREATED,
                    "transfer-request", created.id(), http.timestampVersion(created.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.TRANSFER.REQUEST")
    @PostMapping("/transfers/{transferId}/submit")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TransferRequest>> submitTransfer(
            Authentication authentication,
            @PathVariable UUID transferId,
            @Valid @RequestBody SubmitTransferRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/transfers/" + transferId + "/submit";
        var guard = http.begin(authentication, endpoint, key, body, request);
        if (guard.isReplay()) return http.replay(guard, TransferRequest.class);
        try {
            var context = http.context(authentication);
            TransferRequest current = transfers.get(context.tenantId(), transferId);
            http.validateIfMatch(
                    ifMatch, "transfer-request", transferId,
                    http.timestampVersion(current.updatedAt()));
            TransferRequest submitted = transfers.submit(
                    context.tenantId(), context.userId(), transferId, body.approverUserIds());
            return http.complete(
                    guard, submitted, TransferRequest.class, HttpStatus.OK,
                    "transfer-request", submitted.id(),
                    http.timestampVersion(submitted.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.TRANSFER.APPROVE")
    @PostMapping("/transfers/{transferId}/approve")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TransferRequest>> decideTransfer(
            Authentication authentication,
            @PathVariable UUID transferId,
            @Valid @RequestBody DecideTransferRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/transfers/" + transferId + "/approve";
        var guard = http.begin(authentication, endpoint, key, body, request);
        if (guard.isReplay()) return http.replay(guard, TransferRequest.class);
        try {
            var context = http.context(authentication);
            TransferRequest current = transfers.get(context.tenantId(), transferId);
            http.validateIfMatch(
                    ifMatch, "transfer-request", transferId,
                    http.timestampVersion(current.updatedAt()));
            TransferRequest decided = transfers.decide(
                    context.tenantId(), context.userId(), transferId,
                    body.decision(), body.comment());
            return http.complete(
                    guard, decided, TransferRequest.class, HttpStatus.OK,
                    "transfer-request", decided.id(), http.timestampVersion(decided.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.TRANSFER.REQUEST")
    @PostMapping("/transfers/{transferId}/cancel")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TransferRequest>> cancelTransfer(
            Authentication authentication,
            @PathVariable UUID transferId,
            @Valid @RequestBody CancelTransferRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/transfers/" + transferId + "/cancel";
        var guard = http.begin(authentication, endpoint, key, body, request);
        if (guard.isReplay()) return http.replay(guard, TransferRequest.class);
        try {
            var context = http.context(authentication);
            TransferRequest current = transfers.get(context.tenantId(), transferId);
            http.validateIfMatch(
                    ifMatch, "transfer-request", transferId,
                    http.timestampVersion(current.updatedAt()));
            TransferRequest cancelled = transfers.cancel(
                    context.tenantId(), context.userId(), transferId, body.reason());
            return http.complete(
                    guard, cancelled, TransferRequest.class, HttpStatus.OK,
                    "transfer-request", cancelled.id(),
                    http.timestampVersion(cancelled.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    public record CreateTransferRequest(
            @NotNull AssignmentRecordType recordType,
            @NotEmpty @Size(max = 100) List<UUID> recordIds,
            @NotNull OwnerType proposedOwnerType,
            @NotNull UUID proposedOwnerId,
            @NotNull TransferType transferType,
            Instant temporaryEndDate,
            @NotBlank @Size(max = 500) String reason,
            @NotNull TransferPolicy policy) {
        public CreateTransferRequest {
            recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
        }
    }

    public record SubmitTransferRequest(List<UUID> approverUserIds) {
        public SubmitTransferRequest {
            approverUserIds = approverUserIds == null ? List.of() : List.copyOf(approverUserIds);
        }
    }

    public record DecideTransferRequest(
            @NotNull TransferStepDecision decision,
            @Size(max = 1000) String comment) { }

    public record CancelTransferRequest(
            @NotBlank @Size(max = 500) String reason) { }
}
