package com.campusconnect.admin.auth;

import com.campusconnect.common.auth.AuthEndpoints;
import com.campusconnect.common.auth.LoginRequest;
import com.campusconnect.common.auth.LoginResponse;
import com.campusconnect.common.auth.RefreshResponse;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public college-admin auth endpoints (Story 2.3). College admins are bootstrapped by the platform
 * admin (Story 1.6) — no self-register/verify — so this controller is login/refresh/logout only.
 * The {@code /api/admin/auth} base path is public via SecurityConfig's per-service auth matcher; the
 * authenticated {@code /api/admin/recruiters} approval endpoints (Story 2.2) are unaffected. Login
 * authenticates the COLLEGE_ADMIN role only.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private static final String COOKIE_PATH = "/api/admin/auth";

    private final AuthEndpoints authEndpoints;

    public AdminAuthController(AuthEndpoints authEndpoints) {
        this.authEndpoints = authEndpoints;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return authEndpoints.login(request, Role.COLLEGE_ADMIN, COOKIE_PATH);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        return authEndpoints.refresh(refreshToken, COOKIE_PATH);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Boolean>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        return authEndpoints.logout(refreshToken, COOKIE_PATH);
    }
}
