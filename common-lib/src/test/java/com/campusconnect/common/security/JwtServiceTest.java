package com.campusconnect.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-0123456789-0123456789-abcd"; // >= 32 bytes

    private JwtService service(int accessMinutes, int adminMinutes) {
        return new JwtService(new JwtProperties(SECRET, accessMinutes, adminMinutes));
    }

    @Test
    void issueAndParse_roundTripsClaims() {
        JwtService jwt = service(30, 15);
        String token = jwt.issueAccessToken("user-1", Role.STUDENT, "tenant-a");

        JwtService.AuthToken parsed = jwt.parse(token);

        assertThat(parsed.userId()).isEqualTo("user-1");
        assertThat(parsed.role()).isEqualTo(Role.STUDENT);
        assertThat(parsed.tenantId()).isEqualTo("tenant-a");
    }

    @Test
    void platformAdminToken_hasNullTenantId() {
        JwtService jwt = service(30, 15);
        String token = jwt.issueAccessToken("admin-1", Role.PLATFORM_ADMIN, null);

        JwtService.AuthToken parsed = jwt.parse(token);

        assertThat(parsed.role()).isEqualTo(Role.PLATFORM_ADMIN);
        assertThat(parsed.tenantId()).isNull();
    }

    @Test
    void expiredToken_isRejected() {
        JwtService jwt = service(-1, -1); // issues an already-expired token
        String token = jwt.issueAccessToken("user-1", Role.STUDENT, "tenant-a");

        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tamperedToken_isRejected() {
        JwtService jwt = service(30, 15);
        String token = jwt.issueAccessToken("user-1", Role.STUDENT, "tenant-a");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("a") ? "bb" : "aa");

        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void wrongSecret_isRejected() {
        String token = service(30, 15).issueAccessToken("user-1", Role.STUDENT, "tenant-a");
        JwtService other = new JwtService(
                new JwtProperties("different-secret-0123456789-0123456789-xyz", 30, 15));

        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shortSecret_failsFastAtConstruction() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 30, 15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void adminRolesGetShorterLifetimeThanNonAdmin() {
        JwtProperties props = new JwtProperties(SECRET, 30, 15);

        assertThat(props.minutesFor(Role.PLATFORM_ADMIN)).isEqualTo(15);
        assertThat(props.minutesFor(Role.COLLEGE_ADMIN)).isEqualTo(15);
        assertThat(props.minutesFor(Role.RECRUITER)).isEqualTo(30);
        assertThat(props.minutesFor(Role.STUDENT)).isEqualTo(30);

        // and at the token level: an admin token expires sooner than a student token issued together
        JwtService jwt = service(30, 15);
        Date adminExp = expiryOf(jwt.issueAccessToken("a", Role.COLLEGE_ADMIN, "t"));
        Date studentExp = expiryOf(jwt.issueAccessToken("s", Role.STUDENT, "t"));
        assertThat(adminExp).isBefore(studentExp);
    }

    @Test
    void tokenMissingRoleClaim_isRejectedAs401NotNpe() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String signedButRoleless = Jwts.builder()
                .subject("user-1")
                .claim("tenantId", "tenant-a")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> service(30, 15).parse(signedButRoleless))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void unsignedAlgNoneToken_isRejected() {
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url("{\"sub\":\"hacker\",\"role\":\"PLATFORM_ADMIN\"}");
        String unsigned = header + "." + payload + "."; // empty signature

        assertThatThrownBy(() -> service(30, 15).parse(unsigned))
                .isInstanceOf(InvalidTokenException.class);
    }

    private Date expiryOf(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getExpiration();
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
