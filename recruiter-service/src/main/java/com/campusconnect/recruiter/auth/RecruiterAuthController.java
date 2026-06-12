package com.campusconnect.recruiter.auth;

import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public recruiter auth endpoints (Story 2.2, FR-4). The base path {@code /api/recruiter/auth} is
 * admitted by SecurityConfig's permit-all matcher for per-service auth paths — no token required.
 */
@RestController
@RequestMapping("/api/recruiter/auth")
public class RecruiterAuthController {

    private final RecruiterRegistrationService registrationService;

    public RecruiterAuthController(RecruiterRegistrationService registrationService) {
        this.registrationService = registrationService;
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
