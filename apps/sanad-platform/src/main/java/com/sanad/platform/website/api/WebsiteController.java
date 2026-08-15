package com.sanad.platform.website.api;

import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.website.api.WebsiteDtos.*;
import com.sanad.platform.website.application.WebsiteDomainService;
import com.sanad.platform.website.application.WebsitePageService;
import com.sanad.platform.website.application.WebsitePublicResolutionService;
import com.sanad.platform.website.application.WebsiteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;

/**
 * Website Platform API (v20260816.3).
 *
 * Management endpoints under /api/v1/websites — all @RequireCapability.
 * Public endpoint under /api/v1/public/websites — hostname-driven.
 */
@RestController
@RequestMapping("/api/v1/websites")
public class WebsiteController {

    private final WebsiteService websiteService;
    private final WebsitePageService pageService;
    private final WebsiteDomainService domainService;
    private final WebsitePublicResolutionService publicResolutionService;

    public WebsiteController(WebsiteService websiteService, WebsitePageService pageService,
                            WebsiteDomainService domainService,
                            WebsitePublicResolutionService publicResolutionService) {
        this.websiteService = websiteService;
        this.pageService = pageService;
        this.domainService = domainService;
        this.publicResolutionService = publicResolutionService;
    }

    // ===== Websites =====

    @PostMapping
    @RequireCapability("WEBSITE.WRITE")
    public ResponseEntity<WebsiteResponse> createWebsite(Authentication auth, @Valid @RequestBody CreateWebsiteRequest request) {
        return ResponseEntity.ok(websiteService.create(tenantId(auth), request, auth));
    }

    @GetMapping
    @RequireCapability("WEBSITE.VIEW")
    public ResponseEntity<List<WebsiteResponse>> listWebsites(Authentication auth) {
        return ResponseEntity.ok(websiteService.list(tenantId(auth)));
    }

    @GetMapping("/summary")
    @RequireCapability("WEBSITE.VIEW")
    public ResponseEntity<WebsiteSummary> summarize(Authentication auth) {
        return ResponseEntity.ok(websiteService.summarize(tenantId(auth)));
    }

    @GetMapping("/{websiteId}")
    @RequireCapability("WEBSITE.VIEW")
    public ResponseEntity<WebsiteResponse> getWebsite(Authentication auth, @PathVariable UUID websiteId) {
        return ResponseEntity.ok(websiteService.get(tenantId(auth), websiteId));
    }

    @PutMapping("/{websiteId}")
    @RequireCapability("WEBSITE.WRITE")
    public ResponseEntity<WebsiteResponse> updateWebsite(Authentication auth, @PathVariable UUID websiteId,
                                                         @Valid @RequestBody UpdateWebsiteRequest request) {
        return ResponseEntity.ok(websiteService.update(tenantId(auth), websiteId, request, auth));
    }

    @PostMapping("/{websiteId}/activate")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<WebsiteResponse> activateWebsite(Authentication auth, @PathVariable UUID websiteId) {
        return ResponseEntity.ok(websiteService.activate(tenantId(auth), websiteId, auth));
    }

    @PostMapping("/{websiteId}/suspend")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<WebsiteResponse> suspendWebsite(Authentication auth, @PathVariable UUID websiteId) {
        return ResponseEntity.ok(websiteService.suspend(tenantId(auth), websiteId, auth));
    }

    @PostMapping("/{websiteId}/archive")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<WebsiteResponse> archiveWebsite(Authentication auth, @PathVariable UUID websiteId) {
        return ResponseEntity.ok(websiteService.archive(tenantId(auth), websiteId, auth));
    }

    @PostMapping("/{websiteId}/set-primary")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<WebsiteResponse> setPrimaryWebsite(Authentication auth, @PathVariable UUID websiteId) {
        return ResponseEntity.ok(websiteService.setPrimary(tenantId(auth), websiteId, auth));
    }

    // ===== Pages =====

    @GetMapping("/{websiteId}/pages")
    @RequireCapability("WEBSITE.VIEW")
    public ResponseEntity<List<PageResponse>> listPages(Authentication auth, @PathVariable UUID websiteId) {
        return ResponseEntity.ok(pageService.list(tenantId(auth), websiteId));
    }

    @PostMapping("/{websiteId}/pages")
    @RequireCapability("WEBSITE.WRITE")
    public ResponseEntity<PageResponse> createPage(Authentication auth, @PathVariable UUID websiteId,
                                                   @Valid @RequestBody CreatePageRequest request) {
        return ResponseEntity.ok(pageService.create(tenantId(auth), websiteId, request, auth));
    }

