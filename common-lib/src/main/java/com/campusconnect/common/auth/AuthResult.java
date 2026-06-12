package com.campusconnect.common.auth;

import com.campusconnect.common.security.Role;

/**
 * Everything a successful login/refresh produces — the controller turns this into a response body plus
 * the refresh cookie. The {@code refreshTokenValue} is the raw token to put in the HttpOnly cookie; it
 * never appears in a response body.
 */
public record AuthResult(
        String accessToken,
        long expiresInSeconds,
        Role role,
        String refreshTokenValue,
        String userId,
        String tenantId) {
}
