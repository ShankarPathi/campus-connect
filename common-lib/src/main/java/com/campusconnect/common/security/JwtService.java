package com.campusconnect.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Issues and validates HS256 access tokens. Stateless — no DB. The {@code tenantId} claim is omitted
 * for {@code PLATFORM_ADMIN} (no tenant).
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "security.jwt.secret must be at least 32 bytes (256-bit) for HS256 signing");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /** Builds a signed access token with {@code sub=userId}, {@code role}, and (unless null) {@code tenantId}. */
    public String issueAccessToken(String userId, Role role, String tenantId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.minutesFor(role), ChronoUnit.MINUTES);
        var builder = Jwts.builder()
                .subject(userId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));
        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    /**
     * Validates signature + expiry and extracts the principal. Throws {@link InvalidTokenException}
     * for any malformed / tampered / wrong-signed / expired / unknown-role token.
     */
    public AuthToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String roleName = claims.get("role", String.class);
            if (roleName == null) {
                throw new InvalidTokenException("Token is missing the role claim", null);
            }
            Role role = Role.valueOf(roleName);
            String tenantId = claims.get("tenantId", String.class);
            return new AuthToken(claims.getSubject(), role, tenantId);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }
    }

    /** The validated principal carried by a token. */
    public record AuthToken(String userId, Role role, String tenantId) {
    }
}
