package com.campusconnect.student.resume;

import com.campusconnect.common.domain.Resume;

import java.time.Instant;

/**
 * The student's view of their active resume (Story 3.2) plus a short-lived pre-signed preview URL.
 * Never exposes the internal {@code s3Key} or bucket. When the student has no resume, {@code hasResume}
 * is false and the file fields / URL are null.
 */
public record ResumeResponse(
        boolean hasResume,
        String originalName,
        String mimeType,
        Integer version,
        Long sizeBytes,
        Instant uploadedAt,
        String previewUrl,
        Integer previewExpiresInSeconds) {

    /** Empty view for a student who has not uploaded yet. */
    public static ResumeResponse none() {
        return new ResumeResponse(false, null, null, null, null, null, null, null);
    }

    public static ResumeResponse of(Resume r, String previewUrl, int previewTtlSeconds) {
        // uploadedAt is the creation time — updatedAt is bumped when a version is later deactivated.
        return new ResumeResponse(true, r.getOriginalName(), r.getMimeType(), r.getVersion(),
                r.getSizeBytes(), r.getCreatedAt(), previewUrl, previewTtlSeconds);
    }
}
