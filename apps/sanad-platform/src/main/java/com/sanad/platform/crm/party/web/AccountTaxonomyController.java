package com.sanad.platform.crm.party.web;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.application.AccountMasterUseCases;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.CreateTaxonomyCommand;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.TaxonomyRecord;
import com.sanad.platform.crm.party.web.AccountMasterModels.CreateTaxonomyRequest;
import com.sanad.platform.crm.party.web.AccountMasterModels.TaxonomyResponse;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/crm/account-taxonomies")
public class AccountTaxonomyController {
    private final AccountMasterUseCases master;

    public AccountTaxonomyController(AccountMasterUseCases master) {
        this.master = master;
    }

    @RequireCapability("CRM.ACCOUNT.MASTER.READ")
    @GetMapping
    public List<TaxonomyResponse> list(
            Authentication authentication,
            @RequestParam String type) {
        return master.listTaxonomies(tenantId(authentication), type)
                .stream().map(AccountTaxonomyController::toResponse).toList();
    }

    @RequireCapability("CRM.ACCOUNT.MASTER.WRITE")
    @PostMapping
    public ResponseEntity<TaxonomyResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateTaxonomyRequest request) {
        TaxonomyRecord created = master.createTaxonomy(
                tenantId(authentication), userId(authentication),
                new CreateTaxonomyCommand(
                        request.taxonomyType(), request.code(), request.nameAr(),
                        request.nameEn(), request.parentId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    private static TaxonomyResponse toResponse(TaxonomyRecord record) {
        return new TaxonomyResponse(
                record.id(), record.version(), record.taxonomyType(), record.code(),
                record.nameAr(), record.nameEn(), record.parentId(), record.active(),
                record.createdAt(), record.updatedAt());
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