    @GetMapping("/{websiteId}/pages/{pageId}")
    @RequireCapability("WEBSITE.VIEW")
    public ResponseEntity<PageResponse> getPage(Authentication auth, @PathVariable UUID websiteId,
                                                 @PathVariable UUID pageId) {
        return ResponseEntity.ok(pageService.get(tenantId(auth), websiteId, pageId));
    }

    @PutMapping("/{websiteId}/pages/{pageId}")
    @RequireCapability("WEBSITE.WRITE")
    public ResponseEntity<PageResponse> updatePage(Authentication auth, @PathVariable UUID websiteId,
                                                    @PathVariable UUID pageId,
                                                    @Valid @RequestBody UpdatePageRequest request) {
        return ResponseEntity.ok(pageService.update(tenantId(auth), websiteId, pageId, request, auth));
    }

    @PostMapping("/{websiteId}/pages/{pageId}/publish")
    @RequireCapability("WEBSITE.PUBLISH")
    public ResponseEntity<PageResponse> publishPage(Authentication auth, @PathVariable UUID websiteId,
                                                     @PathVariable UUID pageId) {
        return ResponseEntity.ok(pageService.publish(tenantId(auth), websiteId, pageId, auth));
    }

    @PostMapping("/{websiteId}/pages/{pageId}/unpublish")
    @RequireCapability("WEBSITE.PUBLISH")
    public ResponseEntity<PageResponse> unpublishPage(Authentication auth, @PathVariable UUID websiteId,
                                                       @PathVariable UUID pageId) {
        return ResponseEntity.ok(pageService.unpublish(tenantId(auth), websiteId, pageId, auth));
    }

    @PostMapping("/{websiteId}/pages/{pageId}/archive")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<PageResponse> archivePage(Authentication auth, @PathVariable UUID websiteId,
                                                      @PathVariable UUID pageId) {
        return ResponseEntity.ok(pageService.archive(tenantId(auth), websiteId, pageId, auth));
    }

    // ===== Domains =====

    @GetMapping("/{websiteId}/domains")
    @RequireCapability("WEBSITE.VIEW")
    public ResponseEntity<List<DomainResponse>> listDomains(Authentication auth, @PathVariable UUID websiteId) {
        return ResponseEntity.ok(domainService.list(tenantId(auth), websiteId));
    }

    @PostMapping("/{websiteId}/domains")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<DomainResponse> registerDomain(Authentication auth, @PathVariable UUID websiteId,
                                                          @Valid @RequestBody CreateDomainRequest request) {
        return ResponseEntity.ok(domainService.registerCustomDomain(tenantId(auth), websiteId, request, auth));
    }

    @GetMapping("/{websiteId}/domains/{domainId}/verification-instructions")
    @RequireCapability("WEBSITE.VIEW")
    public ResponseEntity<DomainVerificationInstructions> getVerificationInstructions(
            Authentication auth, @PathVariable UUID websiteId, @PathVariable UUID domainId) {
        return ResponseEntity.ok(domainService.getVerificationInstructions(tenantId(auth), websiteId, domainId));
    }

    @PostMapping("/{websiteId}/domains/{domainId}/verify")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<DomainResponse> verifyDomain(Authentication auth, @PathVariable UUID websiteId,
                                                        @PathVariable UUID domainId,
                                                        @RequestBody VerifyDomainRequest request) {
        return ResponseEntity.ok(domainService.verifyDomain(tenantId(auth), websiteId, domainId, request, auth));
    }

    @PostMapping("/{websiteId}/domains/{domainId}/activate")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<DomainResponse> activateDomain(Authentication auth, @PathVariable UUID websiteId,
                                                          @PathVariable UUID domainId) {
        return ResponseEntity.ok(domainService.activate(tenantId(auth), websiteId, domainId, auth));
    }

    @PostMapping("/{websiteId}/domains/{domainId}/disable")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<DomainResponse> disableDomain(Authentication auth, @PathVariable UUID websiteId,
                                                         @PathVariable UUID domainId) {
        return ResponseEntity.ok(domainService.disable(tenantId(auth), websiteId, domainId, auth));
    }

    @PostMapping("/{websiteId}/domains/{domainId}/primary")
    @RequireCapability("WEBSITE.ADMIN")
    public ResponseEntity<DomainResponse> setPrimaryDomain(Authentication auth, @PathVariable UUID websiteId,
                                                            @PathVariable UUID domainId) {
        return ResponseEntity.ok(domainService.setPrimary(tenantId(auth), websiteId, domainId, auth));
    }
}
