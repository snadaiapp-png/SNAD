package com.sanad.platform.crm.party.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.application.AccountMasterUseCases;
import com.sanad.platform.crm.party.application.AccountMasterUseCases.AccountMasterView;
import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.AccountProfileRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.AccountRelationshipRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.CreateAccountRelationshipCommand;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.CreateExternalIdentifierCommand;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.ExternalIdentifierRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.OwnershipHistoryRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.ProjectionSnapshotRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.StatusHistoryRecord;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.UpdateAccountProfileCommand;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.web.AccountMasterModels.AccountHistoryResponse;
import com.sanad.platform.crm.party.web.AccountMasterModels.AccountMasterOverviewResponse;
import com.sanad.platform.crm.party.web.AccountMasterModels.AccountProfileResponse;
import com.sanad.platform.crm.party.web.AccountMasterModels.AccountRelationshipResponse;
import com.sanad.platform.crm.party.web.AccountMasterModels.CreateAccountRelationshipRequest;
import com.sanad.platform.crm.party.web.AccountMasterModels.CreateExternalIdentifierRequest;
import com.sanad.platform.crm.party.web.AccountMasterModels.EndAccountRelationshipRequest;
import com.sanad.platform.crm.party.web.AccountMasterModels.ExternalIdentifierResponse;
import com.sanad.platform.crm.party.web.AccountMasterModels.OwnershipHistoryResponse;
import com.sanad.platform.crm.party.web.AccountMasterModels.ProjectionResponse;
import com.sanad.platform.crm.party.web.AccountMasterModels.RiskProfileResponse;
import com.sanad.platform.crm.party.web.AccountMasterModels.StatusHistoryResponse;
import com.sanad.platform.crm.party.web.AccountMasterModels.UpdateAccountProfileRequest;
import com.sanad.platform.crm.party.web.AccountMasterModels.UpdateAccountRiskRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Enterprise Account/Customer Master API. */
@RestController
@RequestMapping("/api/v2/crm/accounts")
public class AccountMasterController {
    private final AccountMasterUseCases master;
    private final AccountUseCases accounts;
    private final ETagService etags;
    private final ObjectMapper objectMapper;

    public AccountMasterController(
            AccountMasterUseCases master,
            AccountUseCases accounts,
            ETagService etags,
            ObjectMapper objectMapper) {
        this.master = master;
        this.accounts = accounts;
        this.etags = etags;
        this.objectMapper = objectMapper;
    }

