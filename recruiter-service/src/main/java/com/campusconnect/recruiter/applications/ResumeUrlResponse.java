package com.campusconnect.recruiter.applications;

/**
 * A short-lived pre-signed URL to view an applicant's frozen résumé snapshot (Story 6.1, architecture §7).
 * Generated on demand and never stored; {@code expiresInSeconds} mirrors the 15-minute signing window so the
 * client knows when to re-request.
 */
public record ResumeUrlResponse(String url, long expiresInSeconds) {
}
