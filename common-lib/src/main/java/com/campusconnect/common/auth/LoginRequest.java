package com.campusconnect.common.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login credentials (Story 2.3). {@code collegeCode} (the tenant slug) is required because email is
 * unique per-tenant and there is no JWT yet to carry the tenant.
 */
public record LoginRequest(
        @NotBlank @Size(max = 64) String collegeCode,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {
}
