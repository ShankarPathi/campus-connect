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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auto-verify path (demo/no-mail deploys): with {@code app.registration.auto-verify=true}, registration
 * must skip the verification email entirely and activate the account immediately (ACTIVE), so a fresh
 * user can log in without any SMTP server. Guards the AWS live deployment against EMAIL_SEND_FAILED.
 */
@SpringBootTest(properties = "app.registration.auto-verify=true")
@Testcontainers
class StudentAutoVerifyTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    /** Recording {@link EmailService} — @Primary, and fails if ever called (auto-verify must not send). */
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

        @Override
        public void sendVerificationEmail(String toEmail, String verificationLink) {
            recipients.add(toEmail);
        }

        @Override
        public void sendEmail(String to, String subject, String body) {
            // unused
        }
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
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
        email.recipients.clear();
    }

    @Test
    void autoVerify_activatesImmediately_andSendsNoEmailOrToken() throws Exception {
        String tenantId = createTenant("demo-tech", TenantStatus.ACTIVE);

        mockMvc.perform(post("/api/student/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterStudentRequest("demo-tech", "jhon@gmail.com", "Jhon@246"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));

        User saved = userRepository.findByTenantIdAndEmail(tenantId, "jhon@gmail.com").orElseThrow();
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        // no verification email sent, no token persisted — the whole email path is skipped
        assertThat(email.recipients).isEmpty();
        assertThat(mongoTemplate.findAll(EmailVerifyToken.class)).isEmpty();
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
}
