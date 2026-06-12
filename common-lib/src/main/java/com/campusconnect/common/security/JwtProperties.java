package com.campusconnect.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration, bound from {@code security.jwt.*}.
 *
 * @param secret                  HS256 signing key — MUST be &ge; 32 bytes (256-bit). Provided via
 *                                env in production; never hardcode a real secret.
 * @param accessTokenMinutes      access-token lifetime for non-admin roles (default 30).
 * @param adminAccessTokenMinutes access-token lifetime for admin roles (default 15, architecture §11).
 * @param refreshTokenDays        refresh-token (session) lifetime in days (default 7, Story 2.3).
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        Integer accessTokenMinutes,
        Integer adminAccessTokenMinutes,
        Integer refreshTokenDays) {

    public JwtProperties {
        if (accessTokenMinutes == null) {
            accessTokenMinutes = 30;
        }
        if (adminAccessTokenMinutes == null) {
            adminAccessTokenMinutes = 15;
        }
        if (refreshTokenDays == null) {
            refreshTokenDays = 7;
        }
    }

    public int minutesFor(Role role) {
        return role.isAdmin() ? adminAccessTokenMinutes : accessTokenMinutes;
    }
}
