package com.sanad.platform.integration.zid.dto;

import java.time.Instant;

/**
 * Response for POST /link/start. The state is a one-time CSRF-guard
 * value the SPA must keep until the callback returns; it is useless
 * without the merchant's consent at Zid.
 */
public record ZidLinkStartResponse(String authorizationUrl, String state, Instant expiresAt) {
}
