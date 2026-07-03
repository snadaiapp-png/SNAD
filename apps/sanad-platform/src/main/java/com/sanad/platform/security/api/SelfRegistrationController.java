package com.sanad.platform.security.api;

import com.sanad.platform.security.dto.SelfRegistrationRequest;
import com.sanad.platform.security.dto.SelfRegistrationResponse;
import com.sanad.platform.security.service.MobileSelfRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public workspace registration without accepting a password over this endpoint. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Secure account and session management.")
public class SelfRegistrationController {

    private final MobileSelfRegistrationService registrationService;

    public SelfRegistrationController(MobileSelfRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account with regional mobile contact and send a password setup link")
    public ResponseEntity<SelfRegistrationResponse> register(
            @Valid @RequestBody SelfRegistrationRequest request,
            HttpServletRequest httpRequest
    ) {
        SelfRegistrationResponse response = registrationService.register(
                request,
                httpRequest.getRemoteAddr(),
                httpRequest.getLocale().getLanguage());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }
}
