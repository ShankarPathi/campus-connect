package com.campusconnect.common.auth;

import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds the HTTP responses for the per-portal auth endpoints (Story 2.3): runs the shared
 * {@link AuthenticationService} and attaches/clears the refresh cookie. Keeps the cookie + envelope
 * wiring in one place so each portal controller is a thin three-liner differing only by role + path.
 */
@Component
public class AuthEndpoints {

    private final AuthenticationService authenticationService;
    private final AuthCookies authCookies;

    public AuthEndpoints(AuthenticationService authenticationService, AuthCookies authCookies) {
        this.authenticationService = authenticationService;
        this.authCookies = authCookies;
    }

    /** Authenticate for {@code expectedRole}; on success return the body + set the refresh cookie at {@code cookiePath}. */
    public ResponseEntity<ApiResponse<LoginResponse>> login(LoginRequest request, Role expectedRole, String cookiePath) {
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
