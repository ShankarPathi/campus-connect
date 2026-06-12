package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A long-lived refresh token (Story 2.3) backing a login session. Opaque random value delivered to the
 * client in an HttpOnly cookie and stored here so it can be rotated/invalidated. The
 * {@code expireAfter="0s"} index removes the document once {@link #expiresAt} passes (callers also
 * check expiry in code, since the TTL monitor lags ~60s).
 */
@Document("refreshTokens")
public class RefreshToken extends BaseDocument {

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
