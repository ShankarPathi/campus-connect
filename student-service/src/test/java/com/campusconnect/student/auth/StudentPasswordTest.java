package com.campusconnect.student.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.PasswordResetOtp;
import com.campusconnect.common.domain.RefreshToken;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.repository.RefreshTokenRepository;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Student password reset (OTP) + authenticated change, including the session-kill (Story 2.4). */
@SpringBootTest
@Testcontainers
class StudentPasswordTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
        // Story 2.5: raise the rate limits far above any test so the shared RateLimiter never trips here.
        registry.add("app.ratelimit.login.limit", () -> "100000");
        registry.add("app.ratelimit.otp.limit", () -> "100000");
    }

    @TestConfiguration
    static class RecordingMailConfig {
        @Bean @Primary RecordingEmail recordingEmail() { return new RecordingEmail(); }
    }

    static class RecordingEmail implements EmailService {
        final List<String> bodies = new ArrayList<>();
        @Override public void sendVerificationEmail(String to, String link) { }
        @Override public void sendEmail(String to, String subject, String body) { bodies.add(body); }
        String lastCode() {
            Matcher m = Pattern.compile("code is: (\\d{6})").matcher(bodies.get(bodies.size() - 1));
            return m.find() ? m.group(1) : null;
        }
        void clear() { bodies.clear(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired RecordingEmail email;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    String tenantId;
    String userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), PasswordResetOtp.class);
        mongoTemplate.remove(new Query(), RefreshToken.class);
        email.clear();
        tenantId = seedTenant("vignan");
        userId = seedUser(tenantId, "s@v.edu", "old-password");
    }

    @Test
    void forgot_returns200_andEmailsCode() throws Exception {
        mockMvc.perform(post("/api/student/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("collegeCode", "vignan", "email", "s@v.edu")))
                .andExpect(status().isOk());
        assertThat(email.lastCode()).matches("\\d{6}");
    }

    @Test
    void forgot_unknownUser_returns200_noCode() throws Exception {
        mockMvc.perform(post("/api/student/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("collegeCode", "vignan", "email", "ghost@v.edu")))
                .andExpect(status().isOk());
        assertThat(email.bodies).isEmpty();
        assertThat(mongoTemplate.findAll(PasswordResetOtp.class)).isEmpty();
    }

    @Test
    void reset_happyPath_changesPassword_killsSessions() throws Exception {
        seedRefreshToken();
        mockMvc.perform(post("/api/student/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON).content(json("collegeCode", "vignan", "email", "s@v.edu")));
        String code = email.lastCode();

        mockMvc.perform(post("/api/student/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("collegeCode", "vignan", "email", "s@v.edu", "otp", code, "newPassword", "brand-new-pw")))
                .andExpect(status().isOk());

        User u = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches("brand-new-pw", u.getPasswordHash())).isTrue();
        assertThat(mongoTemplate.findAll(RefreshToken.class)).isEmpty(); // logged out everywhere
    }

    @Test
    void reset_wrongCode_is400() throws Exception {
        mockMvc.perform(post("/api/student/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON).content(json("collegeCode", "vignan", "email", "s@v.edu")));

        mockMvc.perform(post("/api/student/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("collegeCode", "vignan", "email", "s@v.edu", "otp", "000000", "newPassword", "brand-new-pw")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
    }

    @Test
    void reset_invalidBody_is400() throws Exception {
        mockMvc.perform(post("/api/student/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("collegeCode", "vignan", "email", "s@v.edu", "otp", "12", "newPassword", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void reset_invalidBody_doesNotConsumeOtp() throws Exception {
        // request a valid OTP first
        mockMvc.perform(post("/api/student/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON).content(json("collegeCode", "vignan", "email", "s@v.edu")));
        String code = email.lastCode();

        // a reset whose body fails validation (short password) is rejected before the service runs
        mockMvc.perform(post("/api/student/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("collegeCode", "vignan", "email", "s@v.edu", "otp", code, "newPassword", "short")))
                .andExpect(status().isBadRequest());

        // the OTP survived — a valid reset with the same code still works
        mockMvc.perform(post("/api/student/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("collegeCode", "vignan", "email", "s@v.edu", "otp", code, "newPassword", "brand-new-pw")))
                .andExpect(status().isOk());
    }

    @Test
    void change_shortNewPassword_is400() throws Exception {
        mockMvc.perform(post("/api/student/account/password")
                        .header(HttpHeaders.AUTHORIZATION, studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", "old-password", "newPassword", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void change_withToken_andCorrectCurrent_changesPassword_killsSessions() throws Exception {
        seedRefreshToken();
        mockMvc.perform(post("/api/student/account/password")
                        .header(HttpHeaders.AUTHORIZATION, studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", "old-password", "newPassword", "brand-new-pw")))
                .andExpect(status().isOk());

        User u = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches("brand-new-pw", u.getPasswordHash())).isTrue();
        assertThat(mongoTemplate.findAll(RefreshToken.class)).isEmpty();
    }

    @Test
    void change_wrongCurrent_is401() throws Exception {
        mockMvc.perform(post("/api/student/account/password")
                        .header(HttpHeaders.AUTHORIZATION, studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", "WRONG", "newPassword", "brand-new-pw")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void change_withoutToken_is401() throws Exception {
        mockMvc.perform(post("/api/student/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", "old-password", "newPassword", "brand-new-pw")))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──

    private String studentToken() {
        return "Bearer " + jwtService.issueAccessToken(userId, Role.STUDENT, tenantId);
    }

    private void seedRefreshToken() {
        RefreshToken rt = new RefreshToken();
        rt.setToken("sess-1");
        rt.setUserId(userId);
        rt.setTenantId(tenantId);
        rt.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofDays(7)));
        refreshTokenRepository.save(rt);
    }

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedUser(String tid, String emailAddr, String password) {
        User u = new User();
        u.setTenantId(tid);
        u.setEmail(emailAddr.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(Role.STUDENT);
        u.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.save(u).getId();
    }

    private String json(String... kv) throws Exception {
        var map = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) map.put(kv[i], kv[i + 1]);
        return objectMapper.writeValueAsString(map);
    }
}
