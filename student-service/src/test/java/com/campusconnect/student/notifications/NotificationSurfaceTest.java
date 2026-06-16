package com.campusconnect.student.notifications;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Notification;
import com.campusconnect.common.domain.NotificationType;
import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.security.JwtService;
import com.campusconnect.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The student notification panel — list newest-first, unread count, mark one/all, owner + tenant isolation (Story 8.3, FR-28). */
@SpringBootTest
@Testcontainers
class NotificationSurfaceTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired WebApplicationContext context;
    @Autowired TenantRepository tenantRepository;
    @Autowired JwtService jwtService;
    @Autowired MongoTemplate mongoTemplate;

    MockMvc mockMvc;
    String tenantId;
    String studentId;
    private int seq;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Tenant.class);
        mongoTemplate.remove(new Query(), Notification.class);
        tenantId = seedTenant("vignan");
        studentId = seedActiveStudent(tenantId, "s@v.edu");
    }

    @Test
    void list_returnsMyNotifications_newestFirst() throws Exception {
        seedNotification(tenantId, studentId, "Oldest", false, Instant.now().minusSeconds(300));
        seedNotification(tenantId, studentId, "Newest", false, Instant.now());
        seedNotification(tenantId, studentId, "Middle", true, Instant.now().minusSeconds(150));

        mockMvc.perform(get("/api/student/notifications").header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].title").value("Newest"))
                .andExpect(jsonPath("$.data.items[2].title").value("Oldest"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    void list_unreadOnly_filtersToUnread() throws Exception {
        seedNotification(tenantId, studentId, "Unread", false, Instant.now());
        seedNotification(tenantId, studentId, "Read", true, Instant.now().minusSeconds(60));

        mockMvc.perform(get("/api/student/notifications").param("unreadOnly", "true")
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Unread"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void unreadCount_endpoint_isCorrect() throws Exception {
        seedNotification(tenantId, studentId, "A", false, Instant.now());
        seedNotification(tenantId, studentId, "B", false, Instant.now());
        seedNotification(tenantId, studentId, "C", true, Instant.now());

        mockMvc.perform(get("/api/student/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    void pagination_capsSize_andReportsTotal() throws Exception {
        for (int i = 0; i < 5; i++) {
            seedNotification(tenantId, studentId, "n" + i, false, Instant.now().minusSeconds(i));
        }

        mockMvc.perform(get("/api/student/notifications").param("page", "0").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.size").value(2));

        // size above the cap is clamped to 100 (still returns all 5 here)
        mockMvc.perform(get("/api/student/notifications").param("size", "9999")
                        .header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").value(5));
    }

    @Test
    void markRead_flipsOne_isIdempotent_andDropsUnreadCount() throws Exception {
        String id = seedNotification(tenantId, studentId, "Unread", false, Instant.now());

        mockMvc.perform(post("/api/student/notifications/{id}/read", id).header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
        assertThat(mongoTemplate.findById(id, Notification.class).isRead()).isTrue();

        // second mark is a no-op (still 200, still read)
        mockMvc.perform(post("/api/student/notifications/{id}/read", id).header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void markRead_anotherUsersNotification_is404_andUnchanged() throws Exception {
        String otherStudent = seedActiveStudent(tenantId, "other@v.edu");
        String otherId = seedNotification(tenantId, otherStudent, "Theirs", false, Instant.now());

        mockMvc.perform(post("/api/student/notifications/{id}/read", otherId).header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(mongoTemplate.findById(otherId, Notification.class).isRead()).isFalse(); // untouched
    }

    @Test
    void markAllRead_flipsEveryUnread_andIsIdempotent() throws Exception {
        seedNotification(tenantId, studentId, "A", false, Instant.now());
        seedNotification(tenantId, studentId, "B", false, Instant.now());
        seedNotification(tenantId, studentId, "C", true, Instant.now());

        mockMvc.perform(post("/api/student/notifications/read-all").header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        assertThat(mongoTemplate.find(new Query(Criteria.where("userId").is(studentId)), Notification.class))
                .allMatch(Notification::isRead);

        // idempotent re-run
        mockMvc.perform(post("/api/student/notifications/read-all").header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void list_doesNotLeakAnotherUsersOrAnotherTenantsNotifications() throws Exception {
        String otherStudent = seedActiveStudent(tenantId, "other@v.edu");
        seedNotification(tenantId, otherStudent, "Other user", false, Instant.now());      // same tenant, other user
        String otherTenant = seedTenant("other");
        String foreignStudent = seedActiveStudent(otherTenant, "x@o.edu");
        seedNotification(otherTenant, foreignStudent, "Other tenant", false, Instant.now()); // other tenant
        seedNotification(tenantId, studentId, "Mine", false, Instant.now());

        mockMvc.perform(get("/api/student/notifications").header(HttpHeaders.AUTHORIZATION, token(studentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Mine"))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    // ── helpers ──

    private String token(String userId, String tid) {
        return "Bearer " + jwtService.issueAccessToken(userId, Role.STUDENT, tid);
    }

    private String seedNotification(String tid, String userId, String title, boolean read, Instant createdAt) {
        Notification n = new Notification();
        n.setTenantId(tid);
        n.setUserId(userId);
        n.setType(NotificationType.PROFILE_APPROVED);
        n.setTitle(title);
        n.setMessage(title + " body");
        n.setRead(read);
        n.setEventId("evt-" + (++seq)); // distinct so the unique {tenant,eventId,user} index never collides
        n.setCreatedAt(createdAt);
        return mongoTemplate.save(n).getId();
    }

    private String seedTenant(String slug) {
        Tenant t = new Tenant();
        t.setName(slug);
        t.setSlug(slug);
        t.setSubdomain(slug);
        t.setBranches(List.of("CSE", "ECE"));
        t.setBatches(List.of("2026", "2027"));
        t.setSeason(new Season(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)));
        t.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(t).getId();
    }

    private String seedActiveStudent(String tid, String emailAddr) {
        User u = new User();
        u.setTenantId(tid);
        u.setEmail(emailAddr.toLowerCase());
        u.setPasswordHash("hash");
        u.setRole(Role.STUDENT);
        u.setAccountStatus(AccountStatus.ACTIVE);
        return mongoTemplate.save(u).getId();
    }
}
