package com.campusconnect.admin.recruiters;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A College Admin's reason for rejecting a recruiter (Story 2.2). The reason is emailed to the recruiter. */
public record RejectRecruiterRequest(@NotBlank @Size(max = 500) String reason) {
}
