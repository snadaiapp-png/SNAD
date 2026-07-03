package com.sanad.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/refresh}.
 *
 * <p>The refresh token is an opaque string (not a JWT) issued during
 * login or a previous refresh. It is single-use — presenting it again
 * after it has been used triggers replay protection (all tokens for
 * that user are revoked).</p>
 */
public class RefreshRequest {

    @NotBlank
    private String refreshToken;

    public RefreshRequest() {
    }

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
