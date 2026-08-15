package com.sanad.platform.website.api;

import com.sanad.platform.website.api.WebsiteDtos.*;
import com.sanad.platform.website.application.WebsitePublicResolutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public Website API (v20260816.3).
 *
 * Hostname-driven public resolution — NO @RequireCapability.
 * Resolves by Host header → active domain → active website → published page.
 * Only PUBLISHED content is exposed publicly.
 */
@RestController
@RequestMapping("/api/v1/public/websites")
public class PublicWebsiteController {

    private final WebsitePublicResolutionService resolutionService;

    public PublicWebsiteController(WebsitePublicResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    @GetMapping("/resolve")
    public ResponseEntity<PublicWebsiteResponse> resolveWebsite(
            @RequestHeader(value = "Host", required = false) String host) {
        if (host == null || host.isBlank()) return ResponseEntity.badRequest().build();
        var website = resolutionService.resolveWebsite(host);
        if (website == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(website);
    }

    @GetMapping("/resolve/page/{pageSlug}")
    public ResponseEntity<PublicPageResponse> resolvePage(
            @RequestHeader(value = "Host", required = false) String host,
            @PathVariable String pageSlug) {
        if (host == null || host.isBlank()) return ResponseEntity.badRequest().build();
        var page = resolutionService.resolvePage(host, pageSlug);
        if (page == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(page);
    }
}
