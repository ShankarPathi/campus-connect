package com.campusconnect.common.auth;

import com.campusconnect.common.security.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the refresh-token cookie (Story 2.3). HttpOnly so JS can't read it; {@code Path} is scoped to
 * the portal's auth endpoints so the browser only sends it to refresh/logout; {@code SameSite=Lax} and
 * the {@code Secure} flag are dev-friendly defaults — final SameSite/Secure/CORS tuning lands with the
 * SPA (Epic 9) and HTTPS (Epic 10), where {@code app.auth.cookie-secure} is set true.
 */
@Component
public class AuthCookies {

    public static final String REFRESH_COOKIE = "refreshToken";

    private final JwtProperties jwtProperties;
    private final boolean secure;

    public AuthCookies(JwtProperties jwtProperties,
                       @Value("${app.auth.cookie-secure:false}") boolean secure) {
        this.jwtProperties = jwtProperties;
        this.secure = secure;
    }

    /** A refresh cookie carrying {@code value}, scoped to {@code path} (e.g. {@code /api/student/auth}). */
    public ResponseCookie refreshCookie(String value, String path) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .sameSite("Lax")
                .maxAge(Duration.ofDays(jwtProperties.refreshTokenDays()))
                .build();
    }

    /** A cleared (immediately-expiring) refresh cookie for logout, scoped to the same {@code path}. */
    public ResponseCookie clearedCookie(String path) {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .sameSite("Lax")
                .maxAge(0)
                .build();
    }
}
