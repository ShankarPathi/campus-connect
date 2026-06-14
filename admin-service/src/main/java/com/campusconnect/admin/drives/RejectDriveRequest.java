package com.campusconnect.admin.drives;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A College Admin's reason for rejecting a drive (Story 4.3). Stored on the drive and emailed to the recruiter. */
public record RejectDriveRequest(@NotBlank @Size(max = 500) String reason) {
}
