package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Metadata for a student's uploaded resume (Story 3.2, FR-8); the file itself lives in MinIO under
 * {@code s3Key}. Tenant-scoped and owned by one student ({@code userId} = the authenticated principal).
 * A student has at most one {@code isActive} resume; prior versions are retained and immutable so that
 * an apply-time snapshot (Epic 5) can reference an exact, unchanging {@code s3Key}.
 *
 * <p>{@code s3Key} is internal — it is never serialized to a client; access is only via short-lived
 * pre-signed URLs.
 */
@Document("resumes")
@CompoundIndex(name = "idx_tenant_user_active", def = "{'tenantId': 1, 'userId': 1, 'isActive': 1}")
public class Resume extends TenantAwareDocument {

    private String userId;
    private String s3Key;
    private String originalName;
    private String mimeType;
    private int version;
    private boolean isActive;
    private long sizeBytes;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
