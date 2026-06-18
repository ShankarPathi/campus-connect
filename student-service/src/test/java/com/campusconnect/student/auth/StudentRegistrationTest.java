package com.campusconnect.student.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.EmailVerifyToken;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of student registration + email verification (Story 2.1, FR-4): tenant resolution
 * by college code, per-tenant email uniqueness, suspended/unknown college handling, public access
 * (no JWT), the verification email + activation flow, and single-use tokens.
 */
@SpringBootTest
@Testcontainers
class StudentRegistrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    /** Recording {@link EmailService} — @Primary so it is injected in place of the real SMTP sender. */
    @TestConfiguration
    static class RecordingMailConfig {
        @Bean
        @Primary
        RecordingEmailService recordingEmailService() {
            return new RecordingEmailService();
        }
    }

    static class RecordingEmailService implements EmailService {
        final List<String> recipients = new CopyOnWriteArrayList<>();
        final List<String> links = new CopyOnWriteArrayList<>();
        volatile boolean failNext = false;

        @Override
        public void sendVerificationEmail(String toEmail, String verificationLink) {
            if (failNext) {
                throw new org.springframework.mail.MailSendException("simulated SMTP failure");
            }
            recipients.add(toEmail);
            links.add(verificationLink);
        }

        @Override
        public void sendEmail(String to, String subject, String body) {
            // unused by student registration; present to satisfy the interface
        }

        void clear() {
            recipients.clear();
            links.clear();
            failNext = false;
        }

        String lastLink() {
            return links.get(links.size() - 1);
        }
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    RecordingEmailService email;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), EmailVerifyToken.class);
        email.clear();
    }

    // ── AC 1, 2, 3, 11, 12: happy path ──

    @Test
    void register_createsPendingStudent_hashesPassword_andSendsEmail() throws Exception {
        String tenantId = createTenant("vignan", TenantStatus.ACTIVE);

        mockMvc.perform(post("/api/student/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("vignan", "STUDENT@Vignan.edu", "s3cret-pw"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("student@vignan.edu")) // normalized (lowercased)
                .andExpect(jsonPath("$.data.accountStatus").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.emailVerifyToken").doesNotExist());

        User saved = userRepository.findByTenantIdAndEmail(tenantId, "student@vignan.edu").orElseThrow();
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(saved.getPasswordHash()).isNotEqualTo("s3cret-pw");
        assertThat(passwordEncoder.matches("s3cret-pw", saved.getPasswordHash())).isTrue();

        // email sent to the normalized address, with a link carrying a token (never in the API body)
        assertThat(email.recipients).containsExactly("student@vignan.edu");
        assertThat(email.lastLink()).contains("token=");
    }

    // ── AC 10: public access (no Authorization header) ──

    @Test
    void register_isPublic_noTokenRequired() throws Exception {
        createTenant("openuni", TenantStatus.ACTIVE);

        mockMvc.perform(post("/api/student/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("openuni", "a@openuni.edu", "s3cret-pw"))))
                .andExpect(status().isCreated()); // not 401
    }

    // ── Review patch (High): email-send failure rolls back the user so the address can be retried ──

    @Test
    void emailSendFailure_rollsBackUser_andAddressCanBeRetried() throws Exception {
        createTenant("flaky", TenantStatus.ACTIVE);
        email.failNext = true;

        mockMvc.perform(post("/api/student/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("flaky", "retry@flaky.edu", "s3cret-pw"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("EMAIL_SEND_FAILED"));

        // rolled back: no orphaned user, no leftover token
        assertThat(mongoTemplate.findAll(User.class)).isEmpty();
        assertThat(mongoTemplate.findAll(EmailVerifyToken.class)).isEmpty();

        // and the same address is free to register again once email works
        email.failNext = false;
        register("flaky", "retry@flaky.edu", "s3cret-pw").andExpect(status().isCreated());
    }

    // ── AC 4: duplicate within tenant → 409; same email across tenants → allowed ──

    @Test
    void duplicateEmailSameTenant_isRejectedWith409() throws Exception {
        createTenant("dupuni", TenantStatus.ACTIVE);

        register("dupuni", "dup@dupuni.edu", "s3cret-pw").andExpect(status().isCreated());

        mockMvc.perform(post("/api/student/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("dupuni", "DUP@dupuni.edu", "s3cret-pw")))) // case-variant
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void sameEmailDifferentCollege_isAllowed() throws Exception {
        createTenant("alpha", TenantStatus.ACTIVE);
        createTenant("beta", TenantStatus.ACTIVE);

        register("alpha", "shared@x.edu", "s3cret-pw").andExpect(status().isCreated());
        register("beta", "shared@x.edu", "s3cret-pw").andExpect(status().isCreated()); // per-tenant only
    }

    // ── AC 5: unknown college → 404 (no user, no email) ──

    @Test
    void unknownCollege_isRejectedWith404() throws Exception {
        register("no-such-college", "a@ghost.edu", "s3cret-pw")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(mongoTemplate.findAll(User.class)).isEmpty();
        assertThat(email.recipients).isEmpty();
    }

    // ── AC 6: suspended college → 403 (no user, no email) ──

    @Test
    void suspendedCollege_isRejectedWith403() throws Exception {
        createTenant("frozen", TenantStatus.SUSPENDED);

        register("frozen", "a@frozen.edu", "s3cret-pw")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(mongoTemplate.findAll(User.class)).isEmpty();
        assertThat(email.recipients).isEmpty();
    }

    // ── AC 7: validation → 400 ──

    @Test
    void invalidBody_isRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/student/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("uni", "not-an-email", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields").exists());

        mockMvc.perform(post("/api/student/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("", "a@b.edu", "s3cret-pw")))) // blank collegeCode
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── AC 8: verification activates the account ──

    @Test
    void verifyEmail_activatesAccount() throws Exception {
        String tenantId = createTenant("verifyuni", TenantStatus.ACTIVE);
        register("verifyuni", "v@verifyuni.edu", "s3cret-pw").andExpect(status().isCreated());
        String token = tokenFromLastEmail();

        mockMvc.perform(get("/api/student/auth/verify-email").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));

        User user = userRepository.findByTenantIdAndEmail(tenantId, "v@verifyuni.edu").orElseThrow();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    // ── AC 8/9: token is single-use ──

    @Test
    void verifyEmail_tokenIsSingleUse() throws Exception {
        createTenant("singleuse", TenantStatus.ACTIVE);
        register("singleuse", "s@singleuse.edu", "s3cret-pw").andExpect(status().isCreated());
        String token = tokenFromLastEmail();

        mockMvc.perform(get("/api/student/auth/verify-email").param("token", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/auth/verify-email").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_VERIFY_TOKEN_INVALID"));
    }

    // ── AC 9: bad token → 400 ──

    @Test
    void verifyEmail_unknownToken_isRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/student/auth/verify-email").param("token", "garbage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_VERIFY_TOKEN_INVALID"));
    }

    // ── helpers ──

    private org.springframework.test.web.servlet.ResultActions register(
            String collegeCode, String emailAddr, String password) throws Exception {
        return mockMvc.perform(post("/api/student/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(collegeCode, emailAddr, password))));
    }

    private String tokenFromLastEmail() {
        String link = email.lastLink();
        String raw = link.substring(link.indexOf("token=") + "token=".length());
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private String createTenant(String slug, TenantStatus statusValue) {
        Tenant t = new Tenant();
        t.setName(slug + " College");
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(new ArrayList<>(List.of("CSE")));
        t.setBatches(new ArrayList<>(List.of("2026")));
        t.setSeason(new Season(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)));
        t.setStatus(statusValue);
        return tenantRepository.save(t).getId();
    }

    private RegisterStudentRequest request(String collegeCode, String emailAddr, String password) {
        return new RegisterStudentRequest(collegeCode, emailAddr, password);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
