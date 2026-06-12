package com.campusconnect.student.auth;

import com.campusconnect.common.auth.AuthEndpoints;
import com.campusconnect.common.auth.LoginRequest;
import com.campusconnect.common.auth.LoginResponse;
import com.campusconnect.common.auth.RefreshResponse;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public student auth endpoints (Story 2.1 register/verify; Story 2.3 login/refresh/logout). The base
 * path {@code /api/student/auth/**} is admitted by {@code SecurityConfig}'s permit-all matcher for
 * per-service auth paths — no token required. Login/refresh/logout delegate to the shared
 * {@code AuthEndpoints}; this portal authenticates the STUDENT role only.
 */
@RestController
@RequestMapping("/api/student/auth")
public class StudentAuthController {

    private static final String COOKIE_PATH = "/api/student/auth";

    private final StudentRegistrationService registrationService;
    private final AuthEndpoints authEndpoints;

    public StudentAuthController(StudentRegistrationService registrationService, AuthEndpoints authEndpoints) {
        this.registrationService = registrationService;
        this.authEndpoints = authEndpoints;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return authEndpoints.login(request, Role.STUDENT, COOKIE_PATH);
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

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<StudentRegistrationResponse>> register(
            @Valid @RequestBody RegisterStudentRequest request) {
        StudentRegistrationResponse created = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                created, "Registration successful — check your email to verify your account."));
    }

    /**
     * GET so the emailed link is directly clickable (there is no SPA yet — Epic 9 may move this to a
     * frontend route that POSTs the token). Effectively idempotent: a consumed token returns 400.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Boolean>> verifyEmail(@RequestParam("token") String token) {
        registrationService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE, "Email verified — you may now log in."));
    }
}
