package com.campusconnect.recruiter.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.EmailVerifyToken;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.repository.RecruiterProfileRepository;
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
 * End-to-end test of recruiter registration + email verification (Story 2.2, FR-4): company details
 * captured, tenant resolution, public access, the verify → PENDING_APPROVAL transition (NOT active),
 * and the send-failure rollback.
 */
@SpringBootTest
@Testcontainers
class RecruiterRegistrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

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
            // not used by registration
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
    RecruiterProfileRepository recruiterProfileRepository;
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
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
        mongoTemplate.remove(new Query(), EmailVerifyToken.class);
        email.clear();
    }

    @Test
    void register_createsPendingRecruiter_withCompanyProfile_andSendsEmail() throws Exception {
        String tenantId = createTenant("vignan", TenantStatus.ACTIVE);

        mockMvc.perform(post("/api/recruiter/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("vignan", "HR@Acme.com", "s3cret-pw", "Acme Corp"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("hr@acme.com")) // normalized
                .andExpect(jsonPath("$.data.accountStatus").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        User saved = userRepository.findByTenantIdAndEmail(tenantId, "hr@acme.com").orElseThrow();
        assertThat(saved.getRole().name()).isEqualTo("RECRUITER");
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(passwordEncoder.matches("s3cret-pw", saved.getPasswordHash())).isTrue();

        RecruiterProfile profile = recruiterProfileRepository.findByUserIdAndTenantId(saved.getId(), tenantId).orElseThrow();
        assertThat(profile.getCompanyName()).isEqualTo("Acme Corp");

        assertThat(email.recipients).containsExactly("hr@acme.com");
        assertThat(email.lastLink()).contains("token=");
    }

    @Test
    void register_isPublic_noTokenRequired() throws Exception {
        createTenant("openuni", TenantStatus.ACTIVE);

        mockMvc.perform(post("/api/recruiter/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("openuni", "a@openuni.edu", "s3cret-pw", "Co"))))
                .andExpect(status().isCreated()); // not 401
    }

    @Test
    void blankCompanyName_isRejectedWith400() throws Exception {
        createTenant("uni", TenantStatus.ACTIVE);

        mockMvc.perform(post("/api/recruiter/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("uni", "a@uni.edu", "s3cret-pw", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields").exists());
    }

    @Test
    void duplicateEmailSameTenant_isRejectedWith409_noProfileLeftBehind() throws Exception {
        createTenant("dupuni", TenantStatus.ACTIVE);

        register("dupuni", "dup@dupuni.edu", "Acme").andExpect(status().isCreated());

        mockMvc.perform(post("/api/recruiter/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("dupuni", "DUP@dupuni.edu", "s3cret-pw", "Other"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));

        // exactly one user + one profile (the 409 path created nothing)
        assertThat(mongoTemplate.findAll(User.class)).hasSize(1);
        assertThat(mongoTemplate.findAll(RecruiterProfile.class)).hasSize(1);
    }

    @Test
    void sameEmailDifferentCollege_isAllowed() throws Exception {
        createTenant("alpha", TenantStatus.ACTIVE);
        createTenant("beta", TenantStatus.ACTIVE);

        register("alpha", "shared@x.edu", "A").andExpect(status().isCreated());
        register("beta", "shared@x.edu", "B").andExpect(status().isCreated());
    }

    @Test
    void unknownCollege_isRejectedWith404_noSideEffects() throws Exception {
        register("no-such", "a@ghost.edu", "Co")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(mongoTemplate.findAll(User.class)).isEmpty();
        assertThat(mongoTemplate.findAll(RecruiterProfile.class)).isEmpty();
        assertThat(email.recipients).isEmpty();
    }

    @Test
    void suspendedCollege_isRejectedWith403_noSideEffects() throws Exception {
        createTenant("frozen", TenantStatus.SUSPENDED);

        register("frozen", "a@frozen.edu", "Co")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(mongoTemplate.findAll(User.class)).isEmpty();
        assertThat(mongoTemplate.findAll(RecruiterProfile.class)).isEmpty();
    }

    @Test
    void verifyEmail_movesToPendingApproval_notActive() throws Exception {
        String tenantId = createTenant("verifyuni", TenantStatus.ACTIVE);
        register("verifyuni", "v@verifyuni.edu", "Co").andExpect(status().isCreated());
        String token = tokenFromLastEmail();

        mockMvc.perform(get("/api/recruiter/auth/verify-email").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        User user = userRepository.findByTenantIdAndEmail(tenantId, "v@verifyuni.edu").orElseThrow();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL); // NOT ACTIVE
    }

    @Test
    void verifyEmail_tokenIsSingleUse() throws Exception {
        createTenant("singleuse", TenantStatus.ACTIVE);
        register("singleuse", "s@singleuse.edu", "Co").andExpect(status().isCreated());
        String token = tokenFromLastEmail();

        mockMvc.perform(get("/api/recruiter/auth/verify-email").param("token", token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/recruiter/auth/verify-email").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_VERIFY_TOKEN_INVALID"));
    }

    @Test
    void verifyEmail_unknownToken_isRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/recruiter/auth/verify-email").param("token", "garbage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_VERIFY_TOKEN_INVALID"));
    }

    @Test
    void emailSendFailure_rollsBackUserAndProfile_andAddressCanBeRetried() throws Exception {
        createTenant("flaky", TenantStatus.ACTIVE);
        email.failNext = true;

        mockMvc.perform(post("/api/recruiter/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("flaky", "retry@flaky.edu", "s3cret-pw", "Co"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("EMAIL_SEND_FAILED"));

        assertThat(mongoTemplate.findAll(User.class)).isEmpty();
        assertThat(mongoTemplate.findAll(RecruiterProfile.class)).isEmpty();
        assertThat(mongoTemplate.findAll(EmailVerifyToken.class)).isEmpty();

        email.failNext = false;
        register("flaky", "retry@flaky.edu", "Co").andExpect(status().isCreated());
    }

    // ── helpers ──

    private org.springframework.test.web.servlet.ResultActions register(
            String collegeCode, String emailAddr, String company) throws Exception {
        return mockMvc.perform(post("/api/recruiter/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(collegeCode, emailAddr, "s3cret-pw", company))));
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

    private RegisterRecruiterRequest request(String collegeCode, String emailAddr, String password, String company) {
        return new RegisterRecruiterRequest(collegeCode, emailAddr, password, company,
                null, null, null, null, null);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
