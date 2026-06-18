package com.campusconnect.admin.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.PasswordResetOtp;
import com.campusconnect.common.domain.RefreshToken;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** College-admin password reset + authenticated change (Story 2.4). */
@SpringBootTest
@Testcontainers
class AdminPasswordTest {

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
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired RecordingEmail email;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    String tenantId, userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), PasswordResetOtp.class);
        mongoTemplate.remove(new Query(), RefreshToken.class);
        email.clear();
        tenantId = seedTenant("vignan");
        userId = seedAdmin(tenantId, "tpo@vignan.edu");
    }

    @Test
    void reset_viaOtp_changesPassword() throws Exception {
        mockMvc.perform(post("/api/admin/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON).content(json("collegeCode", "vignan", "email", "tpo@vignan.edu")));
        String code = email.lastCode();

        mockMvc.perform(post("/api/admin/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("collegeCode", "vignan", "email", "tpo@vignan.edu", "otp", code, "newPassword", "brand-new-pw")))
                .andExpect(status().isOk());

        assertThat(passwordEncoder.matches("brand-new-pw",
                userRepository.findById(userId).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void change_withAdminToken_changesPassword() throws Exception {
        mockMvc.perform(post("/api/admin/account/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken(userId, Role.COLLEGE_ADMIN, tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", "old-password", "newPassword", "brand-new-pw")))
                .andExpect(status().isOk());

        assertThat(passwordEncoder.matches("brand-new-pw",
                userRepository.findById(userId).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void change_withoutToken_is401() throws Exception {
        mockMvc.perform(post("/api/admin/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", "old-password", "newPassword", "brand-new-pw")))
                .andExpect(status().isUnauthorized());
    }

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug); t.setSlug(slug); t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedAdmin(String tid, String emailAddr) {
        User u = new User();
        u.setTenantId(tid); u.setEmail(emailAddr.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode("old-password"));
        u.setRole(Role.COLLEGE_ADMIN); u.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.save(u).getId();
    }

    private String json(String... kv) throws Exception {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) map.put(kv[i], kv[i + 1]);
        return objectMapper.writeValueAsString(map);
    }
}
