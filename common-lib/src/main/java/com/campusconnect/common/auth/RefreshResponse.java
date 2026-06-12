package com.campusconnect.common.auth;

/** Refresh result body — a fresh access token (the rotated refresh token rides in the cookie). */
public record RefreshResponse(String accessToken, String tokenType, long expiresInSeconds) {

    public static RefreshResponse from(AuthResult result) {
        return new RefreshResponse(result.accessToken(), "Bearer", result.expiresInSeconds());
    }
}
