package com.campusconnect.common.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Set a new password using an emailed OTP (Story 2.4). */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 64) String collegeCode,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "otp must be 6 digits") String otp,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
