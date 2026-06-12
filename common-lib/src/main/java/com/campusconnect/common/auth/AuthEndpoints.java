package com.campusconnect.common.auth;

import com.campusconnect.common.exception.RateLimitException;
import com.campusconnect.common.ratelimit.RateLimiter;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the HTTP responses for the per-portal auth endpoints (Story 2.3): runs the shared
 * {@link AuthenticationService} and attaches/clears the refresh cookie. Keeps the cookie + envelope
 * wiring in one place so each portal controller is a thin three-liner differing only by role + path.
 *
 * <p>Story 2.5: login is rate-limited per client IP ({@code app.ratelimit.login.*}, default 5/15min);
 * the {@link RateLimitException} (429) is raised before authentication, so every attempt counts.
 */
@Component
public class AuthEndpoints {

    private final AuthenticationService authenticationService;
    private final AuthCookies authCookies;
    private final RateLimiter rateLimiter;
    private final int loginLimit;
    private final Duration loginWindow;

    public AuthEndpoints(AuthenticationService authenticationService, AuthCookies authCookies,
                         RateLimiter rateLimiter,
                         @Value("${app.ratelimit.login.limit:5}") int loginLimit,
                         @Value("${app.ratelimit.login.window:PT15M}") Duration loginWindow) {
        this.authenticationService = authenticationService;
        this.authCookies = authCookies;
        this.rateLimiter = rateLimiter;
        this.loginLimit = loginLimit;
        this.loginWindow = loginWindow;
    }

    /** Authenticate for {@code expectedRole}; on success return the body + set the refresh cookie at {@code cookiePath}. */
    public ResponseEntity<ApiResponse<LoginResponse>> login(LoginRequest request, Role expectedRole,
                                                            String cookiePath, String clientIp) {
        rateLimiter.check("login:" + clientIp, loginLimit, loginWindow);
        AuthResult result = authenticationService.login(
                request.collegeCode(), request.email(), request.password(), expectedRole);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.refreshCookie(result.refreshTokenValue(), cookiePath).toString())
                .body(ApiResponse.ok(LoginResponse.from(result)));
    }

    /** Rotate the session: new access token in the body, new refresh token in the cookie. */
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(String refreshCookie, String cookiePath) {
        AuthResult result = authenticationService.refresh(refreshCookie);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.refreshCookie(result.refreshTokenValue(), cookiePath).toString())
                .body(ApiResponse.ok(RefreshResponse.from(result)));
    }

    /** End the session: invalidate the refresh token and clear the cookie. Idempotent. */
    public ResponseEntity<ApiResponse<Boolean>> logout(String refreshCookie, String cookiePath) {
        authenticationService.logout(refreshCookie);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.clearedCookie(cookiePath).toString())
                .body(ApiResponse.ok(Boolean.TRUE, "Logged out."));
    }
}
