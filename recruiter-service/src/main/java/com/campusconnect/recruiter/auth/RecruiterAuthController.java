package com.campusconnect.recruiter.auth;

import com.campusconnect.common.auth.AuthEndpoints;
import com.campusconnect.common.auth.ForgotPasswordRequest;
import com.campusconnect.common.auth.LoginRequest;
import com.campusconnect.common.auth.LoginResponse;
import com.campusconnect.common.auth.PasswordService;
import com.campusconnect.common.auth.RefreshResponse;
import com.campusconnect.common.auth.ResetPasswordRequest;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ApiResponse;
import com.campusconnect.common.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
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
 * Public recruiter auth endpoints (Story 2.2 register/verify; Story 2.3 login/refresh/logout). The base
 * path {@code /api/recruiter/auth} is admitted by SecurityConfig's permit-all matcher for per-service
 * auth paths — no token required. Login authenticates the RECRUITER role only.
 */
@RestController
@RequestMapping("/api/recruiter/auth")
public class RecruiterAuthController {

    private static final String COOKIE_PATH = "/api/recruiter/auth";

    private final RecruiterRegistrationService registrationService;
    private final AuthEndpoints authEndpoints;
    private final PasswordService passwordService;

    public RecruiterAuthController(RecruiterRegistrationService registrationService,
                                   AuthEndpoints authEndpoints, PasswordService passwordService) {
        this.registrationService = registrationService;
        this.authEndpoints = authEndpoints;
        this.passwordService = passwordService;
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Boolean>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.requestReset(request.collegeCode(), request.email());
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE, "If an account exists, a reset code has been sent."));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Boolean>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request.collegeCode(), request.email(), request.otp(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE, "Password reset — please log in with your new password."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest) {
        return authEndpoints.login(request, Role.RECRUITER, COOKIE_PATH, ClientIp.from(httpRequest));
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
    public ResponseEntity<ApiResponse<RecruiterRegistrationResponse>> register(
            @Valid @RequestBody RegisterRecruiterRequest request) {
        RecruiterRegistrationResponse created = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                created, "Registration successful — verify your email, then await College Admin approval."));
    }

    /** GET so the emailed link is directly clickable (no SPA yet). Consumed token returns 400. */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Boolean>> verifyEmail(@RequestParam("token") String token) {
        registrationService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE,
                "Email verified — your account is now pending College Admin approval."));
    }
}
