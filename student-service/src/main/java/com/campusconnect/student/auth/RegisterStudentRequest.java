package com.campusconnect.student.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public student self-registration request (Story 2.1, FR-4). {@code collegeCode} is the tenant
 * {@code slug} — the request resolves its tenant at the edge (no JWT yet).
 */
public record RegisterStudentRequest(
        @NotBlank @Size(max = 64) String collegeCode,
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt consumes at most 72 bytes — cap here so the limit is honest, not silently truncated.
        @NotBlank @Size(min = 8, max = 72) String password) {
}
