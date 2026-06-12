package com.campusconnect.recruiter.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public recruiter self-registration (Story 2.2, FR-4). Credentials + company details in one call;
 * {@code collegeCode} is the tenant slug (resolved at the edge — no JWT yet). Only {@code companyName}
 * is required among the company fields; the College Admin vets these before approval.
 */
public record RegisterRecruiterRequest(
        @NotBlank @Size(max = 64) String collegeCode,
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt consumes at most 72 bytes — cap here so the limit is honest, not silently truncated.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 200) String companyName,
        @Size(max = 200) String companyWebsite,
        @Size(max = 100) String industry,
        @Size(max = 2000) String companyDescription,
        @Size(max = 100) String recruiterDesignation,
        @Size(max = 30) String contactPhone) {
}
