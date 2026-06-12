package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A single-use, expiring password-reset OTP (Story 2.4). Keyed by {@code userId} (a 6-digit code is not
 * globally unique, so there is no unique index on {@code otp}). {@code attempts} counts wrong guesses so
 * the code can be locked after a few tries — the brute-force defense until request rate-limiting (2.5).
 * The {@code expireAfter="0s"} index removes the document once {@link #expiresAt} passes.
 */
@Document("passwordResetOtps")
public class PasswordResetOtp extends BaseDocument {

    @Indexed
    private String userId;

    private String tenantId;

    private String otp;

    private int attempts;

    @Indexed(name = "ttl_expiresAt", expireAfter = "0s")
    private Instant expiresAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
