package com.campusconnect.common.auth;

import com.campusconnect.common.security.Role;

/**
 * Login result body. The access token is here (the SPA holds it in memory and attaches it as
 * {@code Authorization: Bearer}); the refresh token is delivered separately in an HttpOnly cookie.
 */
public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, Role role) {

    public static LoginResponse from(AuthResult result) {
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds(), result.role());
    }
}
