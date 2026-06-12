package com.campusconnect.student.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to bootstrap a College Admin (TPO) for a tenant (FR-2). */
public record CreateCollegeAdminRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt consumes at most 72 bytes — cap here so the limit is honest, not silently truncated.
        @NotBlank @Size(min = 8, max = 72) String password) {
}
