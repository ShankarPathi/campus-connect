package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A single-use email-verification token (Story 2.1). Lives in its own collection rather than on the
 * {@link User} so the link can auto-expire: the {@code expireAfter = "0s"} index removes a document
 * once {@link #expiresAt} passes (TTL monitor runs ~every 60s — so callers also check expiry in code).
 *
 * <p>The same expiring-token mechanism is reused for password-reset OTPs in Story 2.4.
 */
@Document("emailVerifyTokens")
public class EmailVerifyToken extends BaseDocument {

    private String userId;

    private String tenantId;

    @Indexed(unique = true)
    private String token;

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
