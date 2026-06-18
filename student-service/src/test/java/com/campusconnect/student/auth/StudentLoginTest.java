package com.campusconnect.student.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.RefreshToken;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end student login/refresh/logout (Story 2.3): tokens + HttpOnly cookie, status gate, rotation. */
@SpringBootTest
@Testcontainers
class StudentLoginTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
        // Story 2.5: this class makes many login calls from one IP across methods in a shared context;
        // raise the limits far above any test so the shared RateLimiter never trips here. Throttling itself
        // is proven by RateLimiterTest and the dedicated throttle tests.
        registry.add("app.ratelimit.login.limit", () -> "100000");
        registry.add("app.ratelimit.otp.limit", () -> "100000");
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
    JwtService jwtService;
    @Autowired
    MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), RefreshToken.class);
    }

    @Test
    void login_activeStudent_returnsTokenAndHttpOnlyCookie() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);

        MvcResult result = mockMvc.perform(post("/api/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "S@V.edu", "s3cret-pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(1800))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andReturn();

        // the access token is a valid JWT for this user/tenant
        String accessToken = json(result).at("/data/accessToken").asText();
        JwtService.AuthToken parsed = jwtService.parse(accessToken);
        assertThat(parsed.role()).isEqualTo(Role.STUDENT);
        assertThat(parsed.tenantId()).isEqualTo(tenantId);
        // refresh token persisted; never in the body
        String cookieVal = result.getResponse().getCookie("refreshToken").getValue();
        assertThat(mongoTemplate.findAll(RefreshToken.class)).hasSize(1);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(cookieVal);
    }

    @Test
    void login_isPublic_andRejectsBadCredentialsUniformly() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);

        // no Authorization header needed (public), wrong password → 401 INVALID_CREDENTIALS (not 401 auth-required)
        mockMvc.perform(post("/api/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "s@v.edu", "WRONG")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_wrongPortalRole_isInvalidCredentials() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "r@v.edu", "s3cret-pw", Role.RECRUITER, AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "r@v.edu", "s3cret-pw")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_pendingVerification_isEmailNotVerified403() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.PENDING_VERIFICATION);

        mockMvc.perform(post("/api/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "s@v.edu", "s3cret-pw")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void login_deactivated_isAccountInactive403() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.DEACTIVATED);

        mockMvc.perform(post("/api/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "s@v.edu", "s3cret-pw")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void login_invalidBody_is400() throws Exception {
        mockMvc.perform(post("/api/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("", "not-an-email", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void refresh_rotatesCookie_andOldCookieReplayFails() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        Cookie first = login("vignan", "s@v.edu", "s3cret-pw");

        MvcResult refreshed = mockMvc.perform(post("/api/student/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(cookie().exists("refreshToken"))
                .andReturn();
        Cookie rotated = refreshed.getResponse().getCookie("refreshToken");
        assertThat(rotated.getValue()).isNotEqualTo(first.getValue());

        // replaying the original (now-rotated) cookie fails
        mockMvc.perform(post("/api/student/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void logout_clearsCookie_andInvalidatesSession() throws Exception {
        String tenantId = seedTenant("vignan");
        seedUser(tenantId, "s@v.edu", "s3cret-pw", Role.STUDENT, AccountStatus.ACTIVE);
        Cookie session = login("vignan", "s@v.edu", "s3cret-pw");

        mockMvc.perform(post("/api/student/auth/logout").cookie(session))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0)); // cleared

        mockMvc.perform(post("/api/student/auth/refresh").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void logout_isIdempotent_withNoCookie() throws Exception {
        mockMvc.perform(post("/api/student/auth/logout"))
                .andExpect(status().isOk());
    }

    // ── helpers ──

    private Cookie login(String college, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(college, email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("refreshToken");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String loginJson(String college, String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("collegeCode", college);
            put("email", email);
            put("password", password);
        }});
    }

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private void seedUser(String tenantId, String email, String password, Role role, AccountStatus status) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(role);
        u.setAccountStatus(status);
        userRepository.save(u);
    }
}
