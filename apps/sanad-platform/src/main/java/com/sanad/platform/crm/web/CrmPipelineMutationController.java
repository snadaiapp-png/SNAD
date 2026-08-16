package com.sanad.platform.crm.web;

import com.sanad.platform.crm.dto.CrmDtos.PipelineResponse;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Governed CRM v2 pipeline mutations kept separate from the read contract surface. */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmPipelineMutationController {

    private final CrmService crm;
    private final CrmDtoMapper mapper;
    private final CrmIdempotencyHttpSupport idempotency;

    public CrmPipelineMutationController(
            CrmService crm,
            CrmDtoMapper mapper,
            CrmIdempotencyHttpSupport idempotency) {
        this.crm = crm;
        this.mapper = mapper;
        this.idempotency = idempotency;
    }

    @RequireCapability("CRM.ADMIN")
    @PostMapping("/pipelines")
    public ResponseEntity<SingleResponse<PipelineResponse>> createPipeline(
            Authentication auth,
            @Valid @RequestBody CreatePipelineRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(auth, "POST:/api/v2/crm/pipelines", key, body, request);
        if (guard.isReplay()) {
            return idempotency.replay(guard, PipelineResponse.class);
        }
        try {
            Map<String, Object> created = crm.createPipeline(auth, body);
            PipelineResponse response = mapper.toPipelineResponse(created, List.of());
            return idempotency.complete(
                    guard, response, "pipeline", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }
}
