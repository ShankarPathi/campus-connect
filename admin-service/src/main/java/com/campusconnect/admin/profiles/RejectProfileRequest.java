package com.campusconnect.admin.profiles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A College Admin's reason for rejecting a student profile (Story 3.3). Shown to the student and emailed. */
public record RejectProfileRequest(@NotBlank @Size(max = 500) String reason) {
}