    @RequireCapability("CRM.ACCOUNT.MASTER.READ")
    @GetMapping("/{accountId}/master")
    public ResponseEntity<AccountMasterOverviewResponse> getMaster(
            Authentication authentication, @PathVariable UUID accountId) {
        AccountMasterView view = master.get(tenantId(authentication), accountId);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG,
                        etags.etag("account-master", accountId, view.profile().version()))
                .body(toOverview(view));
    }

    @RequireCapability("CRM.ACCOUNT.MASTER.WRITE")
    @PutMapping("/{accountId}/master/profile")
    public ResponseEntity<AccountProfileResponse> updateProfile(
            Authentication authentication,
            @PathVariable UUID accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateAccountProfileRequest request) {
        AccountProfileRecord current = master.get(tenantId(authentication), accountId).profile();
        etags.validateIfMatch(ifMatch, "account-master", accountId, current.version());
        AccountProfileRecord updated = master.updateProfile(
                tenantId(authentication), userId(authentication), accountId,
                new UpdateAccountProfileCommand(
                        request.legalName(), request.tradeName(), request.registrationNumber(),
                        request.taxRegistrationNumber(), request.industry(), request.organizationSize(),
                        request.websiteUrl(), request.customerTier(), null, null,
                        request.classificationId(), request.segmentId(), null),
                current.version());
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG,
                        etags.etag("account-master", accountId, updated.version()))
                .body(toProfile(updated));
    }

    @RequireCapability("CRM.ACCOUNT.RISK.READ")
    @GetMapping("/{accountId}/master/risk")
    public ResponseEntity<RiskProfileResponse> getRisk(
            Authentication authentication, @PathVariable UUID accountId) {
        AccountProfileRecord profile = master.get(tenantId(authentication), accountId).profile();
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG,
                        etags.etag("account-master", accountId, profile.version()))
                .body(toRisk(profile));
    }

    @RequireCapability("CRM.ACCOUNT.RISK.WRITE")
    @PutMapping("/{accountId}/master/risk")
    public ResponseEntity<RiskProfileResponse> updateRisk(
            Authentication authentication,
            @PathVariable UUID accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateAccountRiskRequest request) {
        AccountProfileRecord current = master.get(tenantId(authentication), accountId).profile();
        etags.validateIfMatch(ifMatch, "account-master", accountId, current.version());
        AccountProfileRecord updated = master.updateProfile(
                tenantId(authentication), userId(authentication), accountId,
                new UpdateAccountProfileCommand(
                        null, null, null, null, null, null, null, null,
                        request.riskLevel(), request.riskFlags(), null, null,
                        request.mergeCandidate()),
                current.version());
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG,
                        etags.etag("account-master", accountId, updated.version()))
                .body(toRisk(updated));
    }

    @RequireCapability("CRM.ACCOUNT.RELATIONSHIP.READ")
    @GetMapping("/{accountId}/relationships")
    public List<AccountRelationshipResponse> listRelationships(
            Authentication authentication, @PathVariable UUID accountId) {
        return master.get(tenantId(authentication), accountId).relationships()
                .stream().map(AccountMasterController::toRelationship).toList();
    }

    @RequireCapability("CRM.ACCOUNT.RELATIONSHIP.WRITE")
    @PostMapping("/{accountId}/relationships")
    public ResponseEntity<AccountRelationshipResponse> createRelationship(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateAccountRelationshipRequest request) {
        AccountRelationshipRecord created = master.createRelationship(
                tenantId(authentication), userId(authentication),
                new CreateAccountRelationshipCommand(
                        accountId, request.targetAccountId(), request.relationshipType(),
                        request.effectiveFrom(), request.effectiveTo(), request.description()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.ETAG,
                        etags.etag("account-relationship", created.id(), created.version()))
                .body(toRelationship(created));
    }

    @RequireCapability("CRM.ACCOUNT.RELATIONSHIP.WRITE")
    @PatchMapping("/{accountId}/relationships/{relationshipId}/end")
    public ResponseEntity<AccountRelationshipResponse> endRelationship(
            Authentication authentication,
            @PathVariable UUID accountId,
            @PathVariable UUID relationshipId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) EndAccountRelationshipRequest request) {
        AccountRelationshipRecord current = master.get(tenantId(authentication), accountId)
                .relationships().stream().filter(item -> relationshipId.equals(item.id())).findFirst()
                .orElseThrow(() -> new CrmContractException(CrmErrorCode.RESOURCE_NOT_FOUND));
        etags.validateIfMatch(ifMatch, "account-relationship", relationshipId, current.version());
        LocalDate effectiveTo = request == null ? null : request.effectiveTo();
        AccountRelationshipRecord ended = master.endRelationship(
                tenantId(authentication), userId(authentication), accountId,
                relationshipId, current.version(), effectiveTo);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG,
                        etags.etag("account-relationship", ended.id(), ended.version()))
                .body(toRelationship(ended));
    }

    @RequireCapability("CRM.ACCOUNT.IDENTIFIER.READ")
    @GetMapping("/{accountId}/external-identifiers")
    public List<ExternalIdentifierResponse> listExternalIdentifiers(
            Authentication authentication, @PathVariable UUID accountId) {
        return master.get(tenantId(authentication), accountId).externalIdentifiers()
                .stream().map(AccountMasterController::toIdentifier).toList();
    }

    @RequireCapability("CRM.ACCOUNT.IDENTIFIER.WRITE")
    @PostMapping("/{accountId}/external-identifiers")
    public ResponseEntity<ExternalIdentifierResponse> createExternalIdentifier(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateExternalIdentifierRequest request) {
        ExternalIdentifierRecord created = master.addExternalIdentifier(
                tenantId(authentication), userId(authentication), accountId,
                new CreateExternalIdentifierCommand(
                        request.provider(), request.systemScope(), request.externalId(), request.label()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toIdentifier(created));
    }

    @RequireCapability("CRM.ACCOUNT.IDENTIFIER.WRITE")
    @DeleteMapping("/{accountId}/external-identifiers/{identifierId}")
    public ResponseEntity<Void> removeExternalIdentifier(
            Authentication authentication,
            @PathVariable UUID accountId,
            @PathVariable UUID identifierId) {
        master.removeExternalIdentifier(
                tenantId(authentication), userId(authentication), accountId, identifierId);
        return ResponseEntity.noContent().build();
    }

    @RequireCapability("CRM.ACCOUNT.HISTORY.READ")
    @GetMapping("/{accountId}/history")
    public AccountHistoryResponse getHistory(
            Authentication authentication, @PathVariable UUID accountId) {
        AccountMasterView view = master.get(tenantId(authentication), accountId);
        return new AccountHistoryResponse(
                view.statusHistory().stream().map(AccountMasterController::toStatusHistory).toList(),
                view.ownershipHistory().stream().map(AccountMasterController::toOwnershipHistory).toList());
    }

    @RequireCapability("CRM.ACCOUNT.MASTER.READ")
    @GetMapping("/{accountId}/projections")
    public List<ProjectionResponse> getProjections(
            Authentication authentication, @PathVariable UUID accountId) {
        return master.get(tenantId(authentication), accountId).projections()
                .stream().map(this::toProjection).toList();
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/{accountId}/reactivate")
    public ResponseEntity<AccountMasterOverviewResponse> reactivate(
            Authentication authentication,
            @PathVariable UUID accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AccountRecord current = accounts.getById(tenantId(authentication), accountId);
        etags.validateIfMatch(ifMatch, "account", accountId, current.version());
        accounts.restore(
                tenantId(authentication), userId(authentication), accountId, current.version());
        AccountMasterView view = master.get(tenantId(authentication), accountId);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG,
                        etags.etag("account", accountId, view.account().version()))
                .body(toOverview(view));
    }

    private AccountMasterOverviewResponse toOverview(AccountMasterView view) {
        return new AccountMasterOverviewResponse(
                view.account().id(), view.account().version(), view.account().displayName(),
                view.account().accountType(), view.account().lifecycleStatus(),
                view.account().ownerUserId(), toProfile(view.profile()),
                view.projections().stream().map(this::toProjection).toList());
    }

    private static AccountProfileResponse toProfile(AccountProfileRecord record) {
        return new AccountProfileResponse(
                record.accountId(), record.version(), record.legalName(), record.tradeName(),
                record.registrationNumber(), record.taxRegistrationNumber(), record.industry(),
                record.organizationSize(), record.websiteUrl(), record.customerTier(),
                record.classificationId(), record.segmentId(), record.createdAt(), record.updatedAt());
    }

    private static RiskProfileResponse toRisk(AccountProfileRecord record) {
        return new RiskProfileResponse(
                record.accountId(), record.version(), record.riskLevel(),
                record.riskFlags(), record.mergeCandidate(), record.updatedAt());
    }

    private static AccountRelationshipResponse toRelationship(AccountRelationshipRecord record) {
        return new AccountRelationshipResponse(
                record.id(), record.version(), record.sourceAccountId(), record.targetAccountId(),
                record.relationshipType(), record.status(), record.effectiveFrom(), record.effectiveTo(),
                record.description(), record.createdAt(), record.updatedAt());
    }

    private static ExternalIdentifierResponse toIdentifier(ExternalIdentifierRecord record) {
        return new ExternalIdentifierResponse(
                record.id(), record.accountId(), record.provider(), record.systemScope(),
                record.externalId(), record.label(), record.active(), record.createdAt(), record.updatedAt());
    }

    private static StatusHistoryResponse toStatusHistory(StatusHistoryRecord record) {
        return new StatusHistoryResponse(
                record.id(), record.fromStatus(), record.toStatus(), record.reason(),
                record.changedBy(), record.changedAt());
    }

    private static OwnershipHistoryResponse toOwnershipHistory(OwnershipHistoryRecord record) {
        return new OwnershipHistoryResponse(
                record.id(), record.fromOwnerUserId(), record.toOwnerUserId(), record.reason(),
                record.changedBy(), record.changedAt());
    }

    private ProjectionResponse toProjection(ProjectionSnapshotRecord record) {
        return new ProjectionResponse(
                record.id(), record.projectionType(), record.sourceSystem(),
                record.connectionStatus(), parsePayload(record.payloadJson()),
                record.sourceUpdatedAt(), record.syncedAt());
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return null;
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException exception) {
            throw new CrmContractException(
                    CrmErrorCode.INTERNAL_ERROR, "Account projection payload is invalid", null, exception);
        }
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
    }
}
