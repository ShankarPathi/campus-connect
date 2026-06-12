package com.campusconnect.admin.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.RefreshToken;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * College-admin login (Story 2.3) — admin token TTL, and the end-to-end proof that a logged-in admin
 * can call the Story 2.2 approval endpoints with the real access token (not a test-minted one).
 */
@SpringBootTest
@Testcontainers
class AdminLoginTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
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
    void login_collegeAdmin_hasAdminTokenLifetime() throws Exception {
        String tenantId = seedTenant("vignan");
        seedAdmin(tenantId, "tpo@vignan.edu");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "tpo@vignan.edu", "s3cret-pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("COLLEGE_ADMIN"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(15 * 60)) // admin TTL
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    void loggedInAdmin_canCallApprovalEndpoint_endToEnd() throws Exception {
        String tenantId = seedTenant("vignan");
        seedAdmin(tenantId, "tpo@vignan.edu");

        MvcResult login = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "tpo@vignan.edu", "s3cret-pw")))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = json(login).at("/data/accessToken").asText();

        // the real login token authenticates the Story 2.2 admin-only endpoint
        mockMvc.perform(get("/api/admin/recruiters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void login_badCredentials_is401() throws Exception {
        String tenantId = seedTenant("vignan");
        seedAdmin(tenantId, "tpo@vignan.edu");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("vignan", "tpo@vignan.edu", "WRONG")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
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

    private void seedAdmin(String tenantId, String email) {
        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode("s3cret-pw"));
        u.setRole(Role.COLLEGE_ADMIN);
        u.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(u);
    }
}
