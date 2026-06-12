package com.campusconnect.common.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request a password-reset OTP (Story 2.4). College code resolves the tenant (email is per-tenant). */
public record ForgotPasswordRequest(
        @NotBlank @Size(max = 64) String collegeCode,
        @NotBlank @Email @Size(max = 254) String email) {
}
